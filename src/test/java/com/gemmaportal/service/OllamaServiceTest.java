package com.gemmaportal.service;

import com.gemmaportal.config.OllamaProperties;
import com.gemmaportal.dto.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OllamaServiceTest {

    private OllamaService service;

    @BeforeEach
    void setUp() {
        service = new OllamaService(
                mock(RestClient.class),
                mock(OllamaProperties.class));
    }

    @Test
    void historyIsInitiallyEmpty() {
        assertThat(service.getConversationHistory(new MockHttpSession())).isEmpty();
    }

    @Test
    void getConversationHistoryReturnsCopy() {
        MockHttpSession session = new MockHttpSession();
        service.getHistory(session).add(new ChatMessage("user", "hello"));

        List<ChatMessage> copy = service.getConversationHistory(session);
        copy.clear();

        assertThat(service.getConversationHistory(session)).hasSize(1);
    }

    @Test
    void clearHistoryEmptiesSession() {
        MockHttpSession session = new MockHttpSession();
        service.getHistory(session).add(new ChatMessage("user", "hello"));

        service.clearHistory(session);

        assertThat(service.getConversationHistory(session)).isEmpty();
    }

    @Test
    void differentSessionsHaveIndependentHistories() {
        MockHttpSession session1 = new MockHttpSession();
        MockHttpSession session2 = new MockHttpSession();

        service.getHistory(session1).add(new ChatMessage("user", "session 1 message"));

        assertThat(service.getConversationHistory(session1)).hasSize(1);
        assertThat(service.getConversationHistory(session2)).isEmpty();
    }

    @Test
    void historyPreservesMessageOrder() {
        MockHttpSession session = new MockHttpSession();
        service.getHistory(session).add(new ChatMessage("user", "first"));
        service.getHistory(session).add(new ChatMessage("assistant", "second"));
        service.getHistory(session).add(new ChatMessage("user", "third"));

        List<ChatMessage> history = service.getConversationHistory(session);
        assertThat(history).extracting(ChatMessage::getContent)
                .containsExactly("first", "second", "third");
    }
}