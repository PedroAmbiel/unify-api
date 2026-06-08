package br.com.unify.matchable.user.dto;

import java.time.Instant;
import java.util.UUID;

public record MatchDecisionResponse(
        UUID id,
        UUID starterProfileId,
        UUID pendingProfileId,
        Instant createdAt,
        boolean starterAccepted,
        Boolean pendingAccepted,
        boolean mutualMatch
) {
}