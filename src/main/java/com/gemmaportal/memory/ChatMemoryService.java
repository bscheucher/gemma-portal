package com.gemmaportal.memory;

import com.gemmaportal.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMemoryService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public Conversation getOrCreateConversation(String sessionId, String model) {
        return conversationRepository.findBySessionId(sessionId)
                .orElseGet(() -> conversationRepository.save(Conversation.create(sessionId, model)));
    }

    public Message appendMessage(UUID conversationId, Role role, String content) {
        int nextSeq = conversationRepository.reserveNextSequence(conversationId);
        Conversation ref = conversationRepository.getReferenceById(conversationId);
        return messageRepository.save(Message.create(ref, role, content, nextSeq));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getHistory(String sessionId) {
        return conversationRepository.findBySessionId(sessionId)
                .map(this::loadMessages)
                .orElse(
                        List.of());
    }

    public void clearHistory(String sessionId) {
        conversationRepository.findBySessionId(sessionId)
                .ifPresent(conversationRepository::delete);
    }

    public String roleToString(Role role) {
        return role.name().toLowerCase();
    }

    private List<ChatMessage> loadMessages(Conversation conversation) {
        return messageRepository
                .findByConversationIdOrderBySequenceNumber(conversation.getId())
                .stream()
                .map(m -> new ChatMessage(roleToString(m.getRole()), m.getContent()))
                .toList();
    }
}