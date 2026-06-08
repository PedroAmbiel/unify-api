package br.com.unify.matchable.community.dto;

import java.util.UUID;

public record CommunityLikeResponse(
        UUID postId,
        Long likesCount,
        Boolean likedByCurrentUser
) {
}