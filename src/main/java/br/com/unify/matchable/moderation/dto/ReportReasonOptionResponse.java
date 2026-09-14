package br.com.unify.matchable.moderation.dto;

import br.com.unify.matchable.moderation.enums.ReportReason;

public record ReportReasonOptionResponse(ReportReason value, String description) {
}
