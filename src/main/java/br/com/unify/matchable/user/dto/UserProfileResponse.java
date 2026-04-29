package br.com.unify.matchable.user.dto;

import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String bio,
        String avatarUrl,
        LookupOptionResponse gender,
        List<DisabilityOptionResponse> disabilities,
        List<LookupOptionResponse> accessibilityNeeds,
        LookupOptionResponse autonomyLevel,
        List<LookupOptionResponse> communicationForms,
        List<LookupOptionResponse> lifestyleTypes,
        LookupOptionResponse energyLevel,
        List<LookupOptionResponse> interestTypes,
        LocationResponse activeLocation
) {
}