package br.com.unify.matchable.admin.dto;

import java.time.Instant;

/**
 * Publicação na fila "Publicações denunciadas" do backoffice (semana 07): o
 * post aparece uma vez, com as contagens das denúncias que recebeu.
 */
public record AdminPostSummaryResponse(
        AdminReportedPostResponse post,
        AdminUserSummaryResponse author,
        long reportsTotal,
        long reportsPending,
        Instant lastReportedAt
) {
}
