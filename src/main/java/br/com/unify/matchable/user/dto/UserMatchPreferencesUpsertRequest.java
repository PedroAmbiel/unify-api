package br.com.unify.matchable.user.dto;

import java.util.Set;

import br.com.unify.matchable.user.enums.SimilarityPreference;

public record UserMatchPreferencesUpsertRequest(
        Integer connectionTypeId,
        SimilarityPreference accessibilityNeedSimilarity,
        SimilarityPreference autonomyCompatibility,
        SimilarityPreference lifestyleSimilarity,
        SimilarityPreference energyLevelSimilarity,
        Integer maxMatchDistanceKm,
        Set<Integer> desiredGenderIds
) {
}