package br.com.unify.matchable.user.dto;

import java.util.Set;

public record UserProfileUpsertRequest(
        String bio,
        Integer genderId,
        Set<Integer> disabilityIds,
        Set<Integer> accessibilityNeedIds,
        Integer autonomyLevelId,
        Set<Integer> communicationFormIds,
        Set<Integer> lifestyleTypeIds,
        Integer energyLevelId,
        Set<Integer> interestTypeIds,
        LocationRequest location
) {
}