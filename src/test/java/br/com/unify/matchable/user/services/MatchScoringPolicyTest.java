package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchScoringPolicyTest {

    @Test
    @DisplayName("A soma dos pesos deve ser exatamente 100")
    void weightsMustSumExactlyOneHundred() {
        double sum = MatchScoringPolicy.WEIGHT_COMMUNICATION
                + MatchScoringPolicy.WEIGHT_INTERESTS
                + MatchScoringPolicy.WEIGHT_CONNECTION_TYPE
                + MatchScoringPolicy.WEIGHT_ACCESSIBILITY_NEEDS
                + MatchScoringPolicy.WEIGHT_LIFESTYLE
                + MatchScoringPolicy.WEIGHT_AUTONOMY
                + MatchScoringPolicy.WEIGHT_LOVE_LANGUAGE
                + MatchScoringPolicy.WEIGHT_ENERGY
                + MatchScoringPolicy.WEIGHT_DISTANCE
                + MatchScoringPolicy.WEIGHT_AGE_DIFFERENCE;

        assertEquals(MatchScoringPolicy.TOTAL_WEIGHT, sum, 0.0001d);
    }

    @Test
    @DisplayName("Comunicação continua sendo o maior peso individual")
    void communicationRemainsTheHeaviestFactor() {
        assertTrue(MatchScoringPolicy.WEIGHT_COMMUNICATION > MatchScoringPolicy.WEIGHT_INTERESTS);
        assertTrue(MatchScoringPolicy.WEIGHT_INTERESTS > MatchScoringPolicy.WEIGHT_CONNECTION_TYPE);
    }

    @Test
    @DisplayName("Acessibilidade + autonomia somam 20, como prescrito no MATCH_ALG_GUIDE")
    void accessibilityAxisMatchesGuide() {
        assertEquals(20d,
                MatchScoringPolicy.WEIGHT_ACCESSIBILITY_NEEDS + MatchScoringPolicy.WEIGHT_AUTONOMY,
                0.0001d);
    }

    @Test
    @DisplayName("As faixas do feed orgânico são coerentes entre si e com a escala 0..100")
    void discoveryBandsAreConsistent() {
        assertTrue(MatchScoringPolicy.MINIMUM_DISCOVERY_SCORE > 0);
        assertTrue(MatchScoringPolicy.MINIMUM_DISCOVERY_SCORE < MatchScoringPolicy.PREFERRED_DISCOVERY_MAX_SCORE);
        assertTrue(MatchScoringPolicy.PREFERRED_DISCOVERY_MAX_SCORE <= 100);
    }

    @Test
    @DisplayName("O teto de baixa confiança fica abaixo do máximo da escala")
    void lowConfidenceCapIsBelowMaximumScore() {
        assertTrue(MatchScoringPolicy.LOW_CONFIDENCE_SCORE_CAP < 100d);
        assertTrue(MatchScoringPolicy.MIN_CONFIDENT_COVERAGE > 0d);
        assertTrue(MatchScoringPolicy.MIN_CONFIDENT_COVERAGE < 1d);
    }
}
