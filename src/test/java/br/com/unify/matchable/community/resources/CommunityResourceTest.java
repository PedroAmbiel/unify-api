package br.com.unify.matchable.community.resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.community.dto.CommunityAuthorResponse;
import br.com.unify.matchable.community.dto.CommunityCommentCreateRequest;
import br.com.unify.matchable.community.dto.CommunityCommentResponse;
import br.com.unify.matchable.community.dto.CommunityCommentsResponse;
import br.com.unify.matchable.community.dto.CommunityFeedResponse;
import br.com.unify.matchable.community.dto.CommunityLikeResponse;
import br.com.unify.matchable.community.dto.CommunityMemberHeaderResponse;
import br.com.unify.matchable.community.dto.CommunityMembersResponse;
import br.com.unify.matchable.community.dto.CommunityMemberResponse;
import br.com.unify.matchable.community.dto.CommunityMemberRoleUpdateRequest;
import br.com.unify.matchable.community.dto.CommunityMembershipResponse;
import br.com.unify.matchable.community.dto.CommunityPageResponse;
import br.com.unify.matchable.community.dto.CommunityPostResponse;
import br.com.unify.matchable.community.dto.CommunitySummaryResponse;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.community.services.CommunityService;
import br.com.unify.matchable.user.entity.User;
import jakarta.ws.rs.core.Response;

class CommunityResourceTest {

    @Test
    void listCommunitiesReturnsPaginatedPayload() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.pageResponse = new CommunityPageResponse(List.of(buildCommunitySummary(communityId)), 0, 20, 1L, 1, false);

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.listCommunities(0, 20);

        assertEquals(200, response.getStatus());
        assertEquals(0, service.capturedPage);
        assertEquals(20, service.capturedSize);
        CommunityPageResponse body = assertInstanceOf(CommunityPageResponse.class, response.getEntity());
        assertEquals(List.of("Comunidade Unify"), body.communities().stream().map(CommunitySummaryResponse::name).toList());
    }

    @Test
    void searchCommunitiesReturnsPaginatedPayload() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.pageResponse = new CommunityPageResponse(List.of(buildCommunitySummary(communityId)), 1, 10, 11L, 2, false);

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.searchCommunities("unify", 1, 10);

        assertEquals(200, response.getStatus());
        assertEquals("unify", service.capturedQuery);
        assertEquals(1, service.capturedPage);
        assertEquals(10, service.capturedSize);
        CommunityPageResponse body = assertInstanceOf(CommunityPageResponse.class, response.getEntity());
        assertEquals(11L, body.totalElements());
    }

    @Test
    void createCommunityReturnsCreatedPayloadAndUploadedBytes() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.summaryResponse = buildCommunitySummary(communityId);

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();
        resource.nextUploadedBytes = new byte[] { 1, 2, 3 };

        Response response = resource.createCommunity("Comunidade Unify", "Descrição", null);

        assertEquals(201, response.getStatus());
        assertEquals("Comunidade Unify", service.capturedName);
        assertEquals("Descrição", service.capturedDescription);
        assertArrayEquals(new byte[] { 1, 2, 3 }, service.capturedImageBytes);
        CommunitySummaryResponse body = assertInstanceOf(CommunitySummaryResponse.class, response.getEntity());
        assertEquals(communityId, body.id());
        assertTrue(body.isOwner());
    }

    @Test
    void getFeedReturnsCommunityScopedPayload() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.feedResponse = new CommunityFeedResponse(
                buildCommunitySummary(communityId),
                List.of(new CommunityPostResponse(
                        UUID.randomUUID(),
                        new CommunityAuthorResponse(UUID.randomUUID(), "Pedro Ambiel", null),
                        "há 1 hora",
                        "Primeira publicação",
                        null,
                        3L,
                        1L,
                        true,
                        false
                ))
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.getFeed(communityId);

        assertEquals(200, response.getStatus());
        assertEquals(communityId, service.capturedCommunityId);
        CommunityFeedResponse body = assertInstanceOf(CommunityFeedResponse.class, response.getEntity());
        assertEquals("Primeira publicação", body.posts().getFirst().body());
    }

    @Test
    void joinCommunityReturnsMembershipPayload() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.membershipResponse = new CommunityMembershipResponse(communityId, true, 8L, CommunityMemberRole.MEMBER, false);

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.joinCommunity(communityId);

        assertEquals(200, response.getStatus());
        assertEquals(communityId, service.capturedCommunityId);
        CommunityMembershipResponse body = assertInstanceOf(CommunityMembershipResponse.class, response.getEntity());
        assertEquals(CommunityMemberRole.MEMBER, body.role());
    }

    @Test
    void joinCommunityReturnsConflictWhenUserHasNoProfile() {
        StubCommunityService service = new StubCommunityService();
        service.stateException = new IllegalStateException("Você precisa criar seu perfil antes de participar de comunidades");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.joinCommunity(UUID.randomUUID());

        assertEquals(409, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_CONFLICT", body.error());
    }

    @Test
    void listMembersReturnsUserProfileHeaders() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        UUID userProfileId = UUID.randomUUID();
        service.membersResponse = new CommunityMembersResponse(
                communityId,
                List.of(new CommunityMemberHeaderResponse(
                        userProfileId,
                        "Larissa Costa",
                        "/communities/users/123/avatar",
                        CommunityMemberRole.MEMBER
                ))
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.listMembers(communityId);

        assertEquals(200, response.getStatus());
        assertEquals(communityId, service.capturedCommunityId);
        CommunityMembersResponse body = assertInstanceOf(CommunityMembersResponse.class, response.getEntity());
        assertEquals(userProfileId, body.members().getFirst().userProfileId());
        assertEquals("Larissa Costa", body.members().getFirst().name());
    }

    @Test
    void updateMemberRoleReturnsUpdatedMemberPayload() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        UUID targetUserProfileId = UUID.randomUUID();
        service.memberResponse = new CommunityMemberResponse(
                communityId,
            new CommunityMemberHeaderResponse(targetUserProfileId, "Larissa", null, CommunityMemberRole.MODERATOR),
                CommunityMemberRole.MODERATOR,
                false
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.updateMemberRole(
                communityId,
            targetUserProfileId,
                new CommunityMemberRoleUpdateRequest(CommunityMemberRole.MODERATOR)
        );

        assertEquals(200, response.getStatus());
        assertEquals(communityId, service.capturedCommunityId);
        assertEquals(targetUserProfileId, service.capturedTargetUserProfileId);
        assertEquals(CommunityMemberRole.MODERATOR, service.capturedRole);
        CommunityMemberResponse body = assertInstanceOf(CommunityMemberResponse.class, response.getEntity());
        assertEquals(CommunityMemberRole.MODERATOR, body.role());
        assertEquals(targetUserProfileId, body.user().userProfileId());
    }

    @Test
    void updateMemberRoleReturnsForbiddenWhenServiceRejectsPermission() {
        StubCommunityService service = new StubCommunityService();
        service.securityException = new SecurityException("Você não tem permissão para alterar o nível deste membro");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.updateMemberRole(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new CommunityMemberRoleUpdateRequest(CommunityMemberRole.MODERATOR)
        );

        assertEquals(403, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("AUTH_FORBIDDEN", body.error());
    }

    @Test
    void createPostReturnsCreatedPayloadAndCapturedMultipartFields() {
        StubCommunityService service = new StubCommunityService();
        UUID postId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        service.postResponse = new CommunityPostResponse(
                postId,
                new CommunityAuthorResponse(UUID.randomUUID(), "Mariana Costa", null),
                "agora mesmo",
                "Conteúdo publicado",
                "/communities/posts/" + postId + "/media",
                0L,
                0L,
                false,
                false
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();
        resource.nextUploadedBytes = new byte[] { 9, 9, 9 };

        Response response = resource.createPost(communityId, "Conteúdo publicado", null);

        assertEquals(201, response.getStatus());
        assertEquals(communityId, service.capturedCommunityId);
        assertEquals("Conteúdo publicado", service.capturedBody);
        assertArrayEquals(new byte[] { 9, 9, 9 }, service.capturedImageBytes);
    }

    @Test
    void deletePostReturnsNoContent() {
        StubCommunityService service = new StubCommunityService();
        UUID postId = UUID.randomUUID();

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.deletePost(postId);

        assertEquals(204, response.getStatus());
        assertEquals(postId, service.capturedPostId);
    }

    @Test
    void deleteLikeReturnsNotFoundWhenServiceCannotFindTargetLike() {
        StubCommunityService service = new StubCommunityService();
        service.notFoundException = new NoSuchElementException("Curtida não encontrada");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.deleteLike(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(404, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_NOT_FOUND", body.error());
    }

    @Test
    void deleteCommentReturnsNoContent() {
        StubCommunityService service = new StubCommunityService();
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.deleteComment(postId, commentId);

        assertEquals(204, response.getStatus());
        assertEquals(postId, service.capturedPostId);
        assertEquals(commentId, service.capturedCommentId);
    }

    @Test
    void getPostMediaReturnsStoredBytes() {
        StubCommunityService service = new StubCommunityService();
        UUID postId = UUID.randomUUID();
        service.mediaBytes = new byte[] { 7, 8, 9 };

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.getPostMedia(postId);

        assertEquals(200, response.getStatus());
        assertEquals("image/jpeg", response.getMediaType().toString());
        assertArrayEquals(new byte[] { 7, 8, 9 }, assertInstanceOf(byte[].class, response.getEntity()));
        assertEquals(postId, service.capturedPostId);
    }

    private CommunitySummaryResponse buildCommunitySummary(UUID communityId) {
        UUID ownerId = UUID.randomUUID();
        return new CommunitySummaryResponse(
                communityId,
                "Comunidade Unify",
                5L,
                "Espaço colaborativo",
                "/communities/" + communityId + "/icon",
                true,
                new CommunityAuthorResponse(ownerId, "Owner Unify", null),
                CommunityMemberRole.ADMIN,
                true
        );
    }

    private User buildUser() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = "pedro@example.com";
        return user;
    }

    private static final class TestableCommunityResource extends CommunityResource {
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

    private static final class StubCommunityService implements CommunityService {
        private User capturedUser;
        private Integer capturedPage;
        private Integer capturedSize;
        private String capturedQuery;
        private UUID capturedCommunityId;
        private UUID capturedTargetUserProfileId;
        private UUID capturedPostId;
        private UUID capturedCommentId;
        private String capturedName;
        private String capturedDescription;
        private String capturedBody;
        private byte[] capturedImageBytes;
        private CommunityMemberRole capturedRole;
        private CommunityPageResponse pageResponse;
        private CommunitySummaryResponse summaryResponse;
        private CommunityFeedResponse feedResponse;
        private CommunityMembershipResponse membershipResponse;
        private CommunityMembersResponse membersResponse;
        private CommunityMemberResponse memberResponse;
        private CommunityPostResponse postResponse;
        private CommunityCommentsResponse commentsResponse;
        private CommunityCommentResponse commentResponse;
        private CommunityLikeResponse likeResponse;
        private byte[] mediaBytes;
        private byte[] iconBytes;
        private byte[] avatarBytes;
        private IllegalArgumentException validationException;
        private IllegalStateException stateException;
        private NoSuchElementException notFoundException;
        private SecurityException securityException;

        @Override
        public CommunityPageResponse listCommunities(User user, Integer page, Integer size) {
            capturedUser = user;
            capturedPage = page;
            capturedSize = size;
            if (validationException != null) {
                throw validationException;
            }
            return pageResponse;
        }

        @Override
        public CommunityPageResponse searchCommunities(User user, String query, Integer page, Integer size) {
            capturedUser = user;
            capturedQuery = query;
            capturedPage = page;
            capturedSize = size;
            if (validationException != null) {
                throw validationException;
            }
            return pageResponse;
        }

        @Override
        public CommunitySummaryResponse createCommunity(User user, String name, String description, byte[] iconBytes) {
            capturedUser = user;
            capturedName = name;
            capturedDescription = description;
            capturedImageBytes = iconBytes;
            if (validationException != null) {
                throw validationException;
            }
            if (stateException != null) {
                throw stateException;
            }
            return summaryResponse;
        }

        @Override
        public void deleteCommunity(User user, UUID communityId) {
            capturedUser = user;
            capturedCommunityId = communityId;
            if (securityException != null) {
                throw securityException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
        }

        @Override
        public CommunityFeedResponse getFeed(User user, UUID communityId) {
            capturedUser = user;
            capturedCommunityId = communityId;
            if (notFoundException != null) {
                throw notFoundException;
            }
            return feedResponse;
        }

        @Override
        public CommunityMembershipResponse joinCommunity(User user, UUID communityId) {
            capturedUser = user;
            capturedCommunityId = communityId;
            if (stateException != null) {
                throw stateException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return membershipResponse;
        }

        @Override
        public CommunityMembershipResponse leaveCommunity(User user, UUID communityId) {
            capturedUser = user;
            capturedCommunityId = communityId;
            if (validationException != null) {
                throw validationException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return membershipResponse;
        }

        @Override
        public CommunityMembersResponse listMembers(User user, UUID communityId) {
            capturedUser = user;
            capturedCommunityId = communityId;
            if (validationException != null) {
                throw validationException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return membersResponse;
        }

        @Override
        public CommunityMemberResponse updateMemberRole(User user, UUID communityId, UUID targetUserProfileId, CommunityMemberRole role) {
            capturedUser = user;
            capturedCommunityId = communityId;
            capturedTargetUserProfileId = targetUserProfileId;
            capturedRole = role;
            if (validationException != null) {
                throw validationException;
            }
            if (securityException != null) {
                throw securityException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return memberResponse;
        }

        @Override
        public CommunityPostResponse createPost(User user, UUID communityId, String body, byte[] imageBytes) {
            capturedUser = user;
            capturedCommunityId = communityId;
            capturedBody = body;
            capturedImageBytes = imageBytes;
            if (validationException != null) {
                throw validationException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return postResponse;
        }

        @Override
        public void deletePost(User user, UUID postId) {
            capturedUser = user;
            capturedPostId = postId;
            if (validationException != null) {
                throw validationException;
            }
            if (securityException != null) {
                throw securityException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
        }

        @Override
        public CommunityLikeResponse likePost(User user, UUID postId) {
            capturedUser = user;
            capturedPostId = postId;
            if (validationException != null) {
                throw validationException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return likeResponse;
        }

        @Override
        public CommunityLikeResponse unlikePost(User user, UUID postId) {
            capturedUser = user;
            capturedPostId = postId;
            if (validationException != null) {
                throw validationException;
            }
            if (securityException != null) {
                throw securityException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return likeResponse;
        }

        @Override
        public CommunityLikeResponse deleteLike(User user, UUID postId, UUID targetUserId) {
            capturedUser = user;
            capturedPostId = postId;
            capturedTargetUserProfileId = targetUserId;
            if (validationException != null) {
                throw validationException;
            }
            if (securityException != null) {
                throw securityException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return likeResponse;
        }

        @Override
        public CommunityCommentsResponse getComments(User user, UUID postId) {
            capturedUser = user;
            capturedPostId = postId;
            if (validationException != null) {
                throw validationException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return commentsResponse;
        }

        @Override
        public CommunityCommentResponse createComment(User user, UUID postId, String body) {
            capturedUser = user;
            capturedPostId = postId;
            if (validationException != null) {
                throw validationException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
            return commentResponse;
        }

        @Override
        public void deleteComment(User user, UUID postId, UUID commentId) {
            capturedUser = user;
            capturedPostId = postId;
            capturedCommentId = commentId;
            if (validationException != null) {
                throw validationException;
            }
            if (securityException != null) {
                throw securityException;
            }
            if (notFoundException != null) {
                throw notFoundException;
            }
        }

        @Override
        public byte[] getCommunityIcon(UUID communityId) {
            capturedCommunityId = communityId;
            return iconBytes;
        }

        @Override
        public byte[] getPostMedia(UUID postId) {
            capturedPostId = postId;
            return mediaBytes;
        }

        @Override
        public byte[] getAuthorAvatar(UUID userId) {
            capturedTargetUserProfileId = userId;
            return avatarBytes;
        }
    }
}