package br.com.unify.matchable.admin.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.enums.ScoringGroup;

class AdminScoringWeightServiceImplementationTest {

    @Test
    @DisplayName("Até duas casas decimais; zeros à direita não contam como casa")
    void decimalPlaces() {
        ScoringDimension like = ScoringDimension.FEED_LIKE;

        assertEquals(new BigDecimal("2.50"), AdminScoringWeightServiceImplementation.validateWeight(like, new BigDecimal("2.5")));
        assertEquals(new BigDecimal("2.50"), AdminScoringWeightServiceImplementation.validateWeight(like, new BigDecimal("2.5000")));
        assertEquals(new BigDecimal("1000.00"), AdminScoringWeightServiceImplementation.validateWeight(like, new BigDecimal("1E+3")));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AdminScoringWeightServiceImplementation.validateWeight(like, new BigDecimal("2.555")));
        assertTrue(error.getMessage().contains("'Curtida'"), error.getMessage());
    }

    @Test
    @DisplayName("Limites por dimensão, com a unidade na mensagem")
    void limits() {
        IllegalArgumentException percent = assertThrows(IllegalArgumentException.class,
                () -> AdminScoringWeightServiceImplementation.validateWeight(
                        ScoringDimension.MATCH_DISTANCE, new BigDecimal("100.01")));
        assertTrue(percent.getMessage().endsWith("entre 0 e 100%"), percent.getMessage());

        IllegalArgumentException hours = assertThrows(IllegalArgumentException.class,
                () -> AdminScoringWeightServiceImplementation.validateWeight(
                        ScoringDimension.FEED_RECENCY_HALF_LIFE_HOURS, new BigDecimal("0.5")));
        assertTrue(hours.getMessage().endsWith("entre 1 e 720 horas"), hours.getMessage());

        assertDoesNotThrow(() -> AdminScoringWeightServiceImplementation.validateWeight(
                ScoringDimension.MATCH_DISTANCE, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Soma das fatias: exata em 100, sem tolerância de ponto flutuante")
    void percentTotal() {
        Map<ScoringDimension, BigDecimal> weights = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dimension : ScoringGroup.MATCH.dimensions()) {
            weights.put(dimension, dimension.defaultWeightDecimal());
        }
        assertDoesNotThrow(() -> AdminScoringWeightServiceImplementation.validatePercentTotal(ScoringGroup.MATCH, weights));

        // 33,33 + 33,33 + 33,34 = 100 exatos em BigDecimal (em double seria 99,99999...).
        for (ScoringDimension dimension : ScoringGroup.MATCH.dimensions()) {
            if (dimension.section().equals("Compatibilidade")) {
                weights.put(dimension, BigDecimal.ZERO);
            }
        }
        weights.put(ScoringDimension.MATCH_COMMUNICATION, new BigDecimal("33.33"));
        weights.put(ScoringDimension.MATCH_INTERESTS, new BigDecimal("33.33"));
        weights.put(ScoringDimension.MATCH_CONNECTION_TYPE, new BigDecimal("33.34"));
        assertDoesNotThrow(() -> AdminScoringWeightServiceImplementation.validatePercentTotal(ScoringGroup.MATCH, weights));

        weights.put(ScoringDimension.MATCH_CONNECTION_TYPE, new BigDecimal("33.33"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AdminScoringWeightServiceImplementation.validatePercentTotal(ScoringGroup.MATCH, weights));
        assertTrue(error.getMessage().contains("a soma dá 99,99%"), error.getMessage());

        // Grupo sem fatias não tem regra de soma.
        assertDoesNotThrow(() -> AdminScoringWeightServiceImplementation.validatePercentTotal(ScoringGroup.FEED, Map.of()));
    }

    @Test
    @DisplayName("Números em pt-BR nas mensagens e na auditoria")
    void formatsWithComma() {
        assertEquals("12,5", AdminScoringWeightServiceImplementation.format(new BigDecimal("12.50")));
        assertEquals("100", AdminScoringWeightServiceImplementation.format(new BigDecimal("100.00")));
        assertEquals("0", AdminScoringWeightServiceImplementation.format(new BigDecimal("0.00")));
    }
}
