package br.com.unify.matchable.user.resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.user.dto.MatchDecisionRequest;
import br.com.unify.matchable.user.dto.MatchDecisionResponse;
import br.com.unify.matchable.user.dto.MutualMatchPageResponse;
import br.com.unify.matchable.user.dto.MutualMatchResponse;
import br.com.unify.matchable.user.dto.MutualMatchSummaryResponse;
import br.com.unify.matchable.user.dto.PotentialMatchesRequest;
import br.com.unify.matchable.user.dto.UserProfileImageResponse;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.UserMatchService;
import jakarta.ws.rs.core.Response;

class UserMatchResourceTest {

    @Test
    void getPotentialMatchesReturnsServicePayloadForAuthenticatedUser() {
        StubUserMatchService service = new StubUserMatchService();
        service.discoveryResponse = List.of(UUID.randomUUID(), UUID.randomUUID());

        TestableUserMatchResource resource = new TestableUserMatchResource();
        resource.userMatchService = service;
        resource.currentUser = buildUser();

        PotentialMatchesRequest request = new PotentialMatchesRequest(List.of(UUID.randomUUID()));
        Response response = resource.getPotentialMatches(request);

        assertEquals(200, response.getStatus());
        assertSame(resource.currentUser, service.capturedUser);
        assertSame(request, service.capturedPotentialMatchesRequest);
        assertEquals(service.discoveryResponse, response.getEntity());
    }

    @Test
    void registerDecisionReturnsConflictWhenMatchAlreadyExists() {
        StubUserMatchService service = new StubUserMatchService();
        service.decisionConflict = new IllegalStateException("Você já iniciou um match com este perfil");

        TestableUserMatchResource resource = new TestableUserMatchResource();
        resource.userMatchService = service;
        resource.currentUser = buildUser();

        Response response = resource.registerDecision(new MatchDecisionRequest(UUID.randomUUID(), true));

        assertEquals(409, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_CONFLICT", body.error());
        assertTrue(body.message().contains("já iniciou um match"));
    }

    @Test
    void registerDecisionReturnsNotFoundWhenTargetProfileIsMissing() {
        StubUserMatchService service = new StubUserMatchService();
        service.decisionNotFound = new NoSuchElementException("Perfil de destino não encontrado");

        TestableUserMatchResource resource = new TestableUserMatchResource();
        resource.userMatchService = service;
        resource.currentUser = buildUser();

        Response response = resource.registerDecision(new MatchDecisionRequest(UUID.randomUUID(), true));

        assertEquals(404, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_NOT_FOUND", body.error());
        assertTrue(body.message().contains("Perfil de destino"));
    }

    @Test
    void getMutualMatchesReturnsMatchedProfilePayload() {
        StubUserMatchService service = new StubUserMatchService();
        UUID profileId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        service.mutualMatchesResponse = List.of(
                new MutualMatchResponse(
                        profileId,
                        new UserProfileImageResponse(imageId, true, true, "/users/me/matches/images/" + imageId)
                )
        );

        TestableUserMatchResource resource = new TestableUserMatchResource();
        resource.userMatchService = service;
        resource.currentUser = buildUser();

        Response response = resource.getMutualMatches();

        assertEquals(200, response.getStatus());
        List<?> body = assertInstanceOf(List.class, response.getEntity());
        assertEquals(1, body.size());
        MutualMatchResponse match = assertInstanceOf(MutualMatchResponse.class, body.getFirst());
        assertEquals(profileId, match.userProfileId());
        assertTrue(match.profileImage().url().contains("/users/me/matches/images/"));
    }

    @Test
    void getMutualMatchesPageReturnsPagedPayload() {
        StubUserMatchService service = new StubUserMatchService();
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        service.mutualMatchesPageResponse = new MutualMatchPageResponse(
                List.of(new MutualMatchSummaryResponse(
                        userId,
                        profileId,
                        "Ana Souza",
                        29,
                        new UserProfileImageResponse(imageId, true, true, "/users/me/matches/images/" + imageId)
                )),
                1,
                10,
                11L,
                2,
                true
        );

        TestableUserMatchResource resource = new TestableUserMatchResource();
        resource.userMatchService = service;
        resource.currentUser = buildUser();

        Response response = resource.getMutualMatchesPage(1, 10);

        assertEquals(200, response.getStatus());
        MutualMatchPageResponse body = assertInstanceOf(MutualMatchPageResponse.class, response.getEntity());
        assertEquals(1, body.page());
        assertEquals(10, body.size());
        assertEquals(11L, body.totalElements());
        assertTrue(body.hasNext());
        assertEquals(userId, body.matches().getFirst().userId());
        assertEquals(profileId, body.matches().getFirst().userProfileId());
        assertEquals(1, service.capturedPage);
        assertEquals(10, service.capturedSize);
    }

    @Test
    void getMutualMatchesPageReturnsValidationErrorWhenPaginationIsInvalid() {
        StubUserMatchService service = new StubUserMatchService();
        service.pagedMutualMatchesValidation = new IllegalArgumentException("O parâmetro 'page' deve ser maior ou igual a zero");

        TestableUserMatchResource resource = new TestableUserMatchResource();
        resource.userMatchService = service;
        resource.currentUser = buildUser();

        Response response = resource.getMutualMatchesPage(-1, 10);

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_INVALID_FORMAT", body.error());
        assertTrue(body.message().contains("parâmetro 'page'"));
    }

    @Test
    void getMatchedProfileImageReturnsStoredBytes() {
        StubUserMatchService service = new StubUserMatchService();
        service.imageBytes = new byte[] { 8, 7, 6 };

        TestableUserMatchResource resource = new TestableUserMatchResource();
        resource.userMatchService = service;
        resource.currentUser = buildUser();
        UUID imageId = UUID.randomUUID();

        Response response = resource.getMatchedProfileImage(imageId);

        assertEquals(200, response.getStatus());
        assertEquals("image/jpeg", response.getMediaType().toString());
        assertArrayEquals(new byte[] { 8, 7, 6 }, assertInstanceOf(byte[].class, response.getEntity()));
        assertEquals(imageId, service.capturedImageId);
    }

    private User buildUser() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = "pedro@example.com";
        return user;
    }

    private static final class TestableUserMatchResource extends UserMatchResource {
        private User currentUser;

        @Override
        protected User findCurrentUser() {
            return currentUser;
        }
    }

    private static final class StubUserMatchService implements UserMatchService {
        private User capturedUser;
        private PotentialMatchesRequest capturedPotentialMatchesRequest;
        private UUID capturedImageId;
        private Integer capturedPage;
        private Integer capturedSize;
        private List<UUID> discoveryResponse = List.of();
        private MatchDecisionResponse decisionResponse = new MatchDecisionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                true,
                null,
                false
        );
        private List<MutualMatchResponse> mutualMatchesResponse = List.of();
            private MutualMatchPageResponse mutualMatchesPageResponse = new MutualMatchPageResponse(List.of(), 0, 20, 0L, 0, false);
        private byte[] imageBytes = new byte[] { 1 };
        private IllegalStateException decisionConflict;
        private NoSuchElementException decisionNotFound;
            private IllegalArgumentException pagedMutualMatchesValidation;

        @Override
        public List<UUID> getPotentialMatches(User user, PotentialMatchesRequest request) {
            this.capturedUser = user;
            this.capturedPotentialMatchesRequest = request;
            return discoveryResponse;
        }

        @Override
        public MatchDecisionResponse registerDecision(User user, MatchDecisionRequest request) {
            this.capturedUser = user;
            if (decisionNotFound != null) {
                throw decisionNotFound;
            }
            if (decisionConflict != null) {
                throw decisionConflict;
            }
            return decisionResponse;
        }

        @Override
        public List<MutualMatchResponse> getMutualMatches(User user) {
            this.capturedUser = user;
            return mutualMatchesResponse;
        }

        @Override
        public MutualMatchPageResponse getMutualMatchesPage(User user, Integer page, Integer size) {
            this.capturedUser = user;
            this.capturedPage = page;
            this.capturedSize = size;
            if (pagedMutualMatchesValidation != null) {
                throw pagedMutualMatchesValidation;
            }
            return mutualMatchesPageResponse;
        }

        @Override
        public byte[] getMatchedProfileImage(User user, UUID imageId) {
            this.capturedUser = user;
            this.capturedImageId = imageId;
            return imageBytes;
        }
    }
}