package br.com.unify.matchable.user.services;

/**
 * Tabela de pesos e constantes do algoritmo de compatibilidade do Unify.
 *
 * Regra de ouro: a soma de TODOS os pesos deve ser exatamente 100. O score final
 * é normalizado pelos pesos efetivamente avaliáveis (ver
 * UserMatchServiceImplementation.calculateCompatibilityScore), de modo que perfis
 * incompletos não ganhem nem percam pontos "de graça".
 */
public final class MatchScoringPolicy {

    private MatchScoringPolicy() {
    }

    // ---- Pesos (soma = 100) --------------------------------------------
    public static final double WEIGHT_COMMUNICATION = 22d;
    public static final double WEIGHT_INTERESTS = 18d;
    public static final double WEIGHT_CONNECTION_TYPE = 12d;
    public static final double WEIGHT_ACCESSIBILITY_NEEDS = 12d;
    public static final double WEIGHT_LIFESTYLE = 10d;
    public static final double WEIGHT_AUTONOMY = 8d;
    public static final double WEIGHT_LOVE_LANGUAGE = 6d;
    public static final double WEIGHT_ENERGY = 5d;
    public static final double WEIGHT_DISTANCE = 4d;
    public static final double WEIGHT_AGE_DIFFERENCE = 3d;

    public static final double TOTAL_WEIGHT = 100d;

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

    // ---- Penalidades (aplicadas após a normalização) --------------------
    /** A preferência de gênero do candidato exclui o usuário atual. */
    public static final double PENALTY_GENDER_NOT_RECIPROCAL = 8d;
    /** A faixa etária do candidato exclui o usuário atual. */
    public static final double PENALTY_AGE_NOT_RECIPROCAL = 5d;
    /** Perfil que já foi recusado e voltou após o cooldown. */
    public static final double PENALTY_RESHOWN_AFTER_COOLDOWN = 10d;

    // ---- Confiança -------------------------------------------------------
    /** Abaixo desta cobertura de pesos o score é considerado pouco confiável. */
    public static final double MIN_CONFIDENT_COVERAGE = 0.40d;
    /** Teto imposto a scores de baixa confiança. */
    public static final double LOW_CONFIDENCE_SCORE_CAP = 70d;

    // ---- Faixas do feed orgânico (recalibradas em B10) ------------------
    public static final int MINIMUM_DISCOVERY_SCORE = 35;
    public static final int PREFERRED_DISCOVERY_MAX_SCORE = 75;
}
