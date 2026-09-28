package br.com.unify.matchable.social.dto;

import java.util.List;

/**
 * Sem totalElements/totalPages de propósito: scroll infinito não precisa e evita COUNT por página.
 *
 * @param visible {@code false} só em {@code GET /users/{id}/posts} quando o autor restringiu os
 *                posts a seguidores e quem lê não o segue — o cliente mostra "posts privados"
 *                em vez de "nenhuma publicação". Sempre {@code true} no feed.
 */
public record UserFeedPageResponse(
        List<UserPostResponse> posts,
        Integer page,
        Integer size,
        Boolean hasNext,
        Boolean visible
) {

    public UserFeedPageResponse(List<UserPostResponse> posts, Integer page, Integer size, Boolean hasNext) {
        this(posts, page, size, hasNext, true);
    }

    public static UserFeedPageResponse hidden(int page, int size) {
        return new UserFeedPageResponse(List.of(), page, size, false, false);
    }
}
