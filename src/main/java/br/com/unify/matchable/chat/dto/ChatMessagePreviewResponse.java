package br.com.unify.matchable.chat.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.chat.enums.ChatMessageType;

/**
 * Última mensagem na listagem de conversas.
 *
 * @param preview para TEXT: os primeiros 120 caracteres. Para mídia: "Imagem" / "Áudio de N segundos".
 */
public record ChatMessagePreviewResponse(
        UUID id,
        ChatMessageType type,
        String preview,
        boolean fromMe,
        Instant createdAt
) {
}
