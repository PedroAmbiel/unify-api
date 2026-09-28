package br.com.unify.matchable.user.dto;

import java.util.UUID;

/**
 * O que o Encontros/perfil público precisa do áudio de outra pessoa: id e
 * duração. A URL é montada no cliente com o userProfileId
 * ({@code /users/me/profile/public/audio/{id}?userProfileId=}).
 */
public record UserProfileAudioPublicResponse(UUID id, int durationSeconds) {
}
