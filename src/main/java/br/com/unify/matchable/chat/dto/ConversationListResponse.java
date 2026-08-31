package br.com.unify.matchable.chat.dto;

import java.time.Instant;
import java.util.List;

/**
 * Envelope de GET /chats.
 *
 * @param serverTime relógio do servidor: o cliente usa como base do parâmetro {@code since} do polling.
 */
public record ConversationListResponse(
        List<ConversationSummaryResponse> conversations,
        long totalUnread,
        Instant serverTime
) {
}
