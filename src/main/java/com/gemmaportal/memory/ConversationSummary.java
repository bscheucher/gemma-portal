package com.gemmaportal.memory;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(UUID id, String model, Instant createdAt, long messageCount) {}