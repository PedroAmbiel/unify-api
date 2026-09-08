package br.com.unify.matchable.chat.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.chat.enums.ChatMessageType;

/**
 * @param mediaUrl    URL relativa protegida por JWT: /chats/messages/{id}/media. Nulo para
 *                    TEXT e para mensagens apagadas.
 * @param deliveredAt destinatário buscou a mensagem (estado "entregue"). Nulo = só enviada.
 * @param readAt      destinatário abriu a conversa (estado "vista").
 * @param editedAt    texto alterado pelo remetente. Nulo = nunca editada.
 * @param deletedAt   exclusão lógica: body e mídia vêm nulos; o cliente mostra "apagada".
 */
public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderUserProfileId,
        String senderName,
        boolean fromMe,
        ChatMessageType type,
        String body,
        String mediaUrl,
        String mediaContentType,
        Long mediaSizeBytes,
        Integer mediaDurationSeconds,
        Instant createdAt,
        Instant deliveredAt,
        Instant readAt,
        Instant editedAt,
        Instant deletedAt
) {
}
