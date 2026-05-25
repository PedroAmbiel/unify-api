package br.com.unify.matchable.community.dto;

import java.util.UUID;

import br.com.unify.matchable.community.enums.CommunityMemberRole;

public record CommunitySummaryResponse(
        UUID id,
        String name,
        Long memberCount,
        String description,
        String iconData,
        Boolean isMember,
        CommunityAuthorResponse owner,
        CommunityMemberRole currentUserRole,
        Boolean isOwner
) {
}