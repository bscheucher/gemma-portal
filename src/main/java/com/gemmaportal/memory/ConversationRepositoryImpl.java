package com.gemmaportal.memory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.UUID;

@SuppressWarnings("unused") // wired by Spring Data via the *Impl naming convention
public class ConversationRepositoryImpl implements ConversationRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public int reserveNextSequence(UUID conversationId) {
        Object result = entityManager.createNativeQuery("""
                        UPDATE conversations
                           SET next_sequence = next_sequence + 1
                         WHERE id = :id
                         RETURNING next_sequence - 1
                        """)
                .setParameter("id", conversationId)
                .getSingleResult();
        return ((Number) result).intValue();
    }
}