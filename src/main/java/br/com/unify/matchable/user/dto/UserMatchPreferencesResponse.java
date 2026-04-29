package br.com.unify.matchable.user.dto;

import java.util.List;
import java.util.UUID;

public record UserMatchPreferencesResponse(
        UUID id,
        LookupOptionResponse connectionType,
        String accessibilityNeedSimilarity,
        String autonomyCompatibility,
        String lifestyleSimilarity,
        String energyLevelSimilarity,
        Integer maxMatchDistanceKm,
        List<LookupOptionResponse> desiredGenders
) {
}