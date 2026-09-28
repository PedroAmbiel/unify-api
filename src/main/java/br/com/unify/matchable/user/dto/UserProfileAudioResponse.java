package br.com.unify.matchable.user.dto;

import java.time.Instant;
import java.util.UUID;

/** Metadados do PRÓPRIO áudio de apresentação (tela Perfil). {@code url} é relativa, como em UserProfileImageResponse. */
public record UserProfileAudioResponse(
        UUID id,
        int durationSeconds,
        String contentType,
        long sizeBytes,
        Instant createdAt,
        String url
) {
}
