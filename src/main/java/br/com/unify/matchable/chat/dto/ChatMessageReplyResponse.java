package br.com.unify.matchable.chat.dto;

import java.util.UUID;

import br.com.unify.matchable.chat.enums.ChatMessageType;

/**
 * Citação da mensagem respondida, montada a partir da mensagem original no momento
 * da leitura (edição e exclusão da original se refletem aqui).
 *
 * @param preview texto (TEXT) ou legenda (mídia) truncado. Nulo para mídia sem legenda
 *                e para mensagem apagada — o cliente monta o rótulo pelo {@code type}.
 * @param deleted a original foi apagada: o cliente mostra "Mensagem apagada".
 */
public record ChatMessageReplyResponse(
        UUID id,
        UUID senderUserProfileId,
        String senderName,
        boolean fromMe,
        ChatMessageType type,
        String preview,
        Integer mediaDurationSeconds,
        boolean deleted
) {
}
