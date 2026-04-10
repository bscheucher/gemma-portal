package com.gemmaportal.service;

import com.gemmaportal.config.OllamaConfig;
import com.gemmaportal.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private final RestClient ollamaRestClient;
    private final OllamaConfig ollamaConfig;
    private final List<ChatMessage> conversationHistory = new ArrayList<>();

    public ChatResponse chat(String userMessage) {
        try {
            // Add user message to conversation history
            ChatMessage userChatMessage = new ChatMessage("user", userMessage);
            conversationHistory.add(userChatMessage);

            // Build request
            OllamaChatRequest request = OllamaChatRequest.builder()
                    .model(ollamaConfig.getModel())
                    .messages(new ArrayList<>(conversationHistory))
                    .stream(false)
                    .build();

            log.info("Sending request to Ollama with model: {}", ollamaConfig.getModel());

            // Call Ollama API
            OllamaChatResponse response = ollamaRestClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response != null && response.getMessage() != null) {
                // Add assistant response to conversation history
                conversationHistory.add(response.getMessage());

                log.info("Received response from Ollama");
                return new ChatResponse(
                        response.getMessage().getContent(),
                        response.getModel()
                );
            }

            throw new RuntimeException("No response received from Ollama");

        } catch (Exception e) {
            log.error("Error communicating with Ollama: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to communicate with Ollama: " + e.getMessage(), e);
        }
    }

    public void clearHistory() {
        conversationHistory.clear();
        log.info("Conversation history cleared");
    }

    public List<ChatMessage> getConversationHistory() {
        return new ArrayList<>(conversationHistory);
    }
}
