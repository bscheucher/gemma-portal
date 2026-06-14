package com.gemmaportal.memory;

import java.util.UUID;

public interface ConversationRepositoryCustom {

    int reserveNextSequence(UUID conversationId);
}