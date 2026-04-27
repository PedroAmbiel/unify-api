package br.com.unify.matchable.auth.dto;

public record ResetPasswordRequest(
        String token,
        String password
) {
}