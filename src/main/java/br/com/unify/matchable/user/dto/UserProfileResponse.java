package br.com.unify.matchable.user.dto;

import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String name,
        String lastName,
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
        LocationResponse activeLocation,
        UserProfileImageResponse profilePicture,
        List<UserProfileImageResponse> galleryImages
) {
}