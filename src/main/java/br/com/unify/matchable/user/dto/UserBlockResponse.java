package br.com.unify.matchable.user.dto;

import java.time.Instant;
import java.util.UUID;

/** Uma pessoa que EU bloqueei (POST /users/{id}/block e GET /users/me/blocks). */
public record UserBlockResponse(
        UUID userProfileId,
        String name,
        Instant blockedAt
) {
}
