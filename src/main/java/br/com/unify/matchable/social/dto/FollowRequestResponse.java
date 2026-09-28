package br.com.unify.matchable.social.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido para seguir pendente. Os dados de perfil são da OUTRA pessoa: quem
 * pediu (lista de recebidos) ou quem eu pedi para seguir (lista de enviados).
 */
public record FollowRequestResponse(
        UUID id,
        UUID userProfileId,
        UUID userId,
        String name,
        String avatarUrl,
        Instant createdAt
) {
}
