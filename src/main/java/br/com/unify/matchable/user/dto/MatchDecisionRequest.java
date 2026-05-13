package br.com.unify.matchable.user.dto;

import java.util.UUID;

public record MatchDecisionRequest(
        UUID targetProfileId,
        Boolean accepted
) {
}