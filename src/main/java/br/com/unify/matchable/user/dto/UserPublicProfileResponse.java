package br.com.unify.matchable.user.dto;

import java.util.List;
import java.util.UUID;

public record UserPublicProfileResponse(
        UUID userProfileId,
        String name,
        Integer age,
        String bio,
        LookupOptionResponse gender,
        LookupOptionResponse pronouns,
        List<DisabilityOptionResponse> disabilities,
        List<LookupOptionResponse> accessibilityNeeds,
        LookupOptionResponse autonomyLevel,
        List<LookupOptionResponse> communicationForms,
        List<LookupOptionResponse> lifestyleTypes,
        List<LookupOptionResponse> loveLanguages,
        LookupOptionResponse energyLevel,
        List<LookupOptionResponse> interestTypes,
        List<UUID> galleryImageIds
) {
}