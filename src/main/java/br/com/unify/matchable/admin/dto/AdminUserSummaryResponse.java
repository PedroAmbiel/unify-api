package br.com.unify.matchable.admin.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.enums.UserRole;

public record AdminUserSummaryResponse(
        UUID id,
        String name,
        String lastName,
        String email,
        UserRole role,
        boolean verified,
        boolean banned,
        Instant bannedAt
) {

    public static AdminUserSummaryResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new AdminUserSummaryResponse(
                user.id,
                user.name,
                user.lastName,
                user.email,
                user.role == null ? UserRole.USER : user.role,
                user.verified,
                user.banned,
                user.bannedAt
        );
    }
}
