package br.com.unify.matchable.community.dto;

import java.util.List;
import java.util.UUID;

public record CommunityMembersResponse(
        UUID communityId,
        List<CommunityMemberHeaderResponse> members
) {
}