package br.com.unify.matchable.user.dto;

/** Corpo de PUT /users/me/account/password. */
public record UserPasswordChangeRequest(
        String currentPassword,
        String newPassword
) {
}
