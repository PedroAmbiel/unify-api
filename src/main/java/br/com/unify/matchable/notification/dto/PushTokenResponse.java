package br.com.unify.matchable.notification.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.notification.enums.PushPlatform;

/**
 * O app guarda {@code id} para descadastrar no logout
 * ({@code DELETE /notifications/push-tokens/{id}}): assim o token não trafega
 * na URL nem aparece em access log.
 */
public record PushTokenResponse(UUID id, PushPlatform platform, Instant updatedAt) {
}
