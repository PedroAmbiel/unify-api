package br.com.unify.matchable.community.dto;

import java.util.List;

public record CommunityPageResponse(
        List<CommunitySummaryResponse> communities,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean hasNext
) {
}