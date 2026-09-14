package br.com.unify.matchable.social.dto;

import java.util.UUID;

public record UserPostAuthorResponse(UUID userProfileId, UUID userId, String name, String avatarUrl) {
}
