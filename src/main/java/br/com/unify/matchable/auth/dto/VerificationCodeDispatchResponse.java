package br.com.unify.matchable.auth.dto;

public record VerificationCodeDispatchResponse(
        String email,
        long expiresInSeconds,
        String message
) {
}