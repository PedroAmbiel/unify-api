package br.com.unify.matchable.user.dto;

import java.util.List;

public record ProfileCompletionResponse(
        boolean profileCompleted,
        boolean matchPreferencesCompleted,
        boolean fullyCompleted,
        List<String> missingProfileFields,
        List<String> missingMatchPreferenceFields
) {
}