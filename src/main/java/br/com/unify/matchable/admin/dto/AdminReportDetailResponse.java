package br.com.unify.matchable.admin.dto;

import java.util.List;

/**
 * Detalhe da denúncia com o contexto que o moderador precisa para decidir: a
 * ficha do denunciado e TODAS as denúncias contra ele (mais recente primeiro).
 */
public record AdminReportDetailResponse(
        AdminReportSummaryResponse report,
        AdminUserDetailResponse reportedUser,
        List<AdminReportSummaryResponse> reportedUserHistory
) {
}
