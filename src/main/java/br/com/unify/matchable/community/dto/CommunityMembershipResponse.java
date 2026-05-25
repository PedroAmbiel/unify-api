package br.com.unify.matchable.community.dto;

import java.util.UUID;

import br.com.unify.matchable.community.enums.CommunityMemberRole;

public record CommunityMembershipResponse(
        UUID communityId,
        Boolean isMember,
        Long memberCount,
        CommunityMemberRole role,
        Boolean isOwner
) {
}