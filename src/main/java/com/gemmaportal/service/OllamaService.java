package com.gemmaportal.service;

import com.gemmaportal.config.OllamaProperties;
import com.gemmaportal.dto.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private static final String SESSION_KEY = "conversationHistory";
    private static final tools.jackson.databind.ObjectMapper MAPPER =
            new tools.jackson.databind.ObjectMapper();

    private final RestClient ollamaRestClient;
    private final OllamaProperties ollamaProperties;

    private final HttpClient streamingClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ChatResponse chat(String userMessage, HttpSession session) {
        List<ChatMessage> history = getHistory(session);
        history.add(new ChatMessage("user", userMessage));
        try {
            OllamaChatRequest request = OllamaChatRequest.builder()
                    .model(ollamaProperties.getModel())
                    .messages(new ArrayList<>(history))
                    .stream(false)
                    .build();

            log.info("Sending chat request, model={}", ollamaProperties.getModel());

            OllamaChatResponse response = ollamaRestClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response == null || response.getMessage() == null) {
                throw new RuntimeException("Empty response from Ollama");
            }

            history.add(response.getMessage());
            return new ChatResponse(response.getMessage().getContent(), response.getModel());

        } catch (Exception e) {
            history.remove(history.size() - 1);
            log.error("Ollama chat error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to communicate with Ollama: " + e.getMessage(), e);
        }
    }

    public void streamChat(String userMessage, List<ChatMessage> history, SseEmitter emitter) {
        history.add(new ChatMessage("user", userMessage));

        try {
            OllamaChatRequest ollamaRequest = OllamaChatRequest.builder()
                    .model(ollamaProperties.getModel())
                    .messages(new ArrayList<>(history))
                    .stream(true)
                    .build();

            String requestBody = MAPPER.writeValueAsString(ollamaRequest);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaProperties.getBaseUrl() + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.info("Streaming chat request, model={}", ollamaProperties.getModel());

            HttpResponse<java.io.InputStream> response =
                    streamingClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            StringBuilder fullContent = new StringBuilder();
            boolean metaSent = false;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    OllamaChatResponse chunk = MAPPER.readValue(line, OllamaChatResponse.class);

                    // Send model name once before the first token
                    if (!metaSent) {
                        emitter.send(MAPPER.writeValueAsString(
                                java.util.Map.of("model", chunk.getModel())));
                        metaSent = true;
                    }

                    if (chunk.getMessage() != null) {
                        String token = chunk.getMessage().getContent();
                        if (token != null && !token.isEmpty()) {
                            fullContent.append(token);
                            emitter.send(MAPPER.writeValueAsString(token));
                        }
                    }
                    if (chunk.isDone()) {
                        history.add(new ChatMessage("assistant", fullContent.toString()));
                        emitter.send(MAPPER.writeValueAsString("[DONE]"));
                        emitter.complete();
                        return;
                    }
                }
            }

            // Stream ended without a done=true chunk
            history.add(new ChatMessage("assistant", fullContent.toString()));
            emitter.send(MAPPER.writeValueAsString("[DONE]"));
            emitter.complete();

        } catch (Exception e) {
            history.remove(history.size() - 1);
            log.error("Ollama streaming error: {}", e.getMessage(), e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {}
        }
    }

    public void clearHistory(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        log.info("History cleared for session {}", session.getId());
    }

    public List<ChatMessage> getConversationHistory(HttpSession session) {
        return new ArrayList<>(getHistory(session));
    }

    @SuppressWarnings("unchecked")
    public List<ChatMessage> getHistory(HttpSession session) {
        List<ChatMessage> history = (List<ChatMessage>) session.getAttribute(SESSION_KEY);
        if (history == null) {
            history = Collections.synchronizedList(new ArrayList<>());
            session.setAttribute(SESSION_KEY, history);
        }
        return history;
    }
}