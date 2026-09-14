package br.com.unify.matchable.community.dto;

import java.time.Instant;
import java.util.UUID;

public record CommunityPostResponse(
        UUID id,
        CommunityAuthorResponse author,
        String publishedAt,
        String body,
        String mediaData,
        Instant editedAt,
        Long likesCount,
        Long commentsCount,
        Boolean likedByCurrentUser,
        Boolean commentedByCurrentUser
) {
}
