package com.gemmaportal.service;

import com.gemmaportal.config.OllamaProperties;
import com.gemmaportal.dto.ChatMessage;
import com.gemmaportal.dto.ChatResponse;
import com.gemmaportal.dto.OllamaChatResponse;
import com.gemmaportal.memory.ChatMemoryService;
import com.gemmaportal.memory.Conversation;
import com.gemmaportal.memory.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OllamaServiceTest {

    private static final String SESSION_ID = "session-1";
    private static final String MODEL = "gemma3";

    private ChatMemoryService chatMemoryService;
    private RestClient ollamaRestClient;
    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private OllamaProperties ollamaProperties;
    private OllamaService service;

    @BeforeEach
    void setUp() {
        chatMemoryService = mock(ChatMemoryService.class);
        ollamaRestClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        when(ollamaRestClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/api/chat")).thenReturn(bodySpec);
        doReturn(bodySpec).when(bodySpec).body(any(Object.class));
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        ollamaProperties = new OllamaProperties();
        ollamaProperties.setModel(MODEL);
        service = new OllamaService(ollamaRestClient, ollamaProperties, chatMemoryService);
    }

    @Nested
    class WhenDelegating {

        @Test
        void getConversationHistoryDelegatesToChatMemoryService() {
            List<ChatMessage> expected = List.of(new ChatMessage("user", "hello"));
            when(chatMemoryService.getHistory(SESSION_ID)).thenReturn(expected);

            List<ChatMessage> result = service.getConversationHistory(SESSION_ID);

            assertThat(result).isSameAs(expected);
        }

        @Test
        void clearHistoryDelegatesToChatMemoryService() {
            service.clearHistory(SESSION_ID);

            verify(chatMemoryService).clearHistory(SESSION_ID);
        }
    }

    @Nested
    class WhenChatting {

        @BeforeEach
        void prepareConversation() {
            Conversation conversation = Conversation.create(SESSION_ID, MODEL);
            when(chatMemoryService.getOrCreateConversation(SESSION_ID, MODEL)).thenReturn(conversation);
            when(chatMemoryService.getHistory(SESSION_ID))
                    .thenReturn(List.of(new ChatMessage("user", "hello")));
        }

        @Test
        void persistsUserMessageCallsOllamaPersistsAssistantAndReturnsResponse() {
            OllamaChatResponse ollamaResponse = new OllamaChatResponse();
            ollamaResponse.setModel(MODEL);
            ollamaResponse.setMessage(new ChatMessage("assistant", "hi there"));
            mockOllamaReturns(ollamaResponse);

            ChatResponse result = service.chat("hello", SESSION_ID);

            assertThat(result.getResponse()).isEqualTo("hi there");
            assertThat(result.getModel()).isEqualTo(MODEL);
            verify(chatMemoryService).appendMessage(isNull(), eq(Role.USER), eq("hello"));
            verify(chatMemoryService).appendMessage(isNull(), eq(Role.ASSISTANT), eq("hi there"));
        }

        @Test
        void wrapsNullResponseFromOllamaInRuntimeException() {
            mockOllamaReturns(null);

            assertThatThrownBy(() -> service.chat("hello", SESSION_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to communicate with Ollama");
        }

        @Test
        void wrapsNullMessageInResponseInRuntimeException() {
            OllamaChatResponse ollamaResponse = new OllamaChatResponse();
            ollamaResponse.setModel(MODEL);
            ollamaResponse.setMessage(null);
            mockOllamaReturns(ollamaResponse);

            assertThatThrownBy(() -> service.chat("hello", SESSION_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to communicate with Ollama");
        }

        @Test
        void wrapsRestClientFailureInRuntimeException() {
            when(responseSpec.body(OllamaChatResponse.class))
                    .thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> service.chat("hello", SESSION_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to communicate with Ollama")
                    .hasMessageContaining("connection refused");
        }

        @Test
        void doesNotPersistAssistantMessageWhenOllamaFails() {
            when(responseSpec.body(OllamaChatResponse.class))
                    .thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> service.chat("hello", SESSION_ID));

            verify(chatMemoryService).appendMessage(any(), eq(Role.USER), any());
            verify(chatMemoryService, never()).appendMessage(any(), eq(Role.ASSISTANT), any());
        }

        private void mockOllamaReturns(OllamaChatResponse response) {
            when(responseSpec.body(OllamaChatResponse.class)).thenReturn(response);
        }
    }

    @Nested
    class WhenProcessingStream {

        private Conversation conversation;
        private SseEmitter emitter;

        @BeforeEach
        void setUp() {
            conversation = Conversation.create(SESSION_ID, MODEL);
            emitter = mock(SseEmitter.class);
        }

        @Test
        void persistsFullContentAndCompletesEmitterOnDone() throws IOException {
            InputStream body = streamOf(
                    "{\"model\":\"gemma3\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"done\":false}",
                    "{\"model\":\"gemma3\",\"message\":{\"role\":\"assistant\",\"content\":\" world\"},\"done\":true}");

            service.processStreamChunks(body, conversation, emitter);

            verify(chatMemoryService).appendMessage(isNull(), eq(Role.ASSISTANT), eq("Hello world"));
            verify(emitter).complete();
        }

        @Test
        void persistsPartialContentWhenOllamaSendsErrorChunk() {
            InputStream body = streamOf(
                    "{\"model\":\"gemma3\",\"message\":{\"role\":\"assistant\",\"content\":\"Half\"},\"done\":false}",
                    "{\"error\":\"out of memory\"}");

            assertThatThrownBy(() -> service.processStreamChunks(body, conversation, emitter))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("out of memory");

            verify(chatMemoryService).appendMessage(isNull(), eq(Role.ASSISTANT), eq("Half"));
            verify(emitter, never()).complete();
        }

        @Test
        void persistsPartialContentWhenStreamFailsMidway() {
            InputStream body = new SequenceInputStream(
                    new ByteArrayInputStream(
                            ("{\"model\":\"gemma3\",\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"},\"done\":false}\n")
                                    .getBytes(StandardCharsets.UTF_8)),
                    new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw new IOException("network drop");
                        }
                    });

            assertThatThrownBy(() -> service.processStreamChunks(body, conversation, emitter))
                    .isInstanceOf(IOException.class)
                    .hasMessage("network drop");

            verify(chatMemoryService).appendMessage(isNull(), eq(Role.ASSISTANT), eq("Hi"));
        }

        @Test
        void doesNotPersistWhenStreamFailsBeforeAnyTokens() {
            InputStream body = streamOf("{\"error\":\"model not found\"}");

            assertThatThrownBy(() -> service.processStreamChunks(body, conversation, emitter))
                    .isInstanceOf(RuntimeException.class);

            verify(chatMemoryService, never()).appendMessage(any(), any(), any());
        }

        @Test
        void persistsContentWhenStreamEndsWithoutDoneMarker() throws IOException {
            InputStream body = streamOf(
                    "{\"model\":\"gemma3\",\"message\":{\"role\":\"assistant\",\"content\":\"Tail\"},\"done\":false}");

            service.processStreamChunks(body, conversation, emitter);

            verify(chatMemoryService).appendMessage(isNull(), eq(Role.ASSISTANT), eq("Tail"));
            verify(emitter).complete();
        }

        @Test
        void emitsTokensAndDoneMarkerToClient() throws IOException {
            InputStream body = streamOf(
                    "{\"model\":\"gemma3\",\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"},\"done\":true}");

            service.processStreamChunks(body, conversation, emitter);

            verify(emitter).send("{\"model\":\"gemma3\"}");
            verify(emitter).send("\"Hi\"");
            verify(emitter).send("\"[DONE]\"");
        }

        private InputStream streamOf(String... ndjsonLines) {
            return new ByteArrayInputStream(String.join("\n", ndjsonLines).getBytes(StandardCharsets.UTF_8));
        }
    }
}
