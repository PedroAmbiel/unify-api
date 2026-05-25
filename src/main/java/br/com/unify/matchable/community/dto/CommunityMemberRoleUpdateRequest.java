package br.com.unify.matchable.community.dto;

import br.com.unify.matchable.community.enums.CommunityMemberRole;

public record CommunityMemberRoleUpdateRequest(
        CommunityMemberRole role
) {
}