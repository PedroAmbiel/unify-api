package br.com.unify.matchable.user.services;

/**
 * Constantes do algoritmo de compatibilidade do Unify.
 *
 * Os PESOS de cada dimensão e das penalidades não moram mais aqui: são
 * administráveis pelo backoffice (ScoringDimension MATCH_*, padrão no próprio
 * enum, valor vigente em ScoringWeightRegistry). Regra de ouro mantida pela
 * validação do backoffice: as fatias somam exatamente 100. O score final é
 * normalizado pelos pesos efetivamente avaliáveis (ver
 * UserMatchServiceImplementation.calculateCompatibilityScore), de modo que perfis
 * incompletos não ganhem nem percam pontos "de graça".
 *
 * O que fica aqui são as curvas e frações de cada fator: mudam a regra, não o
 * peso, e por isso continuam sendo código.
 */
public final class MatchScoringPolicy {

    private MatchScoringPolicy() {
    }

    // ---- Comunicação ----------------------------------------------------
    /** Fração do peso concedida assim que existe pelo menos UM canal em comum. */
    public static final double COMMUNICATION_VIABILITY_BASE = 0.60d;

    // ---- Preferência "tanto faz" (ANY) ----------------------------------
    /** Fração do peso concedida quando o usuário declarou explicitamente indiferença. */
    public static final double NEUTRAL_PREFERENCE_RATIO = 0.65d;

    // ---- Tipo de conexão -------------------------------------------------
    public static final double CONNECTION_TYPE_EXACT_RATIO = 1.00d;
    public static final double CONNECTION_TYPE_PARTIAL_RATIO = 0.50d;

    // ---- Níveis (autonomia / energia) -----------------------------------
    public static final double LEVEL_SIMILAR_ADJACENT_RATIO = 0.60d;
    public static final double LEVEL_SIMILAR_FAR_RATIO = 0.20d;
    public static final double LEVEL_DIFFERENT_ADJACENT_RATIO = 0.60d;

    // ---- Distância (curva contínua) -------------------------------------
    public static final double DISTANCE_FULL_SCORE_KM = 10d;
    public static final double DISTANCE_ZERO_SCORE_KM = 120d;

    // ---- Idade (curva contínua) -----------------------------------------
    public static final int AGE_FULL_SCORE_YEARS = 3;
    public static final int AGE_ZERO_SCORE_YEARS = 20;

    // ---- Confiança -------------------------------------------------------
    /** Abaixo desta cobertura de pesos o score é considerado pouco confiável. */
    public static final double MIN_CONFIDENT_COVERAGE = 0.40d;
    /** Teto imposto a scores de baixa confiança. */
    public static final double LOW_CONFIDENCE_SCORE_CAP = 70d;

    // ---- Faixas do feed orgânico (recalibradas em B10) ------------------
    public static final int MINIMUM_DISCOVERY_SCORE = 35;
    public static final int PREFERRED_DISCOVERY_MAX_SCORE = 75;
}
