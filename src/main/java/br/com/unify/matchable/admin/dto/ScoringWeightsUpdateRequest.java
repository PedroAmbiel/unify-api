package br.com.unify.matchable.admin.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * {@code PUT /admin/scoring-weights/{grupo}}.
 *
 * @param expectedRevision revisão que o admin estava editando (a do GET). Se
 *                         outra pessoa salvou depois, o PUT devolve 409.
 * @param weights          {@code {"MATCH_COMMUNICATION": 25, ...}}; dimensão
 *                         ausente mantém o peso atual. O resultado final do
 *                         grupo é validado inteiro (fatias somando 100).
 */
public record ScoringWeightsUpdateRequest(
        Long expectedRevision,
        Map<String, BigDecimal> weights
) {
}
