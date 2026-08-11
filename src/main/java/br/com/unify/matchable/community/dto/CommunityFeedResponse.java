package br.com.unify.matchable.community.dto;

import br.com.unify.matchable.common.dto.PageResponse;

/**
 * Feed de uma comunidade. O campo {@code community} traz o cabecalho da
 * comunidade resolvida (o feed pode ser pedido sem {@code communityId}) e
 * {@code posts} e uma pagina de publicacoes, nao mais uma lista aberta.
 */
public record CommunityFeedResponse(
        CommunitySummaryResponse community,
        PageResponse<CommunityPostResponse> posts
) {
}
