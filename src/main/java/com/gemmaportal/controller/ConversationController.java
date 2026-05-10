package com.gemmaportal.controller;

import com.gemmaportal.dto.ChatMessage;
import com.gemmaportal.memory.ConversationRepository;
import com.gemmaportal.memory.ConversationSummary;
import com.gemmaportal.memory.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @GetMapping
    public List<ConversationSummary> list() {
        return conversationRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(c -> new ConversationSummary(
                        c.getId(),
                        c.getModel(),
                        c.getCreatedAt(),
                        messageRepository.countByConversationId(c.getId())))
                .toList();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<ChatMessage>> messages(@PathVariable UUID id) {
        if (!conversationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<ChatMessage> messages = messageRepository
                .findByConversationIdOrderBySequenceNumber(id)
                .stream()
                .map(m -> new ChatMessage(m.getRole().name().toLowerCase(), m.getContent()))
                .toList();
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!conversationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        conversationRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}