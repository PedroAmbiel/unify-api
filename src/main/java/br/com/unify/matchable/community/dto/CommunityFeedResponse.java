package br.com.unify.matchable.community.dto;

import java.util.List;

public record CommunityFeedResponse(
        CommunitySummaryResponse community,
        List<CommunityPostResponse> posts
) {
}