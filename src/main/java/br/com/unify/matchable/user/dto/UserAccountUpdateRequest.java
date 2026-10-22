package br.com.unify.matchable.user.dto;

/**
 * Corpo de PUT /users/me/account: dados da conta (não do perfil público).
 * {@code cellphone} vazio ou nulo apaga o número. E-mail não muda por aqui —
 * trocar e-mail exigiria nova verificação.
 */
public record UserAccountUpdateRequest(
        String name,
        String lastName,
        String cellphone
) {
}
