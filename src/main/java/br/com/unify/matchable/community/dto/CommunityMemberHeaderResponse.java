package br.com.unify.matchable.community.dto;

import java.util.UUID;

import br.com.unify.matchable.community.enums.CommunityMemberRole;

public record CommunityMemberHeaderResponse(
        UUID userProfileId,
        String name,
        String avatarData,
        CommunityMemberRole role
) {
}