package br.com.unify.matchable.social.dto;

import java.util.List;

public record UserPostCommentPageResponse(
        List<UserPostCommentResponse> comments,
        Integer page,
        Integer size,
        Long totalElements,
        Boolean hasNext
) {
}
