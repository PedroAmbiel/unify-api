package br.com.unify.matchable.notification.services;

import java.util.UUID;

/**
 * Payload do evento CDI disparado a cada notificação criada (ou atualizada
 * por agrupamento). Só o id: quem entrega o push relê a linha numa transação
 * própria, depois do commit da origem.
 */
public record NotificationDispatchEvent(UUID notificationId) {
}
