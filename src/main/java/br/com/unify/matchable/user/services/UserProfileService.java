package br.com.unify.matchable.user.services;

import java.util.UUID;

import br.com.unify.matchable.common.media.AudioContent;
import br.com.unify.matchable.user.dto.ProfileCompletionResponse;
import br.com.unify.matchable.user.dto.ProfileOptionsResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesUpsertRequest;
import br.com.unify.matchable.user.dto.UserProfileAudioResponse;
import br.com.unify.matchable.user.dto.UserProfileAudioStatusResponse;
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

    /**
     * Perfil de outra pessoa visto por {@code viewer}. 404 (NoSuchElementException)
     * se não existe, não é verificado ou há bloqueio em qualquer direção; idade e
     * distância saem nulas quando o dono as ocultou.
     */
    UserPublicProfileResponse getPublicProfile(User viewer, UUID userProfileId);

    UserPublicProfileGalleryImagesResponse getPublicGalleryImages(User viewer, UUID userProfileId);

    byte[] getPublicGalleryImageContent(User viewer, UUID userProfileId, UUID imageId);

    UserProfileImagesResponse getActiveImages(User user);

    UserProfileImagesResponse uploadProfilePicture(User user, byte[] imageBytes);

    UserProfileImagesResponse uploadGalleryImage(User user, byte[] imageBytes);

    UserProfileImagesResponse deactivateImage(User user, UUID imageId);

    byte[] getImageContent(User user, UUID imageId);

    // ------------------------------------------- áudio de apresentação (frente D)

    UserProfileAudioStatusResponse getPresentationAudio(User user);

    /** Substitui o áudio anterior (linha nova, id novo). Duração obrigatória, até 60 s. */
    UserProfileAudioResponse uploadPresentationAudio(User user, String contentType, byte[] bytes, Integer durationSeconds);

    void deletePresentationAudio(User user);

    AudioContent getPresentationAudioContent(User user, UUID audioId);

    /** Mesma guarda do perfil público (verificado, bloqueio). */
    AudioContent getPublicPresentationAudioContent(User viewer, UUID userProfileId, UUID audioId);
}
