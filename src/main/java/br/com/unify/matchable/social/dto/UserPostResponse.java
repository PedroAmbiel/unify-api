package br.com.unify.matchable.social.dto;

import java.time.Instant;
import java.util.UUID;

public record UserPostResponse(
        UUID id,
        UserPostAuthorResponse author,
        String body,
        String mediaUrl,
        Instant createdAt
) {
}
