package br.com.unify.matchable.social.dto;

import java.util.UUID;

public record FollowActionResponse(
        UUID targetUserProfileId,
        boolean following,
        long followersCount,
        long followingCount
) {
}
