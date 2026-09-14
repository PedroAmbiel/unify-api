package br.com.unify.matchable.social.dto;

import java.time.Instant;
import java.util.UUID;

public record UserPostCommentResponse(
        UUID id,
        UserPostAuthorResponse author,
        String body,
        Instant createdAt,
        boolean commentedByCurrentUser
) {
}
