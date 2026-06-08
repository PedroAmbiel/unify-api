package br.com.unify.matchable.community.dto;

import java.util.UUID;

public record CommunityCommentResponse(
        UUID id,
        CommunityAuthorResponse author,
        String publishedAt,
        String body,
        Boolean commentedByCurrentUser
) {
}