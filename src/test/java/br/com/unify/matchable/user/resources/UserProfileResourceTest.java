package br.com.unify.matchable.user.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.user.dto.DisabilityOptionResponse;
import br.com.unify.matchable.user.dto.LocationRequest;
import br.com.unify.matchable.user.dto.LookupOptionResponse;
import br.com.unify.matchable.user.dto.ProfileCompletionResponse;
import br.com.unify.matchable.user.dto.ProfileOptionsResponse;
import br.com.unify.matchable.user.dto.SimilarityOptionResponse;
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
                "Bio em português",
                "https://cdn.exemplo/avatar.png",
                new LookupOptionResponse(1, "Mulher"),
                List.of(new DisabilityOptionResponse(1, "Física", "walk-outline")),
                List.of(),
                null,
                List.of(new LookupOptionResponse(1, "Texto")),
                List.of(new LookupOptionResponse(1, "Caseiro")),
                null,
                List.of(new LookupOptionResponse(1, "Tecnologia")),
                null
        );

        TestableUserProfileResource resource = new TestableUserProfileResource();
        resource.userProfileService = service;
        resource.currentUser = buildUser();

        UserProfileUpsertRequest request = new UserProfileUpsertRequest(
                "Bio em português",
                "https://cdn.exemplo/avatar.png",
                1,
                Set.of(1),
                Set.of(),
                null,
                Set.of(1),
                Set.of(1),
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
                null,
                99,
                Set.of(),
                Set.of(),
                null,
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
                null,
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
                null,
                30,
                Set.of(1)
        );

        Response response = resource.saveMatchPreferences(request);

        assertEquals(200, response.getStatus());
        assertSame(request, service.capturedMatchPreferencesRequest);
        UserMatchPreferencesResponse body = assertInstanceOf(UserMatchPreferencesResponse.class, response.getEntity());
        assertEquals(30, body.maxMatchDistanceKm());
        assertEquals("Amizade", body.connectionType().description());
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

    private User buildUser() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = "pedro@example.com";
        return user;
    }

    private static final class TestableUserProfileResource extends UserProfileResource {
        private User currentUser;

        @Override
        protected User findCurrentUser() {
            return currentUser;
        }
    }

    private static final class StubUserProfileService implements UserProfileService {
        private User capturedUser;
        private UserProfileUpsertRequest capturedProfileRequest;
        private UserMatchPreferencesUpsertRequest capturedMatchPreferencesRequest;
        private UserProfileResponse profileResponse;
        private UserMatchPreferencesResponse matchPreferencesResponse;
        private ProfileCompletionResponse completionResponse;
        private IllegalArgumentException profileException;

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
                    List.of(new DisabilityOptionResponse(1, "Física", "walk-outline")),
                    List.of(new LookupOptionResponse(1, "Leitor de tela")),
                    List.of(new LookupOptionResponse(1, "Independente")),
                    List.of(new LookupOptionResponse(1, "Texto")),
                    List.of(new LookupOptionResponse(1, "Caseiro")),
                    List.of(new LookupOptionResponse(1, "Moderada")),
                    List.of(new LookupOptionResponse(1, "Tecnologia")),
                    List.of(new LookupOptionResponse(1, "Amizade")),
                    List.of(new SimilarityOptionResponse("ANY", "Indiferente"))
            );
        }
    }
}