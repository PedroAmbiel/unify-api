package br.com.unify.matchable.chat.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.user.dto.UserProfileImageResponse;

/** Item da listagem GET /chats. */
public record ConversationSummaryResponse(
        UUID conversationId,
        UUID matchId,
        UUID otherUserId,
        UUID otherUserProfileId,
        String otherUserName,
        UserProfileImageResponse otherUserPhoto,
        ChatMessagePreviewResponse lastMessage,
        long unreadCount,
        Instant lastMessageAt
) {
}
