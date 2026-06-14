package com.gemmaportal.controller;

import com.gemmaportal.config.OllamaProperties;
import com.gemmaportal.dto.ChatMessage;
import com.gemmaportal.dto.ChatRequest;
import com.gemmaportal.dto.ChatResponse;
import com.gemmaportal.service.OllamaService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OllamaService ollamaService;
    private final OllamaProperties ollamaProperties;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, HttpSession session) {
        log.info("Chat request, session={}", session.getId());

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ChatResponse response = ollamaService.chat(request.getMessage(), session.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Chat error: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request, HttpSession session) {
        log.info("Stream request, session={}", session.getId());

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            SseEmitter err = new SseEmitter();
            err.completeWithError(new IllegalArgumentException("Message must not be blank"));
            return err;
        }

        String sessionId = session.getId();
        SseEmitter emitter = new SseEmitter(ollamaProperties.getStreamTimeout().toMillis());

        Thread.ofVirtual().start(() ->
                ollamaService.streamChat(request.getMessage(), sessionId, emitter)
        );

        return emitter;
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory(HttpSession session) {
        return ResponseEntity.ok(ollamaService.getConversationHistory(session.getId()));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(HttpSession session) {
        ollamaService.clearHistory(session.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chat service is running");
    }
}