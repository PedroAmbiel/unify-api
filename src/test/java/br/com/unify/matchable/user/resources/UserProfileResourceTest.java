package br.com.unify.matchable.user.resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.user.dto.DisabilityOptionResponse;
import br.com.unify.matchable.user.dto.LocationRequest;
import br.com.unify.matchable.user.dto.LookupOptionResponse;
import br.com.unify.matchable.user.dto.ProfileCompletionResponse;
import br.com.unify.matchable.user.dto.ProfileOptionsResponse;
import br.com.unify.matchable.user.dto.SimilarityOptionResponse;
import br.com.unify.matchable.user.dto.UserProfileImageResponse;
import br.com.unify.matchable.user.dto.UserProfileImagesResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesUpsertRequest;
import br.com.unify.matchable.user.dto.UserProfileResponse;
import br.com.unify.matchable.user.dto.UserProfileUpsertRequest;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.enums.SimilarityPreference;
import br.com.unify.matchable.user.services.UserProfileService;
import jakarta.ws.rs.core.Response;

class UserProfileResourceTest {

    @Test
    void getCompletionStatusReturnsServicePayloadForAuthenticatedUser() {
        StubUserProfileService service = new StubUserProfileService();
        service.completionResponse = new ProfileCompletionResponse(
                true,
                false,
                false,
                List.of(),
                List.of("gênero desejado")
        );

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();

        Response response = resource.getCompletionStatus();

        assertEquals(200, response.getStatus());
        assertSame(resource.currentUser, service.capturedUser);
        ProfileCompletionResponse body = assertInstanceOf(ProfileCompletionResponse.class, response.getEntity());
        assertTrue(body.profileCompleted());
        assertEquals(List.of("gênero desejado"), body.missingMatchPreferenceFields());
    }

    @Test
    void saveProfileReturnsSavedPayload() {
        StubUserProfileService service = new StubUserProfileService();
        service.profileResponse = new UserProfileResponse(
                UUID.randomUUID(),
            "Pedro",
            "Ambiel",
            31,
                "Bio em português",
                new LookupOptionResponse(1, "Mulher"),
                new LookupOptionResponse(2, "Ele/Dele"),
                List.of(new DisabilityOptionResponse(1, "Física", "walk-outline")),
                List.of(),
                null,
                List.of(new LookupOptionResponse(1, "Texto")),
                List.of(new LookupOptionResponse(1, "Caseiro")),
                List.of(new LookupOptionResponse(2, "Tempo de qualidade")),
                null,
                List.of(new LookupOptionResponse(1, "Tecnologia")),
                null,
                null,
                List.of()
        );

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();

        UserProfileUpsertRequest request = new UserProfileUpsertRequest(
                "Bio em português",
                1,
            2,
                Set.of(1),
                Set.of(),
                null,
                Set.of(1),
                Set.of(1),
            Set.of(2),
                null,
                Set.of(1),
                new LocationRequest(null, null)
        );

        Response response = resource.saveProfile(request);

        assertEquals(200, response.getStatus());
        assertSame(resource.currentUser, service.capturedUser);
        assertSame(request, service.capturedProfileRequest);
        UserProfileResponse body = assertInstanceOf(UserProfileResponse.class, response.getEntity());
        assertEquals("Bio em português", body.bio());
        assertEquals("Mulher", body.gender().description());
        assertEquals("Ele/Dele", body.pronouns().description());
        assertEquals(List.of("Tempo de qualidade"), body.loveLanguages().stream().map(LookupOptionResponse::description).toList());
    }

    @Test
    void saveProfileReturnsValidationErrorWhenServiceRejectsPayload() {
        StubUserProfileService service = new StubUserProfileService();
        service.profileException = new IllegalArgumentException("Valor inválido para gênero");

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();

        Response response = resource.saveProfile(new UserProfileUpsertRequest(
                null,
                99,
            null,
                Set.of(),
                Set.of(),
                null,
                Set.of(),
                Set.of(),
            Set.of(),
                null,
                Set.of(),
                null
        ));

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_INVALID_FORMAT", body.error());
        assertTrue(body.message().contains("Valor inválido para gênero"));
    }

    @Test
    void saveMatchPreferencesReturnsSavedPayload() {
        StubUserProfileService service = new StubUserProfileService();
        service.matchPreferencesResponse = new UserMatchPreferencesResponse(
                UUID.randomUUID(),
                new LookupOptionResponse(1, "Amizade"),
                "ANY",
                null,
                "SIMILAR",
            "DIFFERENT",
                null,
                25,
                35,
                30,
                List.of(new LookupOptionResponse(1, "Mulher"))
        );

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();

        UserMatchPreferencesUpsertRequest request = new UserMatchPreferencesUpsertRequest(
                1,
                SimilarityPreference.ANY,
                null,
                SimilarityPreference.SIMILAR,
            SimilarityPreference.DIFFERENT,
                null,
                25,
                35,
                30,
                Set.of(1)
        );

        Response response = resource.saveMatchPreferences(request);

        assertEquals(200, response.getStatus());
        assertSame(request, service.capturedMatchPreferencesRequest);
        UserMatchPreferencesResponse body = assertInstanceOf(UserMatchPreferencesResponse.class, response.getEntity());
        assertEquals(25, body.minAge());
        assertEquals(35, body.maxAge());
        assertEquals(30, body.maxMatchDistanceKm());
        assertEquals("Amizade", body.connectionType().description());
        assertEquals("DIFFERENT", body.loveLanguageSimilarity());
    }

    @Test
    void saveMatchPreferencesReturnsValidationErrorWhenServiceRejectsAgeRange() {
        StubUserProfileService service = new StubUserProfileService();
        service.matchPreferencesException = new IllegalArgumentException(
                "A idade mínima desejada não pode ser maior que a idade máxima desejada"
        );

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();

        Response response = resource.saveMatchPreferences(new UserMatchPreferencesUpsertRequest(
                1,
                SimilarityPreference.ANY,
                null,
                SimilarityPreference.SIMILAR,
            null,
                null,
                40,
                30,
                20,
                Set.of(1)
        ));

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_INVALID_FORMAT", body.error());
        assertTrue(body.message().contains("idade mínima desejada"));
    }

    @Test
    void getProfileOptionsReturnsUserNotFoundWhenThereIsNoAuthenticatedUser() {
        StubUserProfileService service = new StubUserProfileService();

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;

        Response response = resource.getProfileOptions();

        assertEquals(404, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("USER_NOT_FOUND", body.error());
        assertNull(service.capturedUser);
    }

    @Test
    void getProfileOptionsReturnsPronounsAndLoveLanguages() {
        StubUserProfileService service = new StubUserProfileService();

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();

        Response response = resource.getProfileOptions();

        assertEquals(200, response.getStatus());
        ProfileOptionsResponse body = assertInstanceOf(ProfileOptionsResponse.class, response.getEntity());
        assertEquals(List.of("Ele/Dele"), body.pronouns().stream().map(LookupOptionResponse::description).toList());
        assertEquals(List.of("Tempo de qualidade"), body.loveLanguages().stream().map(LookupOptionResponse::description).toList());
    }

    @Test
    void uploadProfilePictureReturnsSavedImagesPayload() {
        StubUserProfileService service = new StubUserProfileService();
        UUID imageId = UUID.randomUUID();
        service.imagesResponse = new UserProfileImagesResponse(
                new UserProfileImageResponse(imageId, true, true, "/users/me/profile/images/" + imageId),
                List.of()
        );

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();
        resource.nextUploadedBytes = new byte[] { 1, 2, 3 };

        Response response = resource.uploadProfilePicture(null);

        assertEquals(200, response.getStatus());
        assertArrayEquals(new byte[] { 1, 2, 3 }, service.capturedImageBytes);
        UserProfileImagesResponse body = assertInstanceOf(UserProfileImagesResponse.class, response.getEntity());
        assertEquals(imageId, body.profilePicture().id());
    }

    @Test
    void uploadGalleryImageReturnsConflictWhenServiceRejectsLimit() {
        StubUserProfileService service = new StubUserProfileService();
        service.imageConflict = new IllegalStateException("Limite de 5 imagens ativas no carrossel atingido");

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();
        resource.nextUploadedBytes = new byte[] { 9, 9, 9 };

        Response response = resource.uploadGalleryImage(null);

        assertEquals(409, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_CONFLICT", body.error());
        assertTrue(body.message().contains("Limite de 5 imagens"));
    }

    @Test
    void deactivateImageReturnsNotFoundWhenImageDoesNotBelongToUser() {
        StubUserProfileService service = new StubUserProfileService();
        service.imageNotFound = new NoSuchElementException("Imagem não encontrada");

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();

        Response response = resource.deactivateImage(UUID.randomUUID());

        assertEquals(404, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_NOT_FOUND", body.error());
    }

    @Test
    void getProfileImageReturnsStoredBytes() {
        StubUserProfileService service = new StubUserProfileService();
        service.imageContent = new byte[] { 4, 5, 6 };

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();
        UUID imageId = UUID.randomUUID();

        Response response = resource.getProfileImage(imageId);

        assertEquals(200, response.getStatus());
        assertEquals("image/jpeg", response.getMediaType().toString());
        assertArrayEquals(new byte[] { 4, 5, 6 }, assertInstanceOf(byte[].class, response.getEntity()));
        assertEquals(imageId, service.capturedImageId);
    }

    private User buildUser() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = "pedro@example.com";
        return user;
    }

    private static final class TestableUserProfileResource extends UserProfileResource {
        private User currentUser;
        private byte[] nextUploadedBytes;

        @Override
        protected User findCurrentUser() {
            return currentUser;
        }

        @Override
        protected byte[] readUploadedBytes(FileUpload image) {
            return nextUploadedBytes;
        }
    }

    private static final class StubUserProfileService implements UserProfileService {
        private User capturedUser;
        private UserProfileUpsertRequest capturedProfileRequest;
        private UserMatchPreferencesUpsertRequest capturedMatchPreferencesRequest;
        private byte[] capturedImageBytes;
        private UUID capturedImageId;
        private UserProfileResponse profileResponse;
        private UserMatchPreferencesResponse matchPreferencesResponse;
        private ProfileCompletionResponse completionResponse;
        private UserProfileImagesResponse imagesResponse;
        private byte[] imageContent;
        private IllegalArgumentException profileException;
        private IllegalArgumentException matchPreferencesException;
        private IllegalStateException imageConflict;
        private NoSuchElementException imageNotFound;

        @Override
        public UserProfileResponse getProfile(User user) {
            this.capturedUser = user;
            return profileResponse;
        }

        @Override
        public UserProfileResponse saveProfile(User user, UserProfileUpsertRequest request) {
            this.capturedUser = user;
            this.capturedProfileRequest = request;
            if (profileException != null) {
                throw profileException;
            }
            return profileResponse;
        }

        @Override
        public UserMatchPreferencesResponse getMatchPreferences(User user) {
            this.capturedUser = user;
            return matchPreferencesResponse;
        }

        @Override
        public UserMatchPreferencesResponse saveMatchPreferences(User user, UserMatchPreferencesUpsertRequest request) {
            this.capturedUser = user;
            this.capturedMatchPreferencesRequest = request;
            if (matchPreferencesException != null) {
                throw matchPreferencesException;
            }
            return matchPreferencesResponse;
        }

        @Override
        public ProfileCompletionResponse getCompletionStatus(User user) {
            this.capturedUser = user;
            return completionResponse;
        }

        @Override
        public ProfileOptionsResponse getProfileOptions() {
            return new ProfileOptionsResponse(
                    List.of(new LookupOptionResponse(1, "Mulher")),
                List.of(new LookupOptionResponse(2, "Ele/Dele")),
                    List.of(new DisabilityOptionResponse(1, "Física", "walk-outline")),
                    List.of(new LookupOptionResponse(1, "Leitor de tela")),
                    List.of(new LookupOptionResponse(1, "Independente")),
                    List.of(new LookupOptionResponse(1, "Texto")),
                    List.of(new LookupOptionResponse(1, "Caseiro")),
                List.of(new LookupOptionResponse(2, "Tempo de qualidade")),
                    List.of(new LookupOptionResponse(1, "Moderada")),
                    List.of(new LookupOptionResponse(1, "Tecnologia")),
                    List.of(new LookupOptionResponse(1, "Amizade")),
                    List.of(new SimilarityOptionResponse("ANY", "Indiferente"))
            );
        }

        @Override
        public UserProfileImagesResponse getActiveImages(User user) {
            this.capturedUser = user;
            return imagesResponse;
        }

        @Override
        public UserProfileImagesResponse uploadProfilePicture(User user, byte[] imageBytes) {
            this.capturedUser = user;
            this.capturedImageBytes = imageBytes;
            if (imageConflict != null) {
                throw imageConflict;
            }
            return imagesResponse;
        }

        @Override
        public UserProfileImagesResponse uploadGalleryImage(User user, byte[] imageBytes) {
            this.capturedUser = user;
            this.capturedImageBytes = imageBytes;
            if (imageConflict != null) {
                throw imageConflict;
            }
            return imagesResponse;
        }

        @Override
        public UserProfileImagesResponse deactivateImage(User user, UUID imageId) {
            this.capturedUser = user;
            this.capturedImageId = imageId;
            if (imageNotFound != null) {
                throw imageNotFound;
            }
            return imagesResponse;
        }

        @Override
        public byte[] getImageContent(User user, UUID imageId) {
            this.capturedUser = user;
            this.capturedImageId = imageId;
            if (imageNotFound != null) {
                throw imageNotFound;
            }
            return imageContent;
        }
    }
}