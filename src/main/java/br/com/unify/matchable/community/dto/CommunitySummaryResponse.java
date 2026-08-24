package br.com.unify.matchable.community.dto;

import java.util.UUID;

import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.community.enums.CommunityPrivacy;

public record CommunitySummaryResponse(
        UUID id,
        String name,
        Long memberCount,
        String description,
        String iconData,
        Boolean isMember,
        CommunityAuthorResponse owner,
        CommunityMemberRole currentUserRole,
        Boolean isOwner,
        CommunityCategoryResponse category,
        CommunityPrivacy privacy,
        Boolean hasPendingRequest
) {
}
