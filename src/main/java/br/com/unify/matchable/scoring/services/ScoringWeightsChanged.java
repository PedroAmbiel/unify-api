package br.com.unify.matchable.scoring.services;

import java.util.Map;

import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.enums.ScoringGroup;

/**
 * Pesos de um grupo salvos pelo backoffice. Disparado dentro da transação do
 * save; o {@link ScoringWeightRegistry} só troca a memória depois do commit
 * (rollback = memória intacta).
 *
 * @param weights todas as dimensões do grupo, já com os valores novos.
 */
public record ScoringWeightsChanged(ScoringGroup group, long revision, Map<ScoringDimension, Double> weights) {

    public ScoringWeightsChanged {
        weights = Map.copyOf(weights);
    }
}
