package br.com.unify.matchable.community.dto;

import java.util.UUID;

import br.com.unify.matchable.community.enums.CommunityMemberRole;

public record CommunityMemberResponse(
        UUID communityId,
        CommunityMemberHeaderResponse user,
        CommunityMemberRole role,
        Boolean owner
) {
}