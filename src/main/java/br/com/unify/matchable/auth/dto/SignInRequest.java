package br.com.unify.matchable.auth.dto;

public record SignInRequest(
        String login,
        String password
) {
}
