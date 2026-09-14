package br.com.unify.matchable.moderation.dto;

import java.util.UUID;

import br.com.unify.matchable.moderation.enums.ReportReason;

public record ReportCreateRequest(
        UUID reportedUserId,
        UUID reportedPostId,
        ReportReason reason,
        String description
) {
}
