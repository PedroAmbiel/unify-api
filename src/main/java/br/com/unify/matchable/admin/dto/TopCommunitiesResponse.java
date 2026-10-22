package br.com.unify.matchable.admin.dto;

import java.util.List;
import java.util.UUID;

/** Ranking curto (sem paginação) das comunidades ativas. */
public record TopCommunitiesResponse(
        List<TopCommunityByMembers> byMembers,
        List<TopCommunityByPosts> byPosts
) {

    public record TopCommunityByMembers(UUID communityId, String name, long memberCount) {
    }

    public record TopCommunityByPosts(UUID communityId, String name, long postCount) {
    }
}
