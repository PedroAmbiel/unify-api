package br.com.unify.matchable.user.dto;

/** Envelope de GET /users/me/profile/audio: {@code audio = null} quando não há áudio (evita 404/204 no cliente). */
public record UserProfileAudioStatusResponse(UserProfileAudioResponse audio) {
}
