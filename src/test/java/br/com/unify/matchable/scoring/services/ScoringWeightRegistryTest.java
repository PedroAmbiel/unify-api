package br.com.unify.matchable.scoring.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.enums.ScoringGroup;

class ScoringWeightRegistryTest {

    @Test
    @DisplayName("Antes do boot a memória tem os padrões do código")
    void startsWithDefaults() {
        assertSame(ScoringWeights.defaults(), new ScoringWeightRegistry().current());
    }

    @Test
    @DisplayName("Save commitado troca a memória; revisão antiga chegando atrasada é ignorada")
    void keepsTheNewestRevision() {
        ScoringWeightRegistry registry = new ScoringWeightRegistry();

        registry.apply(new ScoringWeightsChanged(ScoringGroup.FEED, 2L, Map.of(ScoringDimension.FEED_LIKE, 9d)));
        assertEquals(9d, registry.current().get(ScoringDimension.FEED_LIKE));
        assertEquals(2L, registry.current().revision(ScoringGroup.FEED));

        // AFTER_SUCCESS da revisão 1 chegou depois do da revisão 2.
        registry.apply(new ScoringWeightsChanged(ScoringGroup.FEED, 1L, Map.of(ScoringDimension.FEED_LIKE, 5d)));
        assertEquals(9d, registry.current().get(ScoringDimension.FEED_LIKE));

        // Outro grupo tem revisão própria.
        registry.apply(new ScoringWeightsChanged(ScoringGroup.COMMUNITY_DISCOVERY, 1L,
                Map.of(ScoringDimension.COMMUNITY_DISCOVERY_POST, 4d)));
        assertEquals(4d, registry.current().get(ScoringDimension.COMMUNITY_DISCOVERY_POST));
        assertEquals(9d, registry.current().get(ScoringDimension.FEED_LIKE));
    }
}
