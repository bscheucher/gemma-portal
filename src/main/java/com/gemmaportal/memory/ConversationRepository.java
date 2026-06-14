package com.gemmaportal.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID>, ConversationRepositoryCustom {
    Optional<Conversation> findBySessionId(String sessionId);

    List<Conversation> findAllByOrderByCreatedAtDesc();
}