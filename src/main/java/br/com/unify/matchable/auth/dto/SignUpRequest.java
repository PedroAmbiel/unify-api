package br.com.unify.matchable.auth.dto;

public record SignUpRequest(
        String name,
        String lastName,
        String email,
        String password
) {
}
