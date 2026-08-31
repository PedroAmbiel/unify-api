package br.com.unify.matchable.chat.dto;

import br.com.unify.matchable.chat.enums.ChatMessageType;

/** Resultado da validação/normalização de mídia. */
public record ChatMediaPayload(
        ChatMessageType type,
        byte[] bytes,
        String contentType,
        Integer durationSeconds
) {
}
