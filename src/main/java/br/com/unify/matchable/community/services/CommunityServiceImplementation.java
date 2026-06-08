package br.com.unify.matchable.community.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.image.OidImageService;
import br.com.unify.matchable.community.dto.CommunityAuthorResponse;
import br.com.unify.matchable.community.dto.CommunityCommentResponse;
import br.com.unify.matchable.community.dto.CommunityCommentsResponse;
import br.com.unify.matchable.community.dto.CommunityFeedResponse;
import br.com.unify.matchable.community.dto.CommunityLikeResponse;
import br.com.unify.matchable.community.dto.CommunityMemberHeaderResponse;
import br.com.unify.matchable.community.dto.CommunityMembersResponse;
import br.com.unify.matchable.community.dto.CommunityMemberResponse;
import br.com.unify.matchable.community.dto.CommunityMembershipResponse;
import br.com.unify.matchable.community.dto.CommunityPageResponse;
import br.com.unify.matchable.community.dto.CommunityPostResponse;
import br.com.unify.matchable.community.dto.CommunitySummaryResponse;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.community.entity.CommunityPost;
import br.com.unify.matchable.community.entity.CommunityPostComment;
import br.com.unify.matchable.community.entity.CommunityPostLike;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
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

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final String COMMUNITY_NOT_FOUND_MESSAGE = "Comunidade não encontrada";
    private static final String COMMUNITY_ICON_NOT_FOUND_MESSAGE = "Ícone da comunidade não encontrado";
    private static final String COMMUNITY_NAME_REQUIRED_MESSAGE = "Informe o nome da comunidade";
    private static final String COMMUNITY_SEARCH_REQUIRED_MESSAGE = "Informe um texto para pesquisar comunidades";
    private static final String COMMUNITY_MEMBER_NOT_FOUND_MESSAGE = "Membro da comunidade não encontrado";
    private static final String COMMUNITY_OWNER_LEAVE_MESSAGE = "O proprietário da comunidade não pode sair da própria comunidade";
    private static final String COMMUNITY_DELETE_FORBIDDEN_MESSAGE = "Apenas o proprietário da comunidade pode excluir a comunidade";
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
    private static final String INVALID_PAGE_MESSAGE = "O parâmetro 'page' deve ser maior ou igual a zero";
    private static final String INVALID_SIZE_MESSAGE = "O parâmetro 'size' deve estar entre 1 e " + MAX_PAGE_SIZE;
    private static final String COMMUNITY_ICON_URL_SUFFIX = "/icon";
    private static final String POST_MEDIA_URL_PREFIX = "/communities/posts/";
    private static final String POST_MEDIA_URL_SUFFIX = "/media";
    private static final String AUTHOR_AVATAR_URL_PREFIX = "/communities/users/";
    private static final String AUTHOR_AVATAR_URL_SUFFIX = "/avatar";

    @Inject
    OidImageService oidImageService;

    @Override
    public CommunityPageResponse listCommunities(User user, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        PanacheQuery<Community> query = Community.find("active = true order by id desc");
        long totalElements = query.count();
        List<Community> communities = query.page(Page.of(resolvedPage, resolvedSize)).list();
        return toCommunityPageResponse(communities, user, resolvedPage, resolvedSize, totalElements);
    }

    @Override
    public CommunityPageResponse searchCommunities(User user, String queryText, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);
        String normalizedQuery = requireText(queryText, COMMUNITY_SEARCH_REQUIRED_MESSAGE).toLowerCase();
        String wildcardQuery = "%" + normalizedQuery + "%";

        PanacheQuery<Community> query = Community.find(
                "active = true and (lower(name) like ?1 or lower(coalesce(description, '')) like ?1) order by id desc",
                wildcardQuery
        );
        long totalElements = query.count();
        List<Community> communities = query.page(Page.of(resolvedPage, resolvedSize)).list();
        return toCommunityPageResponse(communities, user, resolvedPage, resolvedSize, totalElements);
    }

    @Override
    @Transactional
    public CommunitySummaryResponse createCommunity(User user, String name, String description, byte[] iconBytes) {
        UserProfile ownerProfile = requireUserProfile(user);

        Community community = new Community();
        community.id = UUIDv7Generator.generate();
        community.name = requireText(name, COMMUNITY_NAME_REQUIRED_MESSAGE);
        community.description = normalizeText(description);
        community.owner = user;
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
    public void deleteCommunity(User user, UUID communityId) {
        Community community = requireCommunity(communityId);
        ensureOwner(user, community);

        community.active = false;
        community.featured = false;
    }

    @Override
    public CommunityFeedResponse getFeed(User user, UUID communityId) {
        Community community = resolveFeedCommunity(communityId);
        if (community == null) {
            return new CommunityFeedResponse(null, List.of());
        }

        return new CommunityFeedResponse(
                toCommunitySummaryResponse(community, user),
                CommunityPost.listByCommunity(community).stream()
                        .map(post -> toPostResponse(post, user))
                        .toList()
        );
    }

    @Override
    @Transactional
    public CommunityMembershipResponse joinCommunity(User user, UUID communityId) {
        Community community = requireCommunityOrDefault(communityId);
        UserProfile userProfile = requireUserProfile(user);
        CommunityMembership existingMembership = CommunityMembership.findByCommunityAndUserProfile(community, userProfile);
        if (existingMembership == null) {
            CommunityMembership membership = new CommunityMembership();
            membership.id = UUIDv7Generator.generate();
            membership.community = community;
            membership.userProfile = userProfile;
            membership.role = CommunityMemberRole.MEMBER;
            membership.joinedAt = Instant.now();
            membership.persist();
            return toMembershipResponse(community, membership, user);
        }

        return toMembershipResponse(community, existingMembership, user);
    }

    @Override
    @Transactional
    public CommunityMembershipResponse leaveCommunity(User user, UUID communityId) {
        Community community = requireCommunityOrDefault(communityId);
        CommunityMembership existingMembership = requireMembership(community, user);
        if (isOwner(community, user)) {
            throw new IllegalStateException(COMMUNITY_OWNER_LEAVE_MESSAGE);
        }

        existingMembership.delete();
        return toMembershipResponse(community, null, user);
    }

    @Override
    public CommunityMembersResponse listMembers(User user, UUID communityId) {
        Community community = requireCommunity(communityId);
        List<CommunityMembership> memberships = CommunityMembership.listByCommunity(community);

        return new CommunityMembersResponse(
                community.id,
                memberships.stream().map(this::toMemberHeaderResponse).toList()
        );
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
    public CommunityCommentsResponse getComments(User user, UUID postId) {
        CommunityPost post = requirePost(postId);
        return new CommunityCommentsResponse(
                post.id,
                CommunityPostComment.listByPost(post).stream()
                        .map(comment -> toCommentResponse(comment, user))
                        .toList()
        );
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
        return new CommunitySummaryResponse(
                community.id,
                community.name,
                CommunityMembership.countByCommunity(community),
                community.description,
                community.iconOid == null ? null : "/communities/" + community.id + COMMUNITY_ICON_URL_SUFFIX,
                membership != null,
                toAuthorResponse(community.owner),
                membership == null ? null : membership.role,
                isOwner(community, user)
        );
    }

    private CommunityMembershipResponse toMembershipResponse(Community community, CommunityMembership membership, User currentUser) {
        return new CommunityMembershipResponse(
                community.id,
                membership != null,
                CommunityMembership.countByCommunity(community),
                membership == null ? null : membership.role,
                isOwner(community, currentUser)
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

        private CommunityMemberHeaderResponse toMemberHeaderResponse(CommunityMembership membership) {
        UserProfile profile = membership == null ? null : membership.userProfile;
        User member = profile == null ? null : profile.user;
        return new CommunityMemberHeaderResponse(
                profile == null ? null : profile.id,
            member == null ? null : buildAuthorName(member),
            hasAvatar(profile) && member != null && member.id != null
                ? AUTHOR_AVATAR_URL_PREFIX + member.id + AUTHOR_AVATAR_URL_SUFFIX
                : null,
            membership == null ? null : membership.role
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
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        if (resolvedPage < 0) {
            throw new IllegalArgumentException(INVALID_PAGE_MESSAGE);
        }
        return resolvedPage;
    }

    private int validateSize(Integer size) {
        int resolvedSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (resolvedSize < 1 || resolvedSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(INVALID_SIZE_MESSAGE);
        }
        return resolvedSize;
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