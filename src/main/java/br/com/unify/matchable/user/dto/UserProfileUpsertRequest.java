package br.com.unify.matchable.user.dto;

import java.util.Set;

public record UserProfileUpsertRequest(
        String bio,
        Integer genderId,
        Integer pronounsId,
        Set<Integer> disabilityIds,
        Set<Integer> accessibilityNeedIds,
        Integer autonomyLevelId,
        Set<Integer> communicationFormIds,
        Set<Integer> lifestyleTypeIds,
        Set<Integer> loveLanguageIds,
        Integer energyLevelId,
        Set<Integer> interestTypeIds,
        LocationRequest location
) {
}