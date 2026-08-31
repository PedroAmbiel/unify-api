package br.com.unify.matchable.chat.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.chat.enums.ChatMessageType;

/**
 * @param mediaUrl URL relativa protegida por JWT: /chats/messages/{id}/media. Nulo para TEXT.
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
        Instant readAt
) {
}
