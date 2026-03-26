package br.com.unify.matchable.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
