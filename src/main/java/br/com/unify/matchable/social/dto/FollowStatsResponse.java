package br.com.unify.matchable.social.dto;

import java.util.UUID;

public record FollowStatsResponse(
        UUID userProfileId,
        long followersCount,
        long followingCount,
        boolean followedByCurrentUser,
        /** Tenho um pedido pendente para seguir este perfil. */
        boolean followRequestedByCurrentUser,
        /** O dono do perfil aprova seguidores ("Pedir para seguir"). */
        boolean followApprovalRequired,
        /** Só no MEU perfil: pedidos recebidos pendentes. Nulo para perfis de outras pessoas. */
        Long pendingFollowRequestsCount
) {

    public FollowStatsResponse(UUID userProfileId, long followersCount, long followingCount, boolean followedByCurrentUser) {
        this(userProfileId, followersCount, followingCount, followedByCurrentUser, false, false, null);
    }
}
