package br.com.unify.matchable.user.dto;

import java.util.List;

public record UserProfileImagesResponse(
        UserProfileImageResponse profilePicture,
        List<UserProfileImageResponse> galleryImages
) {
}