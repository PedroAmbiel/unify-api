package br.com.unify.matchable.user.dto;

import java.util.List;

public record ProfileOptionsResponse(
        List<LookupOptionResponse> genders,
        List<DisabilityOptionResponse> disabilities,
        List<LookupOptionResponse> accessibilityNeeds,
        List<LookupOptionResponse> autonomyLevels,
        List<LookupOptionResponse> communicationForms,
        List<LookupOptionResponse> lifestyleTypes,
        List<LookupOptionResponse> energyLevels,
        List<LookupOptionResponse> interestTypes,
        List<LookupOptionResponse> connectionTypes,
        List<SimilarityOptionResponse> similarityPreferences
) {
}