package br.com.unify.matchable.user.dto;

import java.util.UUID;

import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.enums.UserRole;

/**
 * {@code GET /users/me} (semana 06): identidade da conta autenticada. O papel
 * vem do BANCO, não do token — o backoffice usa esta resposta como fonte da
 * verdade para liberar a área administrativa.
 */
public record CurrentUserResponse(
        UUID id,
        String name,
        String lastName,
        String email,
        String cellphone,
        UserRole role,
        boolean banned
) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.id,
                user.name,
                user.lastName,
                user.email,
                user.cellphone,
                user.role == null ? UserRole.USER : user.role,
                user.banned
        );
    }
}
