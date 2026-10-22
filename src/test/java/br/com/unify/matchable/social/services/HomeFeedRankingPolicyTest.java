package br.com.unify.matchable.social.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.scoring.enums.ScoringGroup;

class HomeFeedRankingPolicyTest {

    /** Parâmetros de contexto (quem lê o feed); o resto do SQL são pesos. */
    private static final Set<String> CONTEXT_PARAMETERS = Set.of(
            "currentUserId", "followedUserIds", "memberCommunityIds",
            "interestTypeIds", "communityCategoryIds", "hiddenAuthorUserIds");

    @Test
    @DisplayName("Toda dimensão FEED_* do backoffice entra no SQL, uma vez cada")
    void everyFeedDimensionIsBound() {
        assertEquals(
                new HashSet<>(ScoringGroup.FEED.dimensions()),
                new HashSet<>(HomeFeedRankingPolicy.WEIGHT_PARAMETERS.values()));
        assertEquals(ScoringGroup.FEED.dimensions().size(), HomeFeedRankingPolicy.WEIGHT_PARAMETERS.size());
    }

    @Test
    @DisplayName("Parâmetros de peso do SQL batem com o mapa de binding (nenhum sobra, nenhum falta)")
    void sqlParametersMatchTheBindings() {
        Set<String> parameters = new HashSet<>();
        Matcher matcher = Pattern.compile(":([A-Za-z]+)").matcher(HomeFeedRankingPolicy.SQL);
        while (matcher.find()) {
            parameters.add(matcher.group(1));
        }

        Set<String> weightParameters = parameters.stream()
                .filter(parameter -> !CONTEXT_PARAMETERS.contains(parameter))
                .collect(Collectors.toSet());

        assertEquals(HomeFeedRankingPolicy.WEIGHT_PARAMETERS.keySet(), weightParameters);
    }
}
