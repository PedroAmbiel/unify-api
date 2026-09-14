package br.com.unify.matchable.social.dto;

import java.util.List;

/** Sem totalElements/totalPages de propósito: scroll infinito não precisa e evita COUNT por página. */
public record UserFeedPageResponse(
        List<UserPostResponse> posts,
        Integer page,
        Integer size,
        Boolean hasNext
) {
}
