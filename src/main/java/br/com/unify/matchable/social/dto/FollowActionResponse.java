package br.com.unify.matchable.social.dto;

import java.util.UUID;

public record FollowActionResponse(
        UUID targetUserProfileId,
        boolean following,
        /** Alvo exige aprovação: em vez de seguir, ficou um pedido pendente. */
        boolean followRequested,
        long followersCount,
        long followingCount
) {

    public FollowActionResponse(UUID targetUserProfileId, boolean following, long followersCount, long followingCount) {
        this(targetUserProfileId, following, false, followersCount, followingCount);
    }
}
