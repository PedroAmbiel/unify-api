package br.com.unify.matchable.user.dto;

import java.util.UUID;

public record UserProfileImageResponse(
        UUID id,
        boolean profilePicture,
        boolean active,
        String url
) {
}