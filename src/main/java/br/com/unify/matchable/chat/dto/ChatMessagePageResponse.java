package br.com.unify.matchable.chat.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param messages na paginação normal, da mais recente para a mais antiga.
 *                 No modo polling ({@code since}), em ordem crescente.
 */
public record ChatMessagePageResponse(
        List<ChatMessageResponse> messages,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        long unreadCount,
        Instant serverTime
) {
}
