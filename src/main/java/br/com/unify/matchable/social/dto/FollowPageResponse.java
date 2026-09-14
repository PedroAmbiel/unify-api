package br.com.unify.matchable.social.dto;

import java.util.List;

public record FollowPageResponse(
        List<FollowedProfileSummaryResponse> profiles,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean hasNext
) {
}
