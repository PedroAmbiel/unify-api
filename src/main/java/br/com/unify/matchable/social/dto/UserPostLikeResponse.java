package br.com.unify.matchable.social.dto;

import java.util.UUID;

public record UserPostLikeResponse(UUID postId, long likesCount, boolean likedByCurrentUser) {
}
