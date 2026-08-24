package br.com.unify.matchable.community.dto;

import java.time.Instant;
import java.util.UUID;

/** Solicitação pendente de entrada em comunidade privada, para a fila de moderação. */
public record CommunityJoinRequestResponse(
        UUID id,
        UUID userProfileId,
        String name,
        String avatarData,
        Instant requestedAt
) {
}
