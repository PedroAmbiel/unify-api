package br.com.unify.matchable.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatReadResponse(
        UUID conversationId,
        long markedCount,
        Instant readAt
) {
}
