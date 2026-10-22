package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.enums.ScoringGroup;
import br.com.unify.matchable.scoring.enums.ScoringUnit;

class MatchScoringPolicyTest {

    @Test
    @DisplayName("As fatias padrão dos Encontros somam exatamente 100")
    void defaultWeightsMustSumExactlyOneHundred() {
        double sum = Arrays.stream(ScoringDimension.values())
                .filter(dimension -> dimension.group() == ScoringGroup.MATCH)
                .filter(dimension -> dimension.unit() == ScoringUnit.PERCENT)
                .mapToDouble(ScoringDimension::defaultWeight)
                .sum();

        assertEquals(100d, sum, 0.0001d);
    }

    @Test
    @DisplayName("Comunicação continua sendo o maior peso padrão")
    void communicationRemainsTheHeaviestFactor() {
        assertTrue(ScoringDimension.MATCH_COMMUNICATION.defaultWeight() > ScoringDimension.MATCH_INTERESTS.defaultWeight());
        assertTrue(ScoringDimension.MATCH_INTERESTS.defaultWeight() > ScoringDimension.MATCH_CONNECTION_TYPE.defaultWeight());
    }

    @Test
    @DisplayName("Acessibilidade + autonomia somam 20 no padrão, como prescrito no MATCH_ALG_GUIDE")
    void accessibilityAxisMatchesGuide() {
        assertEquals(20d,
                ScoringDimension.MATCH_ACCESSIBILITY_NEEDS.defaultWeight() + ScoringDimension.MATCH_AUTONOMY.defaultWeight(),
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
