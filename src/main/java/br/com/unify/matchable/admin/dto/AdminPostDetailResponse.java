package br.com.unify.matchable.admin.dto;

import java.util.List;

/**
 * Detalhe da publicação para a decisão de moderação: o post, a ficha do autor
 * e todas as denúncias que apontam para ele (mais recente primeiro).
 */
public record AdminPostDetailResponse(
        AdminReportedPostResponse post,
        AdminUserDetailResponse author,
        List<AdminReportSummaryResponse> reports
) {
}
