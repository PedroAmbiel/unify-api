package br.com.unify.matchable.user.dto;

import java.util.UUID;

public record MutualMatchSummaryResponse(
        UUID matchId,
        UUID userId,
        UUID userProfileId,
        String fullName,
        Integer age,
        UserProfileImageResponse profilePicture
) {
}
