package br.com.unify.matchable.community.dto;

import java.util.UUID;

public record CommunityAuthorResponse(
        UUID id,
        String name,
        String avatarData
) {
}