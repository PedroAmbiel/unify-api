package br.com.unify.matchable.social.dto;

import java.util.List;

public record FollowRequestPageResponse(
        List<FollowRequestResponse> requests,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean hasNext
) {
}
