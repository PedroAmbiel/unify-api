package br.com.unify.matchable.chat.dto;

import java.util.UUID;

/**
 * @param replyToMessageId mensagem da mesma conversa que esta responde (citação). Opcional.
 */
public record ChatMessageCreateRequest(String body, UUID replyToMessageId) {
}
