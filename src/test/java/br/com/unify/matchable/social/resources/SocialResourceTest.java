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
import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.social.dto.UserFeedPageResponse;
import br.com.unify.matchable.social.dto.UserPostAuthorResponse;
import br.com.unify.matchable.social.dto.UserPostCommentCreateRequest;
import br.com.unify.matchable.social.dto.UserPostCommentPageResponse;
import br.com.unify.matchable.social.dto.UserPostCommentResponse;
import br.com.unify.matchable.social.dto.UserPostLikeResponse;
import br.com.unify.matchable.social.dto.UserPostResponse;
import br.com.unify.matchable.social.dto.UserPostUpdateRequest;
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

    @Test
    void updatePostReturnsOkAndForwardsBody() {
        StubSocialService service = new StubSocialService();
        service.postResponse = buildPost(null);
        TestableSocialResource resource = buildResource(service);
        UUID postId = UUID.randomUUID();

        Response response = resource.updatePost(postId, new UserPostUpdateRequest("novo texto"));

        assertEquals(200, response.getStatus());
        assertEquals(postId, service.capturedPostId);
        assertEquals("novo texto", service.capturedBody);
        assertInstanceOf(UserPostResponse.class, response.getEntity());
    }

    @Test
    void updatePostMapsForbiddenNotFoundAndBadRequest() {
        StubSocialService service = new StubSocialService();
        TestableSocialResource resource = buildResource(service);

        service.nextException = new ForbiddenException("Só o autor pode editar a publicação");
        assertEquals(403, resource.updatePost(UUID.randomUUID(), new UserPostUpdateRequest("x")).getStatus());

        service.nextException = new NoSuchElementException("Publicação não encontrada");
        assertEquals(404, resource.updatePost(UUID.randomUUID(), new UserPostUpdateRequest("x")).getStatus());

        service.nextException = new IllegalArgumentException("Escreva o texto da publicação");
        assertEquals(400, resource.updatePost(UUID.randomUUID(), null).getStatus());
    }

    @Test
    void likeAndUnlikeReturnOkWithLikeResponse() {
        StubSocialService service = new StubSocialService();
        TestableSocialResource resource = buildResource(service);
        UUID postId = UUID.randomUUID();

        Response liked = resource.likePost(postId);
        assertEquals(200, liked.getStatus());
        UserPostLikeResponse likedBody = assertInstanceOf(UserPostLikeResponse.class, liked.getEntity());
        assertEquals(postId, likedBody.postId());
        assertEquals(true, likedBody.likedByCurrentUser());

        Response unliked = resource.unlikePost(postId);
        assertEquals(200, unliked.getStatus());
        assertEquals(false, assertInstanceOf(UserPostLikeResponse.class, unliked.getEntity()).likedByCurrentUser());

        service.nextException = new NoSuchElementException("Publicação não encontrada");
        assertEquals(404, resource.likePost(postId).getStatus());
        assertEquals(404, resource.unlikePost(postId).getStatus());
    }

    @Test
    void commentsEndpointsMapStatuses() {
        StubSocialService service = new StubSocialService();
        TestableSocialResource resource = buildResource(service);
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        Response page = resource.getComments(postId, 0, 20);
        assertEquals(200, page.getStatus());
        assertEquals(1, assertInstanceOf(UserPostCommentPageResponse.class, page.getEntity()).comments().size());

        Response created = resource.createComment(postId, new UserPostCommentCreateRequest("oi"));
        assertEquals(201, created.getStatus());
        assertEquals("oi", service.capturedBody);
        assertInstanceOf(UserPostCommentResponse.class, created.getEntity());

        Response deleted = resource.deleteComment(postId, commentId);
        assertEquals(204, deleted.getStatus());
        assertEquals(commentId, service.capturedCommentId);

        service.nextException = new ForbiddenException("Só quem escreveu o comentário ou o autor da publicação pode excluí-lo");
        assertEquals(403, resource.deleteComment(postId, commentId).getStatus());

        service.nextException = new IllegalArgumentException("Escreva o texto do comentário");
        assertEquals(400, resource.createComment(postId, null).getStatus());

        service.nextException = new NoSuchElementException("Publicação não encontrada");
        assertEquals(404, resource.getComments(postId, 0, 20).getStatus());
    }

    private static UserPostResponse buildPost(String mediaUrl) {
        return new UserPostResponse(
                UUID.randomUUID(),
                PostOrigin.PERSONAL,
                null,
                new UserPostAuthorResponse(UUID.randomUUID(), UUID.randomUUID(), "Marina Souza", null),
                "corpo",
                mediaUrl,
                Instant.now(),
                null,
                0,
                0,
                false,
                false,
                null
        );
    }

    private static UserPostCommentResponse buildComment() {
        return new UserPostCommentResponse(
                UUID.randomUUID(),
                new UserPostAuthorResponse(UUID.randomUUID(), UUID.randomUUID(), "Marina Souza", null),
                "oi",
                Instant.now(),
                true
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
        private UUID capturedPostId;
        private UUID capturedCommentId;

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
        public UserPostResponse updatePost(User currentUser, UUID postId, String body) {
            maybeThrow();
            capturedPostId = postId;
            capturedBody = body;
            return postResponse;
        }

        @Override
        public void deletePost(User currentUser, UUID postId) {
            maybeThrow();
        }

        @Override
        public UserPostLikeResponse likePost(User currentUser, UUID postId) {
            maybeThrow();
            return new UserPostLikeResponse(postId, 1, true);
        }

        @Override
        public UserPostLikeResponse unlikePost(User currentUser, UUID postId) {
            maybeThrow();
            return new UserPostLikeResponse(postId, 0, false);
        }

        @Override
        public UserPostCommentPageResponse getComments(User currentUser, UUID postId, Integer page, Integer size) {
            maybeThrow();
            return new UserPostCommentPageResponse(List.of(buildComment()), 0, 20, 1L, false);
        }

        @Override
        public UserPostCommentResponse createComment(User currentUser, UUID postId, String body) {
            maybeThrow();
            capturedPostId = postId;
            capturedBody = body;
            return buildComment();
        }

        @Override
        public void deleteComment(User currentUser, UUID postId, UUID commentId) {
            maybeThrow();
            capturedPostId = postId;
            capturedCommentId = commentId;
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
