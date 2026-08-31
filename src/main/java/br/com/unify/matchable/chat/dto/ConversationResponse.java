package br.com.unify.matchable.chat.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.user.dto.UserProfileImageResponse;

/** Retorno de POST /chats. */
public record ConversationResponse(
        UUID conversationId,
        UUID matchId,
        UUID otherUserId,
        UUID otherUserProfileId,
        String otherUserName,
        Integer otherUserAge,
        UserProfileImageResponse otherUserPhoto,
        Instant createdAt,
        Instant lastMessageAt
) {
}
