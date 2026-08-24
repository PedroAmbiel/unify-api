package br.com.unify.matchable.community.dto;

import java.util.UUID;

/** Item do feed "Para você": publicação acompanhada da comunidade de origem. */
public record CommunityForYouPostResponse(
        UUID communityId,
        String communityName,
        String communityIconData,
        CommunityPostResponse post
) {
}
