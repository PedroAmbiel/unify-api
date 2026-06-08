package br.com.unify.matchable.community.dto;

import java.util.List;
import java.util.UUID;

public record CommunityCommentsResponse(
        UUID postId,
        List<CommunityCommentResponse> comments
) {
}