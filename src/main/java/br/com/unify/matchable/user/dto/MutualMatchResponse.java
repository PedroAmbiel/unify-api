package br.com.unify.matchable.user.dto;

import java.util.UUID;

public record MutualMatchResponse(
        UUID userProfileId,
        UserProfileImageResponse profileImage
) {
}