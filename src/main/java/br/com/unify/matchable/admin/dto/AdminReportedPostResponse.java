package br.com.unify.matchable.admin.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.post.enums.PostOrigin;

/**
 * Publicação denunciada como o moderador precisa vê-la, inclusive já apagada
 * pelo autor ({@code active = false}). A imagem sai em
 * {@code GET /admin/reports/{id}/post-media}.
 */
public record AdminReportedPostResponse(
        UUID id,
        PostOrigin origin,
        String body,
        boolean hasImage,
        String imageDescription,
        boolean active,
        UUID communityId,
        String communityName,
        Instant createdAt
) {
}
