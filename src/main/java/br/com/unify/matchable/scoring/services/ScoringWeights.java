package br.com.unify.matchable.scoring.services;

import java.util.Map;

import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.enums.ScoringGroup;
import br.com.unify.matchable.scoring.enums.ScoringUnit;

/**
 * Foto imutável dos pesos vigentes. Ler um peso é um acesso a array pelo
 * ordinal da dimensão (a ideia do EnumMap, sem hash nem boxing).
 *
 * Quem pontua pega UMA foto no começo da requisição e usa só ela: os 250
 * candidatos de um discovery são avaliados com os mesmos pesos mesmo que o
 * backoffice salve no meio — nunca metade com a regra antiga e metade com a
 * nova, nem uma soma de fatias diferente de 100.
 */
public final class ScoringWeights {

    private static final ScoringDimension[] DIMENSIONS = ScoringDimension.values();
    private static final ScoringGroup[] GROUPS = ScoringGroup.values();
    private static final ScoringWeights DEFAULTS = new ScoringWeights(defaultValues(), new long[GROUPS.length]);

    /** Por {@code ScoringDimension.ordinal()}. */
    private final double[] weights;
    /** Por {@code ScoringGroup.ordinal()}: revisão salva no banco (0 = nunca alterado). */
    private final long[] revisions;
    /** Por {@code ScoringGroup.ordinal()}: soma das dimensões PERCENT do grupo. */
    private final double[] percentTotals;

    private ScoringWeights(double[] weights, long[] revisions) {
        this.weights = weights;
        this.revisions = revisions;
        this.percentTotals = new double[GROUPS.length];
        for (ScoringDimension dimension : DIMENSIONS) {
            if (dimension.unit() == ScoringUnit.PERCENT) {
                percentTotals[dimension.group().ordinal()] += weights[dimension.ordinal()];
            }
        }
    }

    /** Padrões do código (antes do boot ler o banco e em testes sem CDI). */
    public static ScoringWeights defaults() {
        return DEFAULTS;
    }

    /** Pesos lidos do banco; dimensão ou grupo ausente fica com o padrão (revisão 0). */
    public static ScoringWeights of(Map<ScoringDimension, Double> weights, Map<ScoringGroup, Long> revisions) {
        double[] values = defaultValues();
        weights.forEach((dimension, weight) -> values[dimension.ordinal()] = weight);
        long[] groupRevisions = new long[GROUPS.length];
        revisions.forEach((group, revision) -> groupRevisions[group.ordinal()] = revision);
        return new ScoringWeights(values, groupRevisions);
    }

    public double get(ScoringDimension dimension) {
        return weights[dimension.ordinal()];
    }

    public long revision(ScoringGroup group) {
        return revisions[group.ordinal()];
    }

    /** Soma das fatias do grupo (100 sempre que salvo pelo backoffice). */
    public double percentTotal(ScoringGroup group) {
        return percentTotals[group.ordinal()];
    }

    /** Nova foto com os pesos de um grupo trocados; as demais dimensões seguem iguais. */
    public ScoringWeights withGroup(ScoringGroup group, long revision, Map<ScoringDimension, Double> groupWeights) {
        double[] values = weights.clone();
        groupWeights.forEach((dimension, weight) -> {
            if (dimension.group() != group) {
                throw new IllegalArgumentException("Dimensão " + dimension + " não pertence ao grupo " + group);
            }
            values[dimension.ordinal()] = weight;
        });
        long[] groupRevisions = revisions.clone();
        groupRevisions[group.ordinal()] = revision;
        return new ScoringWeights(values, groupRevisions);
    }

    /** Quantas dimensões estão fora do padrão do código. */
    public long customizedCount() {
        long count = 0;
        for (ScoringDimension dimension : DIMENSIONS) {
            if (Double.compare(weights[dimension.ordinal()], dimension.defaultWeight()) != 0) {
                count++;
            }
        }
        return count;
    }

    private static double[] defaultValues() {
        double[] values = new double[DIMENSIONS.length];
        for (ScoringDimension dimension : DIMENSIONS) {
            values[dimension.ordinal()] = dimension.defaultWeight();
        }
        return values;
    }
}
