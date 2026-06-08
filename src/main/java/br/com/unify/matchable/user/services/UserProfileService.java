package br.com.unify.matchable.user.services;

import java.util.UUID;

import br.com.unify.matchable.user.dto.ProfileCompletionResponse;
import br.com.unify.matchable.user.dto.ProfileOptionsResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesUpsertRequest;
import br.com.unify.matchable.user.dto.UserProfileImagesResponse;
import br.com.unify.matchable.user.dto.UserProfileResponse;
import br.com.unify.matchable.user.dto.UserProfileUpsertRequest;
import br.com.unify.matchable.user.dto.UserPublicProfileGalleryImagesResponse;
import br.com.unify.matchable.user.dto.UserPublicProfileResponse;
import br.com.unify.matchable.user.entity.User;

public interface UserProfileService {

    UserProfileResponse getProfile(User user);

    UserProfileResponse saveProfile(User user, UserProfileUpsertRequest request);

    UserMatchPreferencesResponse getMatchPreferences(User user);

    UserMatchPreferencesResponse saveMatchPreferences(User user, UserMatchPreferencesUpsertRequest request);

    ProfileCompletionResponse getCompletionStatus(User user);

    ProfileOptionsResponse getProfileOptions();

    UserPublicProfileResponse getPublicProfile(UUID userProfileId);

    UserPublicProfileGalleryImagesResponse getPublicGalleryImages(UUID userProfileId);

    byte[] getPublicGalleryImageContent(UUID userProfileId, UUID imageId);

    UserProfileImagesResponse getActiveImages(User user);

    UserProfileImagesResponse uploadProfilePicture(User user, byte[] imageBytes);

    UserProfileImagesResponse uploadGalleryImage(User user, byte[] imageBytes);

    UserProfileImagesResponse deactivateImage(User user, UUID imageId);

    byte[] getImageContent(User user, UUID imageId);
}