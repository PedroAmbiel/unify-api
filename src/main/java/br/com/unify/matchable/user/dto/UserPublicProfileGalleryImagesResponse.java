package br.com.unify.matchable.user.dto;

import java.util.List;
import java.util.UUID;

public record UserPublicProfileGalleryImagesResponse(
        UUID userProfileId,
        List<UUID> galleryImageIds
) {
}