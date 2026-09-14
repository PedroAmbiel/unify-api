package br.com.unify.matchable.social.dto;

import java.util.UUID;

public record FollowStatsResponse(
        UUID userProfileId,
        long followersCount,
        long followingCount,
        boolean followedByCurrentUser
) {
}
