package br.com.unify.matchable.notification.dto;

import java.util.UUID;

/**
 * Quem causou o evento. {@code avatarUrl} reaproveita o endpoint autenticado
 * {@code GET /communities/users/{userId}/avatar} e só vem preenchido quando a
 * pessoa tem foto de perfil ativa.
 */
public record NotificationActorResponse(
        UUID userId,
        UUID userProfileId,
        String name,
        String avatarUrl
) {
}
