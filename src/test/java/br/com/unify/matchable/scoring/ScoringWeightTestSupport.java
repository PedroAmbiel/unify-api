package br.com.unify.matchable.scoring;

import java.math.BigDecimal;
import java.util.Map;

import br.com.unify.matchable.scoring.entity.ScoringGroupRevision;
import br.com.unify.matchable.scoring.entity.ScoringWeight;
import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.services.ScoringWeightRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * Pesos são estado global (banco + memória da API) compartilhado pela suíte:
 * todo teste que mexe neles chama {@link #restoreDefaults} no fim. As
 * gravações são commitadas (transação própria), então funcionam também dentro
 * de um teste {@code @TestTransaction}.
 */
public final class ScoringWeightTestSupport {

    private ScoringWeightTestSupport() {
    }

    /** Grava pesos direto no banco e recarrega a memória, como num boot. */
    public static void override(ScoringWeightRegistry registry, Map<ScoringDimension, Double> weights) {
        QuarkusTransaction.requiringNew().run(() -> weights.forEach(ScoringWeightTestSupport::store));
        registry.reload();
    }

    /** Volta tudo ao padrão do código: pesos, revisões e autoria. */
    public static void restoreDefaults(ScoringWeightRegistry registry) {
        QuarkusTransaction.requiringNew().run(() -> {
            for (ScoringDimension dimension : ScoringDimension.values()) {
                store(dimension, dimension.defaultWeight());
            }
            ScoringGroupRevision.getEntityManager()
                    .createNativeQuery("update scoring_group_revisions set revision = 0, updated_at = null, fk_updated_by = null")
                    .executeUpdate();
        });
        registry.reload();
    }

    private static void store(ScoringDimension dimension, double weight) {
        BigDecimal value = BigDecimal.valueOf(weight).setScale(2);
        ScoringWeight row = ScoringWeight.findById(dimension.name());
        if (row == null) {
            row = new ScoringWeight();
            row.dimension = dimension.name();
            row.weight = value;
            row.persist();
        } else {
            row.weight = value;
        }
    }
}
