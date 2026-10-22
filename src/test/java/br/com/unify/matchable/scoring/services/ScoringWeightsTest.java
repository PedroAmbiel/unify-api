package br.com.unify.matchable.scoring.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.enums.ScoringGroup;

class ScoringWeightsTest {

    @Test
    @DisplayName("Padrões: valores do enum, revisão 0, fatias dos Encontros somando 100")
    void defaultsComeFromTheEnum() {
        ScoringWeights defaults = ScoringWeights.defaults();

        for (ScoringDimension dimension : ScoringDimension.values()) {
            assertEquals(dimension.defaultWeight(), defaults.get(dimension), dimension.name());
        }
        for (ScoringGroup group : ScoringGroup.values()) {
            assertEquals(0L, defaults.revision(group));
        }
        assertEquals(100d, defaults.percentTotal(ScoringGroup.MATCH), 0.0001d);
        assertEquals(0d, defaults.percentTotal(ScoringGroup.FEED), 0.0001d);
        assertEquals(0L, defaults.customizedCount());
    }

    @Test
    @DisplayName("Lido do banco: dimensão sem linha fica com o padrão")
    void missingRowsKeepTheDefault() {
        ScoringWeights loaded = ScoringWeights.of(
                Map.of(ScoringDimension.FEED_LIKE, 7d),
                Map.of(ScoringGroup.FEED, 3L));

        assertEquals(7d, loaded.get(ScoringDimension.FEED_LIKE));
        assertEquals(ScoringDimension.FEED_COMMENT.defaultWeight(), loaded.get(ScoringDimension.FEED_COMMENT));
        assertEquals(3L, loaded.revision(ScoringGroup.FEED));
        assertEquals(0L, loaded.revision(ScoringGroup.MATCH));
        assertEquals(1L, loaded.customizedCount());
    }

    @Test
    @DisplayName("Trocar um grupo gera foto nova e não mexe na antiga nem nos outros grupos")
    void withGroupIsCopyOnWrite() {
        ScoringWeights before = ScoringWeights.defaults();

        ScoringWeights after = before.withGroup(ScoringGroup.MATCH, 4L, Map.of(
                ScoringDimension.MATCH_COMMUNICATION, 30d,
                ScoringDimension.MATCH_INTERESTS, 10d));

        assertEquals(30d, after.get(ScoringDimension.MATCH_COMMUNICATION));
        assertEquals(10d, after.get(ScoringDimension.MATCH_INTERESTS));
        assertEquals(100d, after.percentTotal(ScoringGroup.MATCH), 0.0001d);
        assertEquals(4L, after.revision(ScoringGroup.MATCH));
        assertEquals(0L, after.revision(ScoringGroup.FEED));
        assertEquals(ScoringDimension.FEED_FOLLOWING.defaultWeight(), after.get(ScoringDimension.FEED_FOLLOWING));

        assertEquals(22d, before.get(ScoringDimension.MATCH_COMMUNICATION));
        assertSame(before, ScoringWeights.defaults());
    }

    @Test
    @DisplayName("Dimensão de outro grupo não entra pela troca de um grupo")
    void withGroupRejectsForeignDimension() {
        assertThrows(IllegalArgumentException.class, () -> ScoringWeights.defaults()
                .withGroup(ScoringGroup.MATCH, 1L, Map.of(ScoringDimension.FEED_LIKE, 1d)));
    }
}
