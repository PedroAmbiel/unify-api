package br.com.unify.matchable.notification.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.notification.enums.NotificationType;

/**
 * Item da caixa de entrada. {@code referenceId}/{@code contextId} seguem o
 * contrato descrito em {@link NotificationType}; {@code actor} é nulo em
 * avisos do sistema (ex.: atualização de denúncia).
 */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        UUID referenceId,
        UUID contextId,
        String title,
        String body,
        int eventCount,
        boolean read,
        Instant createdAt,
        NotificationActorResponse actor
) {
}
