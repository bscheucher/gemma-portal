package com.gemmaportal.controller;

import com.gemmaportal.dto.ChatMessage;
import com.gemmaportal.dto.ChatRequest;
import com.gemmaportal.dto.ChatResponse;
import com.gemmaportal.service.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final OllamaService ollamaService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request");

        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ChatResponse response = ollamaService.chat(request.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing chat request: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory() {
        log.info("Received request for conversation history");
        return ResponseEntity.ok(ollamaService.getConversationHistory());
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory() {
        log.info("Received request to clear conversation history");
        ollamaService.clearHistory();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chat service is running");
    }
}
