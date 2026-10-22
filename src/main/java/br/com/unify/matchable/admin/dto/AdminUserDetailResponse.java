package br.com.unify.matchable.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.unify.matchable.user.enums.UserRole;

/**
 * Ficha do usuário no backoffice. As denúncias contra a conta saem de
 * {@code GET /admin/reports?reportedUserId=}.
 *
 * @param activeSessions         sessões (refresh tokens) não revogadas e não expiradas.
 * @param pendingReportsReceived denúncias OPEN/UNDER_REVIEW contra a conta.
 */
public record AdminUserDetailResponse(
        UUID id,
        String name,
        String lastName,
        String email,
        String cellphone,
        LocalDate birthdate,
        boolean verified,
        UserRole role,
        boolean banned,
        Instant bannedAt,
        String banReason,
        UUID userProfileId,
        String bio,
        long activeSessions,
        long reportsReceived,
        long pendingReportsReceived,
        long reportsMade
) {
}
