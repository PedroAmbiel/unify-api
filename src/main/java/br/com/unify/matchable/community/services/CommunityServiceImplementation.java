package br.com.unify.matchable.community.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.ToLongFunction;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.dto.PageParams;
import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.common.image.OidImageService;
import br.com.unify.matchable.community.dto.CommunityAuthorResponse;
import br.com.unify.matchable.community.dto.CommunityCategoryResponse;
import br.com.unify.matchable.community.dto.CommunityCommentResponse;
import br.com.unify.matchable.community.dto.CommunityFeedResponse;
import br.com.unify.matchable.community.dto.CommunityForYouPostResponse;
import br.com.unify.matchable.community.dto.CommunityJoinRequestResponse;
import br.com.unify.matchable.community.dto.CommunityLikeResponse;
import br.com.unify.matchable.community.dto.CommunityMemberHeaderResponse;
import br.com.unify.matchable.community.dto.CommunityMemberResponse;
import br.com.unify.matchable.community.dto.CommunityMembershipResponse;
import br.com.unify.matchable.community.dto.CommunityPageResponse;
import br.com.unify.matchable.community.dto.CommunityPostResponse;
import br.com.unify.matchable.community.dto.CommunitySummaryResponse;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityCategory;
import br.com.unify.matchable.community.entity.CommunityJoinRequest;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.community.entity.CommunityPost;
import br.com.unify.matchable.community.entity.CommunityPostComment;
import br.com.unify.matchable.community.entity.CommunityPostLike;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.community.enums.CommunityPrivacy;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.entity.UserProfileImage;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CommunityServiceImplementation implements CommunityService {

    private static final String COMMUNITY_NOT_FOUND_MESSAGE = "Comunidade não encontrada";
    private static final String COMMUNITY_ICON_NOT_FOUND_MESSAGE = "Ícone da comunidade não encontrado";
    private static final String COMMUNITY_NAME_REQUIRED_MESSAGE = "Informe o nome da comunidade";
    private static final String COMMUNITY_SEARCH_REQUIRED_MESSAGE = "Informe um texto para pesquisar comunidades";
    private static final String COMMUNITY_MEMBER_NOT_FOUND_MESSAGE = "Membro da comunidade não encontrado";
    private static final String COMMUNITY_OWNER_LEAVE_MESSAGE = "O proprietário da comunidade não pode sair da própria comunidade";
    private static final String COMMUNITY_DELETE_FORBIDDEN_MESSAGE = "Apenas o proprietário da comunidade pode excluir a comunidade";
    private static final String COMMUNITY_UPDATE_FORBIDDEN_MESSAGE = "Apenas administradores da comunidade podem editar os dados dela";
    private static final String COMMUNITY_CATEGORY_INVALID_MESSAGE = "Categoria de comunidade inválida";
    private static final String COMMUNITY_ROLE_UPDATE_REQUIRED_MESSAGE = "Informe o novo nível de permissão do membro";
    private static final String COMMUNITY_ROLE_UPDATE_FORBIDDEN_MESSAGE = "Você não tem permissão para alterar o nível deste membro";
    private static final String COMMUNITY_SELF_ROLE_UPDATE_FORBIDDEN_MESSAGE = "Você não pode alterar o próprio nível de permissão";
    private static final String COMMUNITY_OWNER_ROLE_UPDATE_FORBIDDEN_MESSAGE = "Não é possível alterar o nível do proprietário da comunidade";
    private static final String COMMUNITY_PROFILE_REQUIRED_MESSAGE = "Você precisa criar seu perfil antes de participar de comunidades";
    private static final String POST_NOT_FOUND_MESSAGE = "Publicação não encontrada";
    private static final String POST_MEDIA_NOT_FOUND_MESSAGE = "Imagem da publicação não encontrada";
    private static final String POST_BODY_REQUIRED_MESSAGE = "Informe o conteúdo da publicação";
    private static final String POST_DELETE_FORBIDDEN_MESSAGE = "Você não tem permissão para remover esta publicação";
    private static final String COMMENT_NOT_FOUND_MESSAGE = "Comentário não encontrado";
    private static final String COMMENT_BODY_REQUIRED_MESSAGE = "Informe o conteúdo do comentário";
    private static final String COMMENT_DELETE_FORBIDDEN_MESSAGE = "Você não tem permissão para remover este comentário";
    private static final String LIKE_NOT_FOUND_MESSAGE = "Curtida não encontrada";
    private static final String LIKE_DELETE_FORBIDDEN_MESSAGE = "Você não tem permissão para remover esta curtida";
    private static final String AUTHOR_AVATAR_NOT_FOUND_MESSAGE = "Avatar do autor não encontrado";
    private static final String MEMBERSHIP_REQUIRED_MESSAGE = "Você precisa participar da comunidade para interagir";
    private static final String COMMUNITY_PRIVACY_INVALID_MESSAGE = "Visibilidade de comunidade inválida";
    private static final String JOIN_REQUEST_NOT_FOUND_MESSAGE = "Solicitação de entrada não encontrada";
    private static final String JOIN_REQUEST_MANAGE_FORBIDDEN_MESSAGE = "Apenas administradores ou moderadores podem gerenciar solicitações de entrada";
    private static final String MEMBERSHIP_OR_REQUEST_NOT_FOUND_MESSAGE = "Você não participa nem possui solicitação pendente nesta comunidade";
    private static final String COMMUNITY_ICON_URL_SUFFIX = "/icon";
    private static final String POST_MEDIA_URL_PREFIX = "/communities/posts/";
    private static final String POST_MEDIA_URL_SUFFIX = "/media";
    private static final String AUTHOR_AVATAR_URL_PREFIX = "/communities/users/";
    private static final String AUTHOR_AVATAR_URL_SUFFIX = "/avatar";

    @Inject
    OidImageService oidImageService;

    @Override
    public List<CommunityCategoryResponse> listCategories() {
        return CommunityCategory.listAllOrderedByDescription().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    /*
     * Nota de performance/trade-off: a ordenacao por relevancia (nome > descricao) e por memberCount
     * e feita em memoria depois de buscar todas as comunidades que casam com o filtro, e a paginacao
     * e um subList sobre a lista ja ordenada. Aceitavel na escala atual (dezenas/centenas de
     * comunidades); se o volume crescer, revisitar com SQL nativo calculando relevancia e memberCount
     * em uma unica query com ORDER BY (como em UserMatchServiceImplementation).
     */
    @Override
    public CommunityPageResponse listCommunities(User user, Integer categoryId, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        List<Community> matches = categoryId != null
                ? Community.<Community>find("active = true and category.id = ?1", categoryId).list()
                : Community.<Community>find("active = true").list();

        List<Community> ordered = sortByPopularity(matches, CommunityMembership::countByCommunity);

        long totalElements = ordered.size();
        List<Community> pageItems = paginate(ordered, resolvedPage, resolvedSize);
        return toCommunityPageResponse(pageItems, user, resolvedPage, resolvedSize, totalElements);
    }

    @Override
    public CommunityPageResponse searchCommunities(User user, String queryText, Integer categoryId, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);
        String normalizedQuery = requireText(queryText, COMMUNITY_SEARCH_REQUIRED_MESSAGE).toLowerCase();
        String wildcardQuery = "%" + normalizedQuery + "%";

        String jpql = "active = true and (lower(name) like ?1 or lower(coalesce(description, '')) like ?1)"
                + (categoryId != null ? " and category.id = ?2" : "");

        List<Community> matches = categoryId != null
                ? Community.<Community>find(jpql, wildcardQuery, categoryId).list()
                : Community.<Community>find(jpql, wildcardQuery).list();

        List<Community> ordered = sortByRelevance(matches, normalizedQuery, CommunityMembership::countByCommunity);

        long totalElements = ordered.size();
        List<Community> pageItems = paginate(ordered, resolvedPage, resolvedSize);
        return toCommunityPageResponse(pageItems, user, resolvedPage, resolvedSize, totalElements);
    }

    @Override
    public CommunityPageResponse listMyCommunities(User user, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        List<CommunityMembership> memberships = CommunityMembership.list(
                "userProfile.user = ?1 and community.active = true order by joinedAt desc",
                user
        );

        List<Community> communities = memberships.stream().map(membership -> membership.community).toList();
        long totalElements = communities.size();
        List<Community> pageItems = paginate(communities, resolvedPage, resolvedSize);

        return toCommunityPageResponse(pageItems, user, resolvedPage, resolvedSize, totalElements);
    }

    /** Ordena por popularidade (memberCount desc) e desempata pelo nome. */
    List<Community> sortByPopularity(List<Community> communities, ToLongFunction<Community> memberCounter) {
        Comparator<Community> ordering = Comparator.<Community>comparingLong(memberCounter::applyAsLong)
                .reversed()
                .thenComparing(community -> community.name, String.CASE_INSENSITIVE_ORDER);
        return communities.stream().sorted(ordering).toList();
    }

    /** Match no nome vem antes de match apenas na descricao; empates caem para popularidade. */
    List<Community> sortByRelevance(
            List<Community> communities,
            String normalizedQuery,
            ToLongFunction<Community> memberCounter
    ) {
        Comparator<Community> ordering = Comparator
                .comparing((Community community) -> !containsIgnoreCase(community.name, normalizedQuery))
                .thenComparing(Comparator.<Community>comparingLong(memberCounter::applyAsLong).reversed());
        return communities.stream().sorted(ordering).toList();
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && query != null && value.toLowerCase().contains(query.toLowerCase());
    }

    private <T> List<T> paginate(List<T> items, int page, int size) {
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return items.subList(fromIndex, toIndex);
    }

    @Override
    @Transactional
    public CommunitySummaryResponse createCommunity(
            User user,
            String name,
            String description,
            Integer categoryId,
            String privacy,
            byte[] iconBytes
    ) {
        UserProfile ownerProfile = requireUserProfile(user);

        Community community = new Community();
        community.id = UUIDv7Generator.generate();
        community.name = requireText(name, COMMUNITY_NAME_REQUIRED_MESSAGE);
        community.description = normalizeText(description);
        community.owner = user;
        community.category = resolveCategory(categoryId);
        community.privacy = resolvePrivacy(privacy, CommunityPrivacy.PUBLIC);
        community.active = true;
        community.featured = false;
        if (iconBytes != null && iconBytes.length > 0) {
            community.iconOid = oidImageService.toOidBlob(oidImageService.compressToJpeg(iconBytes));
        }
        community.persist();

        CommunityMembership membership = new CommunityMembership();
        membership.id = UUIDv7Generator.generate();
        membership.community = community;
    membership.userProfile = ownerProfile;
        membership.role = CommunityMemberRole.ADMIN;
        membership.joinedAt = Instant.now();
        membership.persist();

        return toCommunitySummaryResponse(community, user);
    }

    @Override
    @Transactional
    public CommunitySummaryResponse updateCommunity(
            User user,
            UUID communityId,
            String name,
            String description,
            Integer categoryId,
            String privacy,
            byte[] iconBytes
    ) {
        Community community = requireCommunity(communityId);
        ensureAdmin(user, community);

        community.name = requireText(name, COMMUNITY_NAME_REQUIRED_MESSAGE);
        community.description = normalizeText(description);
        community.category = resolveCategory(categoryId);
        community.privacy = resolvePrivacy(privacy, community.privacy);
        if (iconBytes != null && iconBytes.length > 0) {
            community.iconOid = oidImageService.toOidBlob(oidImageService.compressToJpeg(iconBytes));
        }

        return toCommunitySummaryResponse(community, user);
    }

    @Override
    @Transactional
    public void deleteCommunity(User user, UUID communityId) {
        Community community = requireCommunity(communityId);
        ensureOwner(user, community);

        community.active = false;
        community.featured = false;
    }

    @Override
    public CommunityFeedResponse getFeed(User user, UUID communityId, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        Community community = resolveFeedCommunity(communityId);
        if (community == null) {
            return new CommunityFeedResponse(null, PageResponse.empty(resolvedPage, resolvedSize));
        }

        PanacheQuery<CommunityPost> query = CommunityPost.queryByCommunity(community);
        long totalElements = query.count();
        List<CommunityPostResponse> posts = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(post -> toPostResponse(post, user))
                .toList();

        return new CommunityFeedResponse(
                toCommunitySummaryResponse(community, user),
                PageResponse.of(posts, resolvedPage, resolvedSize, totalElements)
        );
    }

    @Override
    @Transactional
    public CommunityMembershipResponse joinCommunity(User user, UUID communityId) {
        Community community = requireCommunityOrDefault(communityId);
        UserProfile userProfile = requireUserProfile(user);
        CommunityMembership existingMembership = CommunityMembership.findByCommunityAndUserProfile(community, userProfile);
        if (existingMembership != null) {
            return toMembershipResponse(community, existingMembership, user, false);
        }

        if (community.privacy == CommunityPrivacy.PRIVATE) {
            if (CommunityJoinRequest.findByCommunityAndUser(community, user) == null) {
                CommunityJoinRequest joinRequest = new CommunityJoinRequest();
                joinRequest.id = UUIDv7Generator.generate();
                joinRequest.community = community;
                joinRequest.userProfile = userProfile;
                joinRequest.requestedAt = Instant.now();
                joinRequest.persist();
            }
            return toMembershipResponse(community, null, user, true);
        }

        CommunityMembership membership = createMembership(community, userProfile, CommunityMemberRole.MEMBER);
        return toMembershipResponse(community, membership, user, false);
    }

    @Override
    @Transactional
    public CommunityMembershipResponse leaveCommunity(User user, UUID communityId) {
        Community community = requireCommunityOrDefault(communityId);
        CommunityMembership existingMembership = CommunityMembership.findByCommunityAndUser(community, user);
        if (existingMembership == null) {
            CommunityJoinRequest pendingRequest = CommunityJoinRequest.findByCommunityAndUser(community, user);
            if (pendingRequest == null) {
                throw new IllegalStateException(MEMBERSHIP_OR_REQUEST_NOT_FOUND_MESSAGE);
            }
            pendingRequest.delete();
            return toMembershipResponse(community, null, user, false);
        }

        if (isOwner(community, user)) {
            throw new IllegalStateException(COMMUNITY_OWNER_LEAVE_MESSAGE);
        }

        existingMembership.delete();
        return toMembershipResponse(community, null, user, false);
    }

    /*
     * Descoberta de comunidades: prioriza afinidade de categoria (categorias das
     * comunidades que o usuário já participa) e, dentro de cada grupo, ordena por
     * engajamento (membros, publicações e curtidas). Mesmo trade-off de memória
     * documentado em listCommunities: aceitável na escala atual.
     */
    @Override
    public CommunityPageResponse discoverCommunities(User user, Integer categoryId, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        List<Community> matches = categoryId != null
                ? Community.<Community>find("active = true and category.id = ?1", categoryId).list()
                : Community.<Community>find("active = true").list();

        java.util.Set<Integer> affinityCategoryIds = CommunityMembership
                .<CommunityMembership>list("userProfile.user = ?1 and community.active = true", user)
                .stream()
                .map(membership -> membership.community.category)
                .filter(java.util.Objects::nonNull)
                .map(category -> category.id)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<UUID, Long> engagementByCommunity = new java.util.HashMap<>();
        for (Community community : matches) {
            long members = CommunityMembership.countByCommunity(community);
            long posts = CommunityPost.count("community", community);
            long likes = CommunityPostLike.count("post.community", community);
            engagementByCommunity.put(community.id, members * 3 + posts * 2 + likes);
        }

        Comparator<Community> ordering = Comparator
                .comparing((Community community) -> isUserMember(community, user))
                .thenComparing(community -> community.category == null
                        || !affinityCategoryIds.contains(community.category.id))
                .thenComparing(Comparator.<Community>comparingLong(community ->
                        engagementByCommunity.getOrDefault(community.id, 0L)).reversed())
                .thenComparing(community -> community.name, String.CASE_INSENSITIVE_ORDER);

        List<Community> ordered = matches.stream().sorted(ordering).toList();
        long totalElements = ordered.size();
        List<Community> pageItems = paginate(ordered, resolvedPage, resolvedSize);
        return toCommunityPageResponse(pageItems, user, resolvedPage, resolvedSize, totalElements);
    }

    @Override
    public PageResponse<CommunityForYouPostResponse> getForYouFeed(User user, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        PanacheQuery<CommunityPost> query = CommunityPost.find(
                "community.active = true and community in "
                        + "(select membership.community from CommunityMembership membership where membership.userProfile.user = ?1) "
                        + "order by createdAt desc, id desc",
                user
        );
        long totalElements = query.count();
        List<CommunityForYouPostResponse> posts = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(post -> new CommunityForYouPostResponse(
                        post.community.id,
                        post.community.name,
                        post.community.iconOid == null ? null : "/communities/" + post.community.id + COMMUNITY_ICON_URL_SUFFIX,
                        toPostResponse(post, user)
                ))
                .toList();

        return PageResponse.of(posts, resolvedPage, resolvedSize, totalElements);
    }

    @Override
    public PageResponse<CommunityJoinRequestResponse> listJoinRequests(User user, UUID communityId, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        Community community = requireCommunity(communityId);
        ensureCanManageJoinRequests(user, community);

        PanacheQuery<CommunityJoinRequest> query = CommunityJoinRequest.queryByCommunity(community);
        long totalElements = query.count();
        List<CommunityJoinRequestResponse> requests = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(this::toJoinRequestResponse)
                .toList();

        return PageResponse.of(requests, resolvedPage, resolvedSize, totalElements);
    }

    @Override
    @Transactional
    public CommunityMemberHeaderResponse approveJoinRequest(User user, UUID communityId, UUID requestId) {
        CommunityJoinRequest joinRequest = requireJoinRequest(user, communityId, requestId);

        Community community = joinRequest.community;
        UserProfile requesterProfile = joinRequest.userProfile;
        joinRequest.delete();

        CommunityMembership membership = CommunityMembership.findByCommunityAndUserProfile(community, requesterProfile);
        if (membership == null) {
            membership = createMembership(community, requesterProfile, CommunityMemberRole.MEMBER);
        }
        return toMemberHeaderResponse(membership);
    }

    @Override
    @Transactional
    public void declineJoinRequest(User user, UUID communityId, UUID requestId) {
        requireJoinRequest(user, communityId, requestId).delete();
    }

    private CommunityJoinRequest requireJoinRequest(User user, UUID communityId, UUID requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("Informe o identificador da solicitação de entrada");
        }

        Community community = requireCommunity(communityId);
        ensureCanManageJoinRequests(user, community);

        CommunityJoinRequest joinRequest = CommunityJoinRequest.findByIdAndCommunity(requestId, community);
        if (joinRequest == null) {
            throw new NoSuchElementException(JOIN_REQUEST_NOT_FOUND_MESSAGE);
        }
        return joinRequest;
    }

    private void ensureCanManageJoinRequests(User user, Community community) {
        CommunityMembership membership = CommunityMembership.findByCommunityAndUser(community, user);
        if (!isElevated(membership)) {
            throw new SecurityException(JOIN_REQUEST_MANAGE_FORBIDDEN_MESSAGE);
        }
    }

    private CommunityMembership createMembership(Community community, UserProfile userProfile, CommunityMemberRole role) {
        CommunityMembership membership = new CommunityMembership();
        membership.id = UUIDv7Generator.generate();
        membership.community = community;
        membership.userProfile = userProfile;
        membership.role = role;
        membership.joinedAt = Instant.now();
        membership.persist();
        return membership;
    }

    private CommunityJoinRequestResponse toJoinRequestResponse(CommunityJoinRequest joinRequest) {
        UserProfile profile = joinRequest.userProfile;
        User requester = profile == null ? null : profile.user;
        return new CommunityJoinRequestResponse(
                joinRequest.id,
                profile == null ? null : profile.id,
                requester == null ? null : buildAuthorName(requester),
                hasAvatar(profile) && requester != null && requester.id != null
                        ? AUTHOR_AVATAR_URL_PREFIX + requester.id + AUTHOR_AVATAR_URL_SUFFIX
                        : null,
                joinRequest.requestedAt
        );
    }

    private boolean isUserMember(Community community, User user) {
        return user != null && CommunityMembership.findByCommunityAndUser(community, user) != null;
    }

    CommunityPrivacy resolvePrivacy(String privacy, CommunityPrivacy fallback) {
        String normalized = normalizeText(privacy);
        if (normalized == null) {
            return fallback == null ? CommunityPrivacy.PUBLIC : fallback;
        }
        try {
            return CommunityPrivacy.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(COMMUNITY_PRIVACY_INVALID_MESSAGE);
        }
    }

    @Override
    public PageResponse<CommunityMemberHeaderResponse> listMembers(User user, UUID communityId, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        Community community = requireCommunity(communityId);
        // A lista de membros e conteudo interno: so quem participa da comunidade pode ler.
        requireMembership(community, user);
        PanacheQuery<CommunityMembership> query = CommunityMembership.queryByCommunity(community);
        long totalElements = query.count();
        List<CommunityMemberHeaderResponse> members = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(this::toMemberHeaderResponse)
                .toList();

        return PageResponse.of(members, resolvedPage, resolvedSize, totalElements);
    }

    @Override
    @Transactional
    public CommunityMemberResponse updateMemberRole(User user, UUID communityId, UUID targetUserProfileId, CommunityMemberRole role) {
        if (role == null) {
            throw new IllegalArgumentException(COMMUNITY_ROLE_UPDATE_REQUIRED_MESSAGE);
        }
        if (targetUserProfileId == null) {
            throw new IllegalArgumentException("Informe o identificador do perfil do membro da comunidade");
        }

        Community community = requireCommunity(communityId);
        CommunityMembership actorMembership = requireMembership(community, user);
        CommunityMembership targetMembership = CommunityMembership.findByCommunityAndUserProfileId(community, targetUserProfileId);
        if (targetMembership == null) {
            throw new NoSuchElementException(COMMUNITY_MEMBER_NOT_FOUND_MESSAGE);
        }

        ensureCanUpdateRole(user, community, actorMembership, targetMembership, role);
        targetMembership.role = role;
        return toMemberResponse(community, targetMembership);
    }

    @Override
    @Transactional
    public CommunityPostResponse createPost(User user, UUID communityId, String body, byte[] imageBytes) {
        Community community = requireMembership(communityId, user);

        CommunityPost post = new CommunityPost();
        post.id = UUIDv7Generator.generate();
        post.community = community;
        post.author = user;
        post.body = requireText(body, POST_BODY_REQUIRED_MESSAGE);
        post.createdAt = Instant.now();
        if (imageBytes != null && imageBytes.length > 0) {
            post.mediaOid = oidImageService.toOidBlob(oidImageService.compressToJpeg(imageBytes));
        }
        post.persist();

        return toPostResponse(post, user);
    }

    @Override
    @Transactional
    public void deletePost(User user, UUID postId) {
        CommunityPost post = requirePost(postId);
        CommunityMembership actorMembership = requireMembership(post.community, user);
        if (!isContentOwner(user, post.author) && !isElevated(actorMembership)) {
            throw new SecurityException(POST_DELETE_FORBIDDEN_MESSAGE);
        }

        CommunityPostLike.delete("post", post);
        CommunityPostComment.delete("post", post);
        post.delete();
    }

    @Override
    @Transactional
    public CommunityLikeResponse likePost(User user, UUID postId) {
        CommunityPost post = requirePost(postId);
        requireMembership(post.community, user);

        CommunityPostLike existingLike = CommunityPostLike.findByPostAndUser(post, user);
        if (existingLike == null) {
            CommunityPostLike like = new CommunityPostLike();
            like.id = UUIDv7Generator.generate();
            like.post = post;
            like.user = user;
            like.createdAt = Instant.now();
            like.persist();
        }

        return new CommunityLikeResponse(post.id, CommunityPostLike.countByPost(post), true);
    }

    @Override
    @Transactional
    public CommunityLikeResponse unlikePost(User user, UUID postId) {
        return deleteLike(user, postId, user == null ? null : user.id);
    }

    @Override
    @Transactional
    public CommunityLikeResponse deleteLike(User user, UUID postId, UUID targetUserId) {
        CommunityPost post = requirePost(postId);
        CommunityMembership actorMembership = requireMembership(post.community, user);

        if (targetUserId == null) {
            throw new IllegalArgumentException("Informe o identificador do usuário da curtida");
        }

        User targetUser = User.findById(targetUserId);
        if (targetUser == null) {
            throw new NoSuchElementException(LIKE_NOT_FOUND_MESSAGE);
        }

        CommunityPostLike like = CommunityPostLike.findByPostAndUser(post, targetUser);
        if (like == null) {
            throw new NoSuchElementException(LIKE_NOT_FOUND_MESSAGE);
        }

        if (!isContentOwner(user, targetUser) && !isElevated(actorMembership)) {
            throw new SecurityException(LIKE_DELETE_FORBIDDEN_MESSAGE);
        }

        like.delete();
        return new CommunityLikeResponse(
                post.id,
                CommunityPostLike.countByPost(post),
                CommunityPostLike.findByPostAndUser(post, user) != null
        );
    }

    @Override
    public PageResponse<CommunityCommentResponse> getComments(User user, UUID postId, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        CommunityPost post = requirePost(postId);
        PanacheQuery<CommunityPostComment> query = CommunityPostComment.queryByPost(post);
        long totalElements = query.count();
        List<CommunityCommentResponse> comments = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(comment -> toCommentResponse(comment, user))
                .toList();

        return PageResponse.of(comments, resolvedPage, resolvedSize, totalElements);
    }

    @Override
    @Transactional
    public CommunityCommentResponse createComment(User user, UUID postId, String body) {
        CommunityPost post = requirePost(postId);
        requireMembership(post.community, user);

        CommunityPostComment comment = new CommunityPostComment();
        comment.id = UUIDv7Generator.generate();
        comment.post = post;
        comment.author = user;
        comment.body = requireText(body, COMMENT_BODY_REQUIRED_MESSAGE);
        comment.createdAt = Instant.now();
        comment.persist();

        return toCommentResponse(comment, user);
    }

    @Override
    @Transactional
    public void deleteComment(User user, UUID postId, UUID commentId) {
        CommunityPost post = requirePost(postId);
        CommunityMembership actorMembership = requireMembership(post.community, user);

        if (commentId == null) {
            throw new IllegalArgumentException("Informe o identificador do comentário");
        }

        CommunityPostComment comment = CommunityPostComment.findByIdAndPost(commentId, post);
        if (comment == null) {
            throw new NoSuchElementException(COMMENT_NOT_FOUND_MESSAGE);
        }

        if (!isContentOwner(user, comment.author) && !isElevated(actorMembership)) {
            throw new SecurityException(COMMENT_DELETE_FORBIDDEN_MESSAGE);
        }

        comment.delete();
    }

    @Override
    public byte[] getCommunityIcon(UUID communityId) {
        Community community = requireCommunity(communityId);
        if (community.iconOid == null) {
            throw new NoSuchElementException(COMMUNITY_ICON_NOT_FOUND_MESSAGE);
        }

        return oidImageService.readOidBlob(community.iconOid);
    }

    @Override
    public byte[] getPostMedia(UUID postId) {
        CommunityPost post = requirePost(postId);
        if (post.mediaOid == null) {
            throw new NoSuchElementException(POST_MEDIA_NOT_FOUND_MESSAGE);
        }

        return oidImageService.readOidBlob(post.mediaOid);
    }

    @Override
    public byte[] getAuthorAvatar(UUID userId) {
        User author = User.findById(userId);
        if (author == null) {
            throw new NoSuchElementException(AUTHOR_AVATAR_NOT_FOUND_MESSAGE);
        }

        UserProfile profile = UserProfile.findByUser(author);
        if (profile == null) {
            throw new NoSuchElementException(AUTHOR_AVATAR_NOT_FOUND_MESSAGE);
        }

        UserProfileImage image = UserProfileImage.findActiveProfilePicture(profile);
        if (image == null) {
            throw new NoSuchElementException(AUTHOR_AVATAR_NOT_FOUND_MESSAGE);
        }

        return oidImageService.readOidBlob(image.oid);
    }

    private Community resolveFeedCommunity(UUID communityId) {
        if (communityId == null) {
            return Community.findFeedCommunity();
        }
        return requireCommunity(communityId);
    }

    private Community requireCommunityOrDefault(UUID communityId) {
        Community community = resolveFeedCommunity(communityId);
        if (community == null) {
            throw new NoSuchElementException(COMMUNITY_NOT_FOUND_MESSAGE);
        }
        return community;
    }

    private Community requireCommunity(UUID communityId) {
        if (communityId == null) {
            throw new IllegalArgumentException("Informe o identificador da comunidade");
        }

        Community community = Community.findActiveById(communityId);
        if (community == null) {
            throw new NoSuchElementException(COMMUNITY_NOT_FOUND_MESSAGE);
        }
        return community;
    }

    private Community requireMembership(UUID communityId, User user) {
        Community community = requireCommunityOrDefault(communityId);
        requireMembership(community, user);
        return community;
    }

    private CommunityMembership requireMembership(Community community, User user) {
        CommunityMembership membership = CommunityMembership.findByCommunityAndUser(community, user);
        if (membership == null) {
            throw new IllegalStateException(MEMBERSHIP_REQUIRED_MESSAGE);
        }
        return membership;
    }

    private UserProfile requireUserProfile(User user) {
        UserProfile profile = resolveUserProfile(user);
        if (profile == null) {
            throw new IllegalStateException(COMMUNITY_PROFILE_REQUIRED_MESSAGE);
        }
        return profile;
    }

    private UserProfile resolveUserProfile(User user) {
        if (user == null) {
            return null;
        }
        return UserProfile.findByUser(user);
    }

    private CommunityPost requirePost(UUID postId) {
        if (postId == null) {
            throw new IllegalArgumentException("Informe o identificador da publicação");
        }

        CommunityPost post = CommunityPost.findByIdWithActiveCommunity(postId);
        if (post == null) {
            throw new NoSuchElementException(POST_NOT_FOUND_MESSAGE);
        }

        return post;
    }

    private void ensureOwner(User user, Community community) {
        if (!isOwner(community, user)) {
            throw new SecurityException(COMMUNITY_DELETE_FORBIDDEN_MESSAGE);
        }
    }

    private void ensureAdmin(User user, Community community) {
        ensureAdminMembership(CommunityMembership.findByCommunityAndUser(community, user));
    }

    /**
     * Regra estritamente ADMIN (diferente de {@code isElevated}, que tambem aceita MODERATOR).
     * O dono da comunidade sempre vira ADMIN em {@code createCommunity}, entao ja esta coberto.
     */
    void ensureAdminMembership(CommunityMembership membership) {
        boolean isAdmin = membership != null && membership.role == CommunityMemberRole.ADMIN;
        if (!isAdmin) {
            throw new SecurityException(COMMUNITY_UPDATE_FORBIDDEN_MESSAGE);
        }
    }

    CommunityCategory resolveCategory(Integer categoryId) {
        if (categoryId == null) {
            return null;
        }

        CommunityCategory category = CommunityCategory.findById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException(COMMUNITY_CATEGORY_INVALID_MESSAGE);
        }
        return category;
    }

    private CommunityCategoryResponse toCategoryResponse(CommunityCategory category) {
        if (category == null) {
            return null;
        }
        return new CommunityCategoryResponse(category.id, category.description, category.ionicIcon);
    }

    private void ensureCanUpdateRole(
            User actor,
            Community community,
            CommunityMembership actorMembership,
            CommunityMembership targetMembership,
            CommunityMemberRole desiredRole
    ) {
        User targetUser = getMembershipUser(targetMembership);
        if (actor == null || actor.id == null || targetUser == null || targetUser.id == null) {
            throw new SecurityException(COMMUNITY_ROLE_UPDATE_FORBIDDEN_MESSAGE);
        }
        if (actor.id.equals(targetUser.id)) {
            throw new SecurityException(COMMUNITY_SELF_ROLE_UPDATE_FORBIDDEN_MESSAGE);
        }
        if (!isElevated(actorMembership)) {
            throw new SecurityException(COMMUNITY_ROLE_UPDATE_FORBIDDEN_MESSAGE);
        }
        if (isOwner(community, targetUser)) {
            throw new SecurityException(COMMUNITY_OWNER_ROLE_UPDATE_FORBIDDEN_MESSAGE);
        }
        if (actorMembership.role == CommunityMemberRole.MODERATOR
                && (targetMembership.role == CommunityMemberRole.ADMIN || desiredRole == CommunityMemberRole.ADMIN)) {
            throw new SecurityException(COMMUNITY_ROLE_UPDATE_FORBIDDEN_MESSAGE);
        }
    }

    private CommunityPageResponse toCommunityPageResponse(
            List<Community> communities,
            User currentUser,
            int page,
            int size,
            long totalElements
    ) {
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new CommunityPageResponse(
                communities.stream().map(community -> toCommunitySummaryResponse(community, currentUser)).toList(),
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages
        );
    }

    private CommunitySummaryResponse toCommunitySummaryResponse(Community community, User user) {
        CommunityMembership membership = user == null ? null : CommunityMembership.findByCommunityAndUser(community, user);
        boolean hasPendingRequest = membership == null
                && user != null
                && CommunityJoinRequest.findByCommunityAndUser(community, user) != null;
        return new CommunitySummaryResponse(
                community.id,
                community.name,
                CommunityMembership.countByCommunity(community),
                community.description,
                community.iconOid == null ? null : "/communities/" + community.id + COMMUNITY_ICON_URL_SUFFIX,
                membership != null,
                toAuthorResponse(community.owner),
                membership == null ? null : membership.role,
                isOwner(community, user),
                toCategoryResponse(community.category),
                community.privacy,
                hasPendingRequest
        );
    }

    private CommunityMembershipResponse toMembershipResponse(
            Community community,
            CommunityMembership membership,
            User currentUser,
            boolean pendingRequest
    ) {
        return new CommunityMembershipResponse(
                community.id,
                membership != null,
                CommunityMembership.countByCommunity(community),
                membership == null ? null : membership.role,
                isOwner(community, currentUser),
                pendingRequest
        );
    }

    private CommunityMemberResponse toMemberResponse(Community community, CommunityMembership membership) {
        User targetUser = getMembershipUser(membership);
        return new CommunityMemberResponse(
                community.id,
            toMemberHeaderResponse(membership),
                membership.role,
            isOwner(community, targetUser)
        );
    }

    CommunityMemberHeaderResponse toMemberHeaderResponse(CommunityMembership membership) {
        UserProfile profile = membership == null ? null : membership.userProfile;
        User member = profile == null ? null : profile.user;
        return new CommunityMemberHeaderResponse(
                profile == null ? null : profile.id,
            member == null ? null : buildAuthorName(member),
            hasAvatar(profile) && member != null && member.id != null
                ? AUTHOR_AVATAR_URL_PREFIX + member.id + AUTHOR_AVATAR_URL_SUFFIX
                : null,
            membership == null ? null : membership.role,
            membership == null ? null : membership.joinedAt
        );
    }

    private CommunityPostResponse toPostResponse(CommunityPost post, User currentUser) {
        return new CommunityPostResponse(
                post.id,
                toAuthorResponse(post.author),
                formatPublishedAt(post.createdAt),
                post.body,
                post.mediaOid == null ? null : POST_MEDIA_URL_PREFIX + post.id + POST_MEDIA_URL_SUFFIX,
                CommunityPostLike.countByPost(post),
                CommunityPostComment.countByPost(post),
                currentUser != null && CommunityPostLike.findByPostAndUser(post, currentUser) != null,
                currentUser != null && CommunityPostComment.existsByPostAndUser(post, currentUser)
        );
    }

    private CommunityCommentResponse toCommentResponse(CommunityPostComment comment, User currentUser) {
        boolean commentedByCurrentUser = currentUser != null
                && currentUser.id != null
                && comment.author != null
                && currentUser.id.equals(comment.author.id);

        return new CommunityCommentResponse(
                comment.id,
                toAuthorResponse(comment.author),
                formatPublishedAt(comment.createdAt),
                comment.body,
                commentedByCurrentUser
        );
    }

    private CommunityAuthorResponse toAuthorResponse(User author) {
        UserProfile profile = UserProfile.findByUser(author);
        return new CommunityAuthorResponse(
                author.id,
                buildAuthorName(author),
                hasAvatar(profile) ? AUTHOR_AVATAR_URL_PREFIX + author.id + AUTHOR_AVATAR_URL_SUFFIX : null
        );
    }

    private boolean hasAvatar(UserProfile profile) {
        return profile != null && UserProfileImage.findActiveProfilePicture(profile) != null;
    }

    private boolean isOwner(Community community, User user) {
        return community != null
                && community.owner != null
                && user != null
                && community.owner.id != null
                && community.owner.id.equals(user.id);
    }

    private boolean isContentOwner(User actor, User contentOwner) {
        return actor != null
                && actor.id != null
                && contentOwner != null
                && contentOwner.id != null
                && actor.id.equals(contentOwner.id);
    }

    private User getMembershipUser(CommunityMembership membership) {
        if (membership == null || membership.userProfile == null) {
            return null;
        }
        return membership.userProfile.user;
    }

    private boolean isElevated(CommunityMembership membership) {
        return membership != null
                && (membership.role == CommunityMemberRole.ADMIN || membership.role == CommunityMemberRole.MODERATOR);
    }

    private int validatePage(Integer page) {
        return PageParams.resolvePage(page);
    }

    private int validateSize(Integer size) {
        return PageParams.resolveSize(size);
    }

    private String requireText(String value, String message) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String buildAuthorName(User author) {
        String firstName = normalizeText(author.name);
        String lastName = normalizeText(author.lastName);
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (firstName != null) {
            return firstName;
        }
        if (lastName != null) {
            return lastName;
        }
        return normalizeText(author.email) == null ? author.id.toString() : author.email;
    }

    private String formatPublishedAt(Instant publishedAt) {
        if (publishedAt == null) {
            return null;
        }

        Instant now = Instant.now();
        if (publishedAt.isAfter(now)) {
            return "agora mesmo";
        }

        Duration duration = Duration.between(publishedAt, now);
        long minutes = duration.toMinutes();
        if (minutes <= 0) {
            return "agora mesmo";
        }
        if (minutes == 1) {
            return "há 1 minuto";
        }
        if (minutes < 60) {
            return "há " + minutes + " minutos";
        }

        long hours = duration.toHours();
        if (hours == 1) {
            return "há 1 hora";
        }
        if (hours < 24) {
            return "há " + hours + " horas";
        }

        long days = duration.toDays();
        if (days == 1) {
            return "há 1 dia";
        }
        if (days < 30) {
            return "há " + days + " dias";
        }

        long months = Math.max(1, days / 30);
        if (months == 1) {
            return "há 1 mês";
        }
        if (months < 12) {
            return "há " + months + " meses";
        }

        long years = Math.max(1, months / 12);
        if (years == 1) {
            return "há 1 ano";
        }
        return "há " + years + " anos";
    }
}