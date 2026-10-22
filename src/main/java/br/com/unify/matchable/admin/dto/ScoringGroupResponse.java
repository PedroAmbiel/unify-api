package br.com.unify.matchable.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * Um grupo de pesos em {@code GET /admin/scoring-weights}.
 *
 * @param requiredPercentTotal 100 quando as dimensões PERCENT do grupo precisam somar 100; nulo se não há regra de soma.
 * @param revision             revisão salva; vai de volta no PUT como {@code expectedRevision}.
 * @param updatedAt            último save pelo backoffice; nulo se o grupo nunca foi alterado.
 */
public record ScoringGroupResponse(
        String code,
        String label,
        String description,
        Double requiredPercentTotal,
        long revision,
        Instant updatedAt,
        String updatedByName,
        List<ScoringDimensionResponse> dimensions
) {
}
