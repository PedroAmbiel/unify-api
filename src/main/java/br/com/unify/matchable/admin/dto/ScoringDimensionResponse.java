package br.com.unify.matchable.admin.dto;

import br.com.unify.matchable.scoring.enums.ScoringUnit;

/**
 * Uma dimensão de pontuação vista pelo backoffice.
 *
 * @param code        nome da constante ({@code MATCH_COMMUNICATION}): chave do mapa no PUT.
 * @param section     subtítulo dentro do grupo (Compatibilidade, Penalidades, Afinidade...).
 * @param lookupCode  catálogo de perfil que alimenta a dimensão ({@code /admin/lookups/{lookupCode}}); nulo se não há.
 * @param weight      peso salvo (ou o padrão, se nunca foi salvo).
 */
public record ScoringDimensionResponse(
        String code,
        String section,
        String label,
        String description,
        ScoringUnit unit,
        String lookupCode,
        String lookupLabel,
        double weight,
        double defaultWeight,
        double minWeight,
        double maxWeight
) {
}
