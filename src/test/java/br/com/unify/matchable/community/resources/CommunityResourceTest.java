package br.com.unify.matchable.community.resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.community.dto.CommunityAuthorResponse;
import br.com.unify.matchable.community.dto.CommunityCategoryResponse;
import br.com.unify.matchable.community.dto.CommunityCommentCreateRequest;
import br.com.unify.matchable.community.dto.CommunityCommentResponse;
import br.com.unify.matchable.community.dto.CommunityFeedResponse;
import br.com.unify.matchable.community.dto.CommunityLikeResponse;
import br.com.unify.matchable.community.dto.CommunityMemberHeaderResponse;
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

        Response response = resource.listCommunities(0, 20, null);

        assertEquals(200, response.getStatus());
        assertEquals(0, service.capturedPage);
        assertEquals(20, service.capturedSize);
        assertNull(service.capturedCategoryId);
        CommunityPageResponse body = assertInstanceOf(CommunityPageResponse.class, response.getEntity());
        assertEquals(List.of("Comunidade Unify"), body.communities().stream().map(CommunitySummaryResponse::name).toList());
    }

    @Test
    void listCategoriesReturnsSeededCatalog() {
        StubCommunityService service = new StubCommunityService();
        service.categoriesResponse = buildSeededCategories();

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.listCategories();

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<CommunityCategoryResponse> body = assertInstanceOf(List.class, response.getEntity());
        assertEquals(10, body.size());
        assertEquals("Apoio e Bem-estar", body.getFirst().description());
        assertEquals("heart-outline", body.getFirst().ionicIcon());
    }

    @Test
    void listCommunitiesForwardsCategoryFilterToService() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.pageResponse = new CommunityPageResponse(
                List.of(buildCommunitySummary(communityId, new CommunityCategoryResponse(3, "Tecnologia Assistiva", "hardware-chip-outline"))),
                0,
                20,
                1L,
                1,
                false
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.listCommunities(0, 20, 3);

        assertEquals(200, response.getStatus());
        assertEquals(3, service.capturedCategoryId);
        CommunityPageResponse body = assertInstanceOf(CommunityPageResponse.class, response.getEntity());
        assertEquals(
                List.of(3),
                body.communities().stream().map(community -> community.category().id()).toList()
        );
    }

    @Test
    void listMyCommunitiesReturnsPaginatedPayload() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.pageResponse = new CommunityPageResponse(List.of(buildCommunitySummary(communityId)), 0, 20, 1L, 1, false);

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.listMyCommunities(0, 20);

        assertEquals(200, response.getStatus());
        assertTrue(service.listMyCommunitiesCalled);
        assertEquals(0, service.capturedPage);
        assertEquals(20, service.capturedSize);
        CommunityPageResponse body = assertInstanceOf(CommunityPageResponse.class, response.getEntity());
        assertEquals(1L, body.totalElements());
    }

    @Test
    void searchCommunitiesReturnsPaginatedPayload() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.pageResponse = new CommunityPageResponse(List.of(buildCommunitySummary(communityId)), 1, 10, 11L, 2, false);

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.searchCommunities("unify", 1, 10, 7);

        assertEquals(200, response.getStatus());
        assertEquals("unify", service.capturedQuery);
        assertEquals(1, service.capturedPage);
        assertEquals(10, service.capturedSize);
        assertEquals(7, service.capturedCategoryId);
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

        Response response = resource.createCommunity("Comunidade Unify", "Descrição", 3, null);

        assertEquals(201, response.getStatus());
        assertEquals("Comunidade Unify", service.capturedName);
        assertEquals("Descrição", service.capturedDescription);
        assertEquals(3, service.capturedCategoryId);
        assertArrayEquals(new byte[] { 1, 2, 3 }, service.capturedImageBytes);
        CommunitySummaryResponse body = assertInstanceOf(CommunitySummaryResponse.class, response.getEntity());
        assertEquals(communityId, body.id());
        assertTrue(body.isOwner());
    }

    @Test
    void createCommunityReturnsBadRequestWhenCategoryDoesNotExist() {
        StubCommunityService service = new StubCommunityService();
        service.validationException = new IllegalArgumentException("Categoria de comunidade inválida");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.createCommunity("Comunidade Unify", "Descrição", 999, null);

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_INVALID_FORMAT", body.error());
        assertTrue(body.message().contains("Categoria de comunidade inválida"));
    }

    @Test
    void updateCommunityReturnsUpdatedSummary() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.summaryResponse = buildCommunitySummary(
                communityId,
                new CommunityCategoryResponse(2, "Arte e Cultura", "color-palette-outline")
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();
        resource.nextUploadedBytes = new byte[] { 4, 5 };

        Response response = resource.updateCommunity(communityId, "Novo nome", "Nova descrição", 2, null);

        assertEquals(200, response.getStatus());
        assertEquals(communityId, service.capturedCommunityId);
        assertEquals("Novo nome", service.capturedName);
        assertEquals("Nova descrição", service.capturedDescription);
        assertEquals(2, service.capturedCategoryId);
        assertArrayEquals(new byte[] { 4, 5 }, service.capturedImageBytes);
        CommunitySummaryResponse body = assertInstanceOf(CommunitySummaryResponse.class, response.getEntity());
        assertEquals(2, body.category().id());
    }

    @Test
    void updateCommunityReturnsForbiddenWhenServiceRejectsNonAdmin() {
        StubCommunityService service = new StubCommunityService();
        service.securityException = new SecurityException("Apenas administradores da comunidade podem editar os dados dela");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.updateCommunity(UUID.randomUUID(), "Novo nome", null, null, null);

        assertEquals(403, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("AUTH_FORBIDDEN", body.error());
        assertTrue(body.message().contains("Apenas administradores da comunidade podem editar os dados dela"));
    }

    @Test
    void updateCommunityReturnsNotFoundWhenCommunityDoesNotExist() {
        StubCommunityService service = new StubCommunityService();
        service.notFoundException = new NoSuchElementException("Comunidade não encontrada");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.updateCommunity(UUID.randomUUID(), "Novo nome", null, null, null);

        assertEquals(404, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_NOT_FOUND", body.error());
    }

    @Test
    void getFeedReturnsCommunityScopedPayload() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.feedResponse = new CommunityFeedResponse(
                buildCommunitySummary(communityId),
                PageResponse.of(List.of(buildPost("Primeira publicação")), 0, 20, 1L)
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.getFeed(communityId, null, null);

        assertEquals(200, response.getStatus());
        assertEquals(communityId, service.capturedCommunityId);
        CommunityFeedResponse body = assertInstanceOf(CommunityFeedResponse.class, response.getEntity());
        assertEquals("Primeira publicação", body.posts().content().getFirst().body());
    }

    @Test
    void getFeedReturnsPaginatedPostsEnvelope() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        service.feedResponse = new CommunityFeedResponse(
                buildCommunitySummary(communityId),
                PageResponse.of(List.of(buildPost("Publicação da página 1")), 1, 2, 5L)
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.getFeed(communityId, 1, 2);

        assertEquals(200, response.getStatus());
        assertEquals(1, service.capturedPage);
        assertEquals(2, service.capturedSize);
        CommunityFeedResponse body = assertInstanceOf(CommunityFeedResponse.class, response.getEntity());
        PageResponse<CommunityPostResponse> posts = body.posts();
        assertEquals(1, posts.content().size());
        assertEquals(1, posts.page());
        assertEquals(2, posts.size());
        assertEquals(5L, posts.totalElements());
        assertEquals(3, posts.totalPages());
        assertTrue(posts.hasNext());
    }

    @Test
    void getFeedReturnsBadRequestWhenSizeExceedsMaximum() {
        StubCommunityService service = new StubCommunityService();
        service.validationException = new IllegalArgumentException("O parâmetro 'size' deve estar entre 1 e 100");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.getFeed(UUID.randomUUID(), 0, 1000);

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_INVALID_FORMAT", body.error());
    }

    @Test
    void getCommentsReturnsBadRequestWhenSizeExceedsMaximum() {
        StubCommunityService service = new StubCommunityService();
        service.validationException = new IllegalArgumentException("O parâmetro 'size' deve estar entre 1 e 100");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.getComments(UUID.randomUUID(), 0, 1000);

        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_INVALID_FORMAT", assertInstanceOf(ErrorResponse.class, response.getEntity()).error());
    }

    @Test
    void getCommentsReturnsPaginatedEnvelope() {
        StubCommunityService service = new StubCommunityService();
        UUID postId = UUID.randomUUID();
        service.commentsResponse = PageResponse.of(
                List.of(new CommunityCommentResponse(
                        UUID.randomUUID(),
                        new CommunityAuthorResponse(UUID.randomUUID(), "Larissa Costa", null),
                        "há 2 minutos",
                        "Primeiro comentário",
                        false
                )),
                0,
                20,
                1L
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.getComments(postId, null, null);

        assertEquals(200, response.getStatus());
        assertEquals(postId, service.capturedPostId);
        @SuppressWarnings("unchecked")
        PageResponse<CommunityCommentResponse> body = assertInstanceOf(PageResponse.class, response.getEntity());
        assertEquals("Primeiro comentário", body.content().getFirst().body());
        assertEquals(1L, body.totalElements());
        assertEquals(1, body.totalPages());
        assertFalse(body.hasNext());
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
        Instant joinedAt = Instant.parse("2026-08-10T12:00:00Z");
        service.membersResponse = PageResponse.of(
                List.of(new CommunityMemberHeaderResponse(
                        userProfileId,
                        "Larissa Costa",
                        "/communities/users/123/avatar",
                        CommunityMemberRole.MEMBER,
                        joinedAt
                )),
                0,
                20,
                1L
        );

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.listMembers(communityId, null, null);

        assertEquals(200, response.getStatus());
        assertEquals(communityId, service.capturedCommunityId);
        @SuppressWarnings("unchecked")
        PageResponse<CommunityMemberHeaderResponse> body = assertInstanceOf(PageResponse.class, response.getEntity());
        assertEquals(userProfileId, body.content().getFirst().userProfileId());
        assertEquals("Larissa Costa", body.content().getFirst().name());
        assertNotNull(body.content().getFirst().joinedAt());
        assertEquals(joinedAt, body.content().getFirst().joinedAt());
        assertEquals(0, body.page());
        assertEquals(20, body.size());
        assertEquals(1L, body.totalElements());
        assertFalse(body.hasNext());
    }

    @Test
    void listMembersReturnsBadRequestWhenSizeExceedsMaximum() {
        StubCommunityService service = new StubCommunityService();
        service.validationException = new IllegalArgumentException("O parâmetro 'size' deve estar entre 1 e 100");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.listMembers(UUID.randomUUID(), 0, 1000);

        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_INVALID_FORMAT", assertInstanceOf(ErrorResponse.class, response.getEntity()).error());
    }

    @Test
    void listCommunitiesReturnsBadRequestWhenSizeExceedsMaximum() {
        StubCommunityService service = new StubCommunityService();
        service.validationException = new IllegalArgumentException("O parâmetro 'size' deve estar entre 1 e 100");

        TestableCommunityResource resource = new TestableCommunityResource();
        resource.communityService = service;
        resource.currentUser = buildUser();

        Response response = resource.listCommunities(0, 1000, null);

        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_INVALID_FORMAT", assertInstanceOf(ErrorResponse.class, response.getEntity()).error());
    }

    @Test
    void updateMemberRoleReturnsUpdatedMemberPayload() {
        StubCommunityService service = new StubCommunityService();
        UUID communityId = UUID.randomUUID();
        UUID targetUserProfileId = UUID.randomUUID();
        service.memberResponse = new CommunityMemberResponse(
                communityId,
            new CommunityMemberHeaderResponse(targetUserProfileId, "Larissa", null, CommunityMemberRole.MODERATOR, Instant.now()),
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

    private CommunityPostResponse buildPost(String body) {
        return new CommunityPostResponse(
                UUID.randomUUID(),
                new CommunityAuthorResponse(UUID.randomUUID(), "Pedro Ambiel", null),
                "há 1 hora",
                body,
                null,
                3L,
                1L,
                true,
                false
        );
    }

    private CommunitySummaryResponse buildCommunitySummary(UUID communityId) {
        return buildCommunitySummary(communityId, null);
    }

    private CommunitySummaryResponse buildCommunitySummary(UUID communityId, CommunityCategoryResponse category) {
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
                true,
                category
        );
    }

    /** Espelha o catalogo semeado em V4__create_community_categories.sql, ja ordenado por descricao. */
    private List<CommunityCategoryResponse> buildSeededCategories() {
        return List.of(
                new CommunityCategoryResponse(4, "Apoio e Bem-estar", "heart-outline"),
                new CommunityCategoryResponse(2, "Arte e Cultura", "color-palette-outline"),
                new CommunityCategoryResponse(5, "Educação e Estudos", "school-outline"),
                new CommunityCategoryResponse(1, "Esportes Adaptados", "basketball-outline"),
                new CommunityCategoryResponse(7, "Jogos e Games Acessíveis", "game-controller-outline"),
                new CommunityCategoryResponse(8, "Música e Podcasts", "musical-notes-outline"),
                new CommunityCategoryResponse(10, "Relacionamentos e Amizade", "people-outline"),
                new CommunityCategoryResponse(3, "Tecnologia Assistiva", "hardware-chip-outline"),
                new CommunityCategoryResponse(6, "Trabalho e Empreendedorismo", "briefcase-outline"),
                new CommunityCategoryResponse(9, "Viagem e Mobilidade Urbana", "airplane-outline")
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
        private Integer capturedCategoryId;
        private boolean listMyCommunitiesCalled;
        private List<CommunityCategoryResponse> categoriesResponse = List.of();
        private String capturedName;
        private String capturedDescription;
        private String capturedBody;
        private byte[] capturedImageBytes;
        private CommunityMemberRole capturedRole;
        private CommunityPageResponse pageResponse;
        private CommunitySummaryResponse summaryResponse;
        private CommunityFeedResponse feedResponse;
        private CommunityMembershipResponse membershipResponse;
        private PageResponse<CommunityMemberHeaderResponse> membersResponse;
        private CommunityMemberResponse memberResponse;
        private CommunityPostResponse postResponse;
        private PageResponse<CommunityCommentResponse> commentsResponse;
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
        public List<CommunityCategoryResponse> listCategories() {
            return categoriesResponse;
        }

        @Override
        public CommunityPageResponse listCommunities(User user, Integer categoryId, Integer page, Integer size) {
            capturedUser = user;
            capturedCategoryId = categoryId;
            capturedPage = page;
            capturedSize = size;
            if (validationException != null) {
                throw validationException;
            }
            return pageResponse;
        }

        @Override
        public CommunityPageResponse searchCommunities(User user, String query, Integer categoryId, Integer page, Integer size) {
            capturedUser = user;
            capturedQuery = query;
            capturedCategoryId = categoryId;
            capturedPage = page;
            capturedSize = size;
            if (validationException != null) {
                throw validationException;
            }
            return pageResponse;
        }

        @Override
        public CommunityPageResponse listMyCommunities(User user, Integer page, Integer size) {
            capturedUser = user;
            capturedPage = page;
            capturedSize = size;
            listMyCommunitiesCalled = true;
            if (validationException != null) {
                throw validationException;
            }
            return pageResponse;
        }

        @Override
        public CommunitySummaryResponse createCommunity(User user, String name, String description, Integer categoryId, byte[] iconBytes) {
            capturedUser = user;
            capturedName = name;
            capturedDescription = description;
            capturedCategoryId = categoryId;
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
        public CommunitySummaryResponse updateCommunity(
                User user,
                UUID communityId,
                String name,
                String description,
                Integer categoryId,
                byte[] iconBytes
        ) {
            capturedUser = user;
            capturedCommunityId = communityId;
            capturedName = name;
            capturedDescription = description;
            capturedCategoryId = categoryId;
            capturedImageBytes = iconBytes;
            if (validationException != null) {
                throw validationException;
            }
            if (securityException != null) {
                throw securityException;
            }
            if (notFoundException != null) {
                throw notFoundException;
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
        public CommunityFeedResponse getFeed(User user, UUID communityId, Integer page, Integer size) {
            capturedUser = user;
            capturedCommunityId = communityId;
            capturedPage = page;
            capturedSize = size;
            if (validationException != null) {
                throw validationException;
            }
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
        public PageResponse<CommunityMemberHeaderResponse> listMembers(User user, UUID communityId, Integer page, Integer size) {
            capturedUser = user;
            capturedCommunityId = communityId;
            capturedPage = page;
            capturedSize = size;
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
        public PageResponse<CommunityCommentResponse> getComments(User user, UUID postId, Integer page, Integer size) {
            capturedUser = user;
            capturedPostId = postId;
            capturedPage = page;
            capturedSize = size;
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