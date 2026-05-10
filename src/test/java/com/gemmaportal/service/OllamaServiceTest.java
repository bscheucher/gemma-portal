package com.gemmaportal.service;

import com.gemmaportal.config.OllamaProperties;
import com.gemmaportal.dto.ChatMessage;
import com.gemmaportal.memory.ChatMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OllamaServiceTest {

    private ChatMemoryService chatMemoryService;
    private OllamaService service;

    @BeforeEach
    void setUp() {
        chatMemoryService = mock(ChatMemoryService.class);
        service = new OllamaService(
                mock(RestClient.class),
                mock(OllamaProperties.class),
                chatMemoryService);
    }

    @Test
    void getConversationHistoryDelegatesToChatMemoryService() {
        List<ChatMessage> expected = List.of(new ChatMessage("user", "hello"));
        when(chatMemoryService.getHistory("session-1")).thenReturn(expected);

        List<ChatMessage> result = service.getConversationHistory("session-1");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void clearHistoryDelegatesToChatMemoryService() {
        service.clearHistory("session-1");

        verify(chatMemoryService).clearHistory("session-1");
    }
}