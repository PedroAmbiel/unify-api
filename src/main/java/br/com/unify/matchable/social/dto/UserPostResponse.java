package br.com.unify.matchable.social.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.social.enums.FeedSource;

/**
 * Publicação do feed unificado ({@code GET /users/feed}) e das listas por
 * perfil. {@code community} só vem preenchida quando {@code origin == COMMUNITY};
 * {@code mediaUrl} aponta para {@code /users/posts/{id}/media} (pessoal) ou
 * {@code /communities/posts/{id}/media} (comunidade). {@code feedSource} diz por
 * que o post entrou no feed ranqueado da aba Início e é nulo fora dele
 * (listas por perfil).
 */
public record UserPostResponse(
        UUID id,
        PostOrigin origin,
        UserPostCommunityResponse community,
        UserPostAuthorResponse author,
        String body,
        String mediaUrl,
        Instant createdAt,
        Instant editedAt,
        long likesCount,
        long commentsCount,
        boolean likedByCurrentUser,
        boolean commentedByCurrentUser,
        FeedSource feedSource
) {
}
