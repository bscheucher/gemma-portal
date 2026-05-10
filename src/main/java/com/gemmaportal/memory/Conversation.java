package com.gemmaportal.memory;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(nullable = false)
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {}

    public static Conversation create(String sessionId, String model) {
        Conversation c = new Conversation();
        c.sessionId = sessionId;
        c.model = model;
        c.createdAt = Instant.now();
        return c;
    }
}