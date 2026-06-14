package com.gemmaportal.service;

import com.gemmaportal.config.OllamaProperties;
import com.gemmaportal.dto.*;
import com.gemmaportal.memory.ChatMemoryService;
import com.gemmaportal.memory.Conversation;
import com.gemmaportal.memory.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private static final tools.jackson.databind.ObjectMapper MAPPER =
            new tools.jackson.databind.ObjectMapper();

    private final RestClient ollamaRestClient;
    private final OllamaProperties ollamaProperties;
    private final ChatMemoryService chatMemoryService;

    private final HttpClient streamingClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ── Public API ───────────────────────────────────────────────────────────

    public ChatResponse chat(String userMessage, String sessionId) {
        Conversation conversation = prepareConversation(sessionId, userMessage);
        OllamaChatResponse response = executeChat(buildRequest(conversation, false));
        saveAssistantMessage(conversation, response.getMessage().getContent());
        return new ChatResponse(response.getMessage().getContent(), response.getModel());
    }

    public void streamChat(String userMessage, String sessionId, SseEmitter emitter) {
        try {
            Conversation conversation = prepareConversation(sessionId, userMessage);
            executeStream(conversation, emitter);
        } catch (Exception e) {
            log.error("Ollama streaming error: {}", e.getMessage(), e);
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    public void clearHistory(String sessionId) {
        chatMemoryService.clearHistory(sessionId);
        log.info("History cleared for session {}", sessionId);
    }

    public List<ChatMessage> getConversationHistory(String sessionId) {
        return chatMemoryService.getHistory(sessionId);
    }

    // ── Mid-level: orchestration ─────────────────────────────────────────────

    private Conversation prepareConversation(String sessionId, String userMessage) {
        Conversation conversation = chatMemoryService.getOrCreateConversation(sessionId, ollamaProperties.getModel());
        chatMemoryService.appendMessage(conversation.getId(), Role.USER, userMessage);
        return conversation;
    }

    private OllamaChatRequest buildRequest(Conversation conversation, boolean stream) {
        List<ChatMessage> history = chatMemoryService.getHistory(conversation.getSessionId());
        return OllamaChatRequest.builder()
                .model(ollamaProperties.getModel())
                .messages(new ArrayList<>(history))
                .stream(stream)
                .build();
    }

    private OllamaChatResponse executeChat(OllamaChatRequest request) {
        log.info("Sending chat request, model={}", ollamaProperties.getModel());
        try {
            OllamaChatResponse response = ollamaRestClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);
            if (response == null || response.getMessage() == null) {
                throw new RuntimeException("Empty response from Ollama");
            }
            return response;
        } catch (Exception e) {
            log.error("Ollama chat error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to communicate with Ollama: " + e.getMessage(), e);
        }
    }

    private void executeStream(Conversation conversation, SseEmitter emitter) throws IOException, InterruptedException {
        HttpRequest httpRequest = buildStreamingHttpRequest(buildRequest(conversation, true));
        log.info("Streaming chat request, model={}", ollamaProperties.getModel());
        HttpResponse<InputStream> response = streamingClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        processStreamChunks(response.body(), conversation, emitter);
    }

    // ── Low-level: I/O and SSE details ──────────────────────────────────────

    private HttpRequest buildStreamingHttpRequest(OllamaChatRequest request) {
        return HttpRequest.newBuilder()
                .uri(URI.create(ollamaProperties.getBaseUrl() + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(request)))
                .build();
    }

    private void processStreamChunks(InputStream body, Conversation conversation, SseEmitter emitter) throws IOException {
        StringBuilder fullContent = new StringBuilder();
        boolean metaSent = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                OllamaChatResponse chunk = parseChunk(line);

                if (chunk.getError() != null) {
                    log.error("Ollama error: {}", chunk.getError());
                    throw new RuntimeException(chunk.getError());
                }
                if (!metaSent) {
                    emitMetaEvent(emitter, chunk.getModel() != null ? chunk.getModel() : ollamaProperties.getModel());
                    metaSent = true;
                }
                if (chunk.getMessage() != null) {
                    String token = chunk.getMessage().getContent();
                    if (token != null && !token.isEmpty()) {
                        fullContent.append(token);
                        emitToken(emitter, token);
                    }
                }
                if (chunk.isDone()) break;
            }
        } catch (IOException | RuntimeException e) {
            persistPartial(conversation, fullContent);
            throw e;
        }

        finishStream(emitter, conversation, fullContent.toString());
    }

    private void persistPartial(Conversation conversation, StringBuilder content) {
        if (content.isEmpty()) return;
        try {
            saveAssistantMessage(conversation, content.toString());
            log.warn("Persisted partial assistant response ({} chars) after stream error", content.length());
        } catch (Exception saveEx) {
            log.error("Failed to persist partial assistant response", saveEx);
        }
    }

    private void emitMetaEvent(SseEmitter emitter, String model) throws IOException {
        emitter.send(toJson(Map.of("model", model)));
    }

    private void emitToken(SseEmitter emitter, String token) throws IOException {
        emitter.send(toJson(token));
    }

    private void finishStream(SseEmitter emitter, Conversation conversation, String content) throws IOException {
        saveAssistantMessage(conversation, content);
        emitter.send(toJson("[DONE]"));
        emitter.complete();
    }

    private void saveAssistantMessage(Conversation conversation, String content) {
        chatMemoryService.appendMessage(conversation.getId(), Role.ASSISTANT, content);
    }

    private OllamaChatResponse parseChunk(String line) {
        return MAPPER.readValue(line, OllamaChatResponse.class);
    }

    private String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
