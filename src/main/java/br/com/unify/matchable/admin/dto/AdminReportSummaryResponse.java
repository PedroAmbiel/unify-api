package br.com.unify.matchable.admin.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;

/**
 * Denúncia na fila de moderação.
 *
 * @param reportedPost nulo para denúncia de perfil ou quando o post foi apagado
 *                     fisicamente (FK {@code on delete set null}).
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
