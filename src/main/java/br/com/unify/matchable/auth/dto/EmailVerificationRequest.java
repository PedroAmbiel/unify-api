package br.com.unify.matchable.auth.dto;

public record EmailVerificationRequest(
        String email,
        String code
) {
}