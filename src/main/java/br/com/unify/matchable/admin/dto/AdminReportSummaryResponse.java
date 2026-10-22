package br.com.unify.matchable.admin.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;

/**
 * Denúncia na fila de moderação.
 *
 * @param reportedPost nulo para denúncia de perfil. Também nulo em denúncias
 *                     antigas de post de comunidade apagado antes da semana 07,
 *                     quando a linha ainda era apagada (FK {@code on delete set null});
 *                     hoje todo post apagado no app é soft delete.
 */
public record AdminReportSummaryResponse(
        UUID id,
        UserReportStatus status,
        ReportReason reason,
        String reasonLabel,
        String description,
        Instant createdAt,
        AdminUserSummaryResponse reporter,
        AdminUserSummaryResponse reportedUser,
        AdminReportedPostResponse reportedPost,
        String adminNote,
        Instant reviewedAt,
        String reviewedByName,
        Instant resolvedAt
) {
}
