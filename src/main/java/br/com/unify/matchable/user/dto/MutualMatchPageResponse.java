package br.com.unify.matchable.user.dto;

import java.util.List;

public record MutualMatchPageResponse(
        List<MutualMatchSummaryResponse> matches,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean hasNext
) {
}