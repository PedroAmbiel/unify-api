package br.com.unify.matchable.community.dto;

import java.util.UUID;

/**
 * {@code id} é o {@code User} (denúncia, avatar); {@code userProfileId} é o
 * {@code UserProfile} (perfil público em {@code /users/[userProfileId]}).
 */
public record CommunityAuthorResponse(
        UUID id,
        UUID userProfileId,
        String name,
        String avatarData
) {
}
