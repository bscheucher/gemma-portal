package com.gemmaportal.controller;

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

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, HttpSession session) {
        log.info("Chat request, session={}", session.getId());

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ChatResponse response = ollamaService.chat(request.getMessage(), session);
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

        // Fetch history on the request thread while session context is guaranteed
        List<ChatMessage> history = ollamaService.getHistory(session);

        SseEmitter emitter = new SseEmitter(120_000L);

        // Virtual thread: blocks on Ollama I/O without tying up a platform thread
        Thread.ofVirtual().start(() ->
                ollamaService.streamChat(request.getMessage(), history, emitter)
        );

        return emitter;
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory(HttpSession session) {
        return ResponseEntity.ok(ollamaService.getConversationHistory(session));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(HttpSession session) {
        ollamaService.clearHistory(session);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chat service is running");
    }
}