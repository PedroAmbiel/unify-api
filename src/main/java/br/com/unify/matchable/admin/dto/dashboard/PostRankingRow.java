package br.com.unify.matchable.admin.dto.dashboard;

import java.time.Instant;
import java.util.UUID;

/** Publicação do período entre as de maior engajamento (curtidas + comentários). */
public record PostRankingRow(
        UUID id,
        String origin,
        String excerpt,
        String communityName,
        String authorName,
        long likes,
        long comments,
        Instant createdAt,
        boolean active,
        boolean moderated
) {
}
