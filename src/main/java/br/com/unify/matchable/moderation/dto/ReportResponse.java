package br.com.unify.matchable.moderation.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;

public record ReportResponse(
        UUID id,
        UUID reportedUserId,
        UUID reportedPostId,
        ReportReason reason,
        String description,
        UserReportStatus status,
        Instant createdAt
) {
}
