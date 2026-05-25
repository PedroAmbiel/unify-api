package br.com.unify.matchable.community.dto;

import java.util.UUID;

public record CommunityPostResponse(
        UUID id,
        CommunityAuthorResponse author,
        String publishedAt,
        String body,
        String mediaData,
        Long likesCount,
        Long commentsCount,
        Boolean likedByCurrentUser,
        Boolean commentedByCurrentUser
) {
}