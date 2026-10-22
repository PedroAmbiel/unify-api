package br.com.unify.matchable.scoring.services;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import br.com.unify.matchable.scoring.entity.ScoringGroupRevision;
import br.com.unify.matchable.scoring.entity.ScoringWeight;
import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.enums.ScoringGroup;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

/**
 * Pesos de pontuação em memória, consultados a cada discovery, feed e
 * descoberta de comunidades sem ir ao banco.
 *
 * <ul>
 *   <li>Boot: lê scoring_weights/scoring_group_revisions ({@link #reload()}).</li>
 *   <li>Backoffice salvou: troca a foto do grupo logo depois do commit
 *       ({@link ScoringWeightsChanged}, fase AFTER_SUCCESS).</li>
 *   <li>Leitura: {@link #current()} é um único read volátil de uma foto
 *       imutável ({@link ScoringWeights}) — sem lock no caminho quente.</li>
 * </ul>
 *
 * Cada instância da API tem a própria memória: com mais de uma réplica, as
 * outras só enxergam o peso novo ao reiniciar (ou chamando {@link #reload()}).
 */
@ApplicationScoped
public class ScoringWeightRegistry {

    private static final Logger LOG = Logger.getLogger(ScoringWeightRegistry.class);

    private volatile ScoringWeights current = ScoringWeights.defaults();

    void onStart(@Observes StartupEvent event) {
        reload();
        LOG.infof("Pesos de pontuação carregados do banco: %d de %d dimensões fora do padrão",
                current.customizedCount(), ScoringDimension.values().length);
    }

    public ScoringWeights current() {
        return current;
    }

    /** Relê o banco (fonte da verdade) e troca a foto inteira. Lê só o que já foi commitado. */
    public synchronized void reload() {
        current = QuarkusTransaction.requiringNew().call(ScoringWeightRegistry::loadFromDatabase);
    }

    void onWeightsChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) ScoringWeightsChanged change) {
        apply(change);
    }

    /**
     * Saves seguidos do mesmo grupo são serializados pela trava da revisão no
     * banco, mas os AFTER_SUCCESS rodam em threads diferentes e podem chegar
     * fora de ordem: só a revisão mais nova entra.
     */
    synchronized void apply(ScoringWeightsChanged change) {
        if (change.revision() <= current.revision(change.group())) {
            return;
        }
        current = current.withGroup(change.group(), change.revision(), change.weights());
    }

    private static ScoringWeights loadFromDatabase() {
        Map<ScoringDimension, Double> weights = new EnumMap<>(ScoringDimension.class);
        List<ScoringWeight> rows = ScoringWeight.listAll();
        for (ScoringWeight row : rows) {
            Optional<ScoringDimension> dimension = ScoringDimension.fromCode(row.dimension);
            if (dimension.isPresent()) {
                weights.put(dimension.get(), row.weight.doubleValue());
            } else {
                LOG.warnf("Peso salvo para a dimensão '%s', que não existe mais no código: ignorado", row.dimension);
            }
        }

        Map<ScoringGroup, Long> revisions = new EnumMap<>(ScoringGroup.class);
        List<ScoringGroupRevision> revisionRows = ScoringGroupRevision.listAll();
        for (ScoringGroupRevision row : revisionRows) {
            ScoringGroup.fromCode(row.scoringGroup).ifPresent(group -> revisions.put(group, row.revision));
        }

        return ScoringWeights.of(weights, revisions);
    }
}
