package br.com.unify.matchable.social.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.exceptions.ForbiddenException;
import br.com.unify.matchable.social.dto.FollowActionResponse;
import br.com.unify.matchable.social.dto.FollowPageResponse;
import br.com.unify.matchable.social.dto.FollowStatsResponse;
import br.com.unify.matchable.social.dto.UserFeedPageResponse;
import br.com.unify.matchable.social.dto.UserPostAuthorResponse;
import br.com.unify.matchable.social.dto.UserPostResponse;
import br.com.unify.matchable.social.services.SocialService;
import br.com.unify.matchable.user.entity.User;
import jakarta.ws.rs.core.Response;

/** Mesmo estilo de CommunityResourceTest: chamada direta ao método + stub do serviço. */
class SocialResourceTest {

    @Test
    void followReturnsOkWithActionResponse() {
        StubSocialService service = new StubSocialService();
        UUID target = UUID.randomUUID();
        service.followResponse = new FollowActionResponse(target, true, 3, 1);
        TestableSocialResource resource = buildResource(service);

        Response response = resource.follow(target);

        assertEquals(200, response.getStatus());
        FollowActionResponse body = assertInstanceOf(FollowActionResponse.class, response.getEntity());
        assertEquals(target, body.targetUserProfileId());
        assertEquals(3, body.followersCount());
    }

    @Test
    void followSelfMapsToBadRequest() {
        StubSocialService service = new StubSocialService();
        service.nextException = new IllegalArgumentException("Não é possível seguir o próprio perfil");
        TestableSocialResource resource = buildResource(service);

        Response response = resource.follow(UUID.randomUUID());

        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_INVALID_FORMAT", assertInstanceOf(ErrorResponse.class, response.getEntity()).error());
    }

    @Test
    void followUnknownProfileMapsToNotFound() {
        StubSocialService service = new StubSocialService();
        service.nextException = new NoSuchElementException("Perfil não encontrado");
        TestableSocialResource resource = buildResource(service);

        assertEquals(404, resource.follow(UUID.randomUUID()).getStatus());
        assertEquals(404, resource.unfollow(UUID.randomUUID()).getStatus());
        assertEquals(404, resource.getFollowStats(UUID.randomUUID()).getStatus());
    }

    @Test
    void missingProfileMapsToConflict() {
        StubSocialService service = new StubSocialService();
        service.nextException = new IllegalStateException("Você precisa criar seu perfil");
        TestableSocialResource resource = buildResource(service);

        assertEquals(409, resource.getFeed(0, 10).getStatus());
        assertEquals(409, resource.listFollowing(0, 10).getStatus());
        assertEquals(409, resource.listFollowers(0, 10).getStatus());
    }

    @Test
    void createPostReturnsCreatedAndForwardsBodyAndImage() {
        StubSocialService service = new StubSocialService();
        service.postResponse = buildPost(null);
        TestableSocialResource resource = buildResource(service);
        resource.nextUploadedBytes = new byte[] {1, 2, 3};

        Response response = resource.createPost("Olá feed", null);

        assertEquals(201, response.getStatus());
        assertEquals("Olá feed", service.capturedBody);
        assertEquals(3, service.capturedImage.length);
        assertInstanceOf(UserPostResponse.class, response.getEntity());
    }

    @Test
    void createPostWithBlankBodyMapsToBadRequest() {
        StubSocialService service = new StubSocialService();
        service.nextException = new IllegalArgumentException("Escreva o texto da publicação");
        TestableSocialResource resource = buildResource(service);

        assertEquals(400, resource.createPost("   ", null).getStatus());
    }

    @Test
    void deletePostByAuthorReturnsNoContent() {
        StubSocialService service = new StubSocialService();
        TestableSocialResource resource = buildResource(service);

        Response response = resource.deletePost(UUID.randomUUID());

        assertEquals(204, response.getStatus());
        assertNull(response.getEntity());
    }

    @Test
    void deletePostByOtherUserMapsToForbidden() {
        StubSocialService service = new StubSocialService();
        service.nextException = new ForbiddenException("Só o autor pode excluir a publicação");
        TestableSocialResource resource = buildResource(service);

        Response response = resource.deletePost(UUID.randomUUID());

        assertEquals(403, response.getStatus());
        assertEquals("AUTH_FORBIDDEN", assertInstanceOf(ErrorResponse.class, response.getEntity()).error());
    }

    @Test
    void getFeedReturnsPage() {
        StubSocialService service = new StubSocialService();
        service.feedResponse = new UserFeedPageResponse(List.of(buildPost("/users/posts/x/media")), 0, 10, false);
        TestableSocialResource resource = buildResource(service);

        Response response = resource.getFeed(0, 10);

        assertEquals(200, response.getStatus());
        UserFeedPageResponse body = assertInstanceOf(UserFeedPageResponse.class, response.getEntity());
        assertEquals(1, body.posts().size());
        assertEquals(false, body.hasNext());
    }

    @Test
    void getPostMediaWithoutMediaMapsToNotFound() {
        StubSocialService service = new StubSocialService();
        service.nextException = new NoSuchElementException("Esta publicação não possui imagem");
        TestableSocialResource resource = buildResource(service);

        assertEquals(404, resource.getPostMedia(UUID.randomUUID()).getStatus());
    }

    @Test
    void endpointsWithoutCurrentUserReturnNotFound() {
        TestableSocialResource resource = buildResource(new StubSocialService());
        resource.currentUser = null;

        assertEquals(404, resource.follow(UUID.randomUUID()).getStatus());
        assertEquals(404, resource.getFeed(0, 10).getStatus());
        assertEquals(404, resource.createPost("x", null).getStatus());
    }

    private static UserPostResponse buildPost(String mediaUrl) {
        return new UserPostResponse(
                UUID.randomUUID(),
                new UserPostAuthorResponse(UUID.randomUUID(), UUID.randomUUID(), "Marina Souza", null),
                "corpo",
                mediaUrl,
                Instant.now()
        );
    }

    private TestableSocialResource buildResource(SocialService service) {
        TestableSocialResource resource = new TestableSocialResource();
        resource.socialService = service;
        User user = new User();
        user.id = UUID.randomUUID();
        resource.currentUser = user;
        return resource;
    }

    private static final class TestableSocialResource extends SocialResource {
        private User currentUser;
        private byte[] nextUploadedBytes;

        @Override
        protected User findCurrentUser() {
            return currentUser;
        }

        @Override
        protected byte[] readOptionalUploadedBytes(FileUpload image) {
            return nextUploadedBytes;
        }
    }

    private static final class StubSocialService implements SocialService {
        private RuntimeException nextException;
        private FollowActionResponse followResponse;
        private UserPostResponse postResponse;
        private UserFeedPageResponse feedResponse;
        private String capturedBody;
        private byte[] capturedImage;

        private void maybeThrow() {
            if (nextException != null) {
                throw nextException;
            }
        }

        @Override
        public FollowActionResponse follow(User currentUser, UUID targetUserProfileId) {
            maybeThrow();
            return followResponse;
        }

        @Override
        public FollowActionResponse unfollow(User currentUser, UUID targetUserProfileId) {
            maybeThrow();
            return followResponse;
        }

        @Override
        public FollowPageResponse listFollowing(User currentUser, Integer page, Integer size) {
            maybeThrow();
            return new FollowPageResponse(List.of(), 0, 20, 0L, 0, false);
        }

        @Override
        public FollowPageResponse listFollowers(User currentUser, Integer page, Integer size) {
            maybeThrow();
            return new FollowPageResponse(List.of(), 0, 20, 0L, 0, false);
        }

        @Override
        public FollowStatsResponse getFollowStats(User currentUser, UUID userProfileId) {
            maybeThrow();
            return new FollowStatsResponse(userProfileId, 0, 0, false);
        }

        @Override
        public UserPostResponse createPost(User author, String body, byte[] imageBytes) {
            maybeThrow();
            capturedBody = body;
            capturedImage = imageBytes;
            return postResponse;
        }

        @Override
        public void deletePost(User currentUser, UUID postId) {
            maybeThrow();
        }

        @Override
        public UserFeedPageResponse getFeed(User currentUser, Integer page, Integer size) {
            maybeThrow();
            return feedResponse;
        }

        @Override
        public byte[] getPostMedia(User currentUser, UUID postId) {
            maybeThrow();
            return new byte[] {1};
        }

        @Override
        public UserFeedPageResponse listPostsByProfile(User currentUser, UUID userProfileId, Integer page, Integer size) {
            maybeThrow();
            return feedResponse;
        }
    }
}
