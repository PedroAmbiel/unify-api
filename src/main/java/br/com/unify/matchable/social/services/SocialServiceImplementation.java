package br.com.unify.matchable.social.services;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.dto.PageParams;
import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.common.exceptions.ForbiddenException;
import br.com.unify.matchable.common.image.OidImageService;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.post.entity.PostComment;
import br.com.unify.matchable.post.entity.PostLike;
import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.social.dto.FollowActionResponse;
import br.com.unify.matchable.social.dto.FollowPageResponse;
import br.com.unify.matchable.social.dto.FollowStatsResponse;
import br.com.unify.matchable.social.dto.FollowedProfileSummaryResponse;
import br.com.unify.matchable.social.dto.UserFeedPageResponse;
import br.com.unify.matchable.social.dto.UserPostAuthorResponse;
import br.com.unify.matchable.social.dto.UserPostCommentPageResponse;
import br.com.unify.matchable.social.dto.UserPostCommentResponse;
import br.com.unify.matchable.social.dto.UserPostCommunityResponse;
import br.com.unify.matchable.social.dto.UserPostLikeResponse;
import br.com.unify.matchable.social.dto.UserPostResponse;
import br.com.unify.matchable.social.entity.UserFollow;
import br.com.unify.matchable.social.enums.FeedSource;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.entity.UserProfileImage;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Seguir/deixar de seguir e feed pessoal. Posts pessoais são linhas de
 * {@link Post} com {@code origin = PERSONAL} (tabela única, ver
 * {@link PostOrigin}); o autor é o {@link User}, igual aos posts de comunidade.
 */
@ApplicationScoped
public class SocialServiceImplementation implements SocialService {

    static final String PROFILE_REQUIRED_MESSAGE =
            "Você precisa criar seu perfil antes de seguir pessoas ou publicar";
    static final String TARGET_PROFILE_REQUIRED_MESSAGE = "Informe o perfil";
    static final String TARGET_PROFILE_NOT_FOUND_MESSAGE = "Perfil não encontrado";
    static final String SELF_FOLLOW_FORBIDDEN_MESSAGE = "Não é possível seguir o próprio perfil";
    static final String POST_BODY_REQUIRED_MESSAGE = "Escreva o texto da publicação";
    static final String POST_BODY_TOO_LONG_MESSAGE = "A publicação pode ter no máximo 4000 caracteres";
    static final String POST_NOT_FOUND_MESSAGE = "Publicação não encontrada";
    static final String POST_MEDIA_NOT_FOUND_MESSAGE = "Esta publicação não possui imagem";
    static final String POST_DELETE_FORBIDDEN_MESSAGE = "Só o autor pode excluir a publicação";
    static final String POST_UPDATE_FORBIDDEN_MESSAGE = "Só o autor pode editar a publicação";
    static final String COMMENT_BODY_REQUIRED_MESSAGE = "Escreva o texto do comentário";
    static final String COMMENT_BODY_TOO_LONG_MESSAGE = "O comentário pode ter no máximo 2000 caracteres";
    static final String COMMENT_NOT_FOUND_MESSAGE = "Comentário não encontrado";
    static final String COMMENT_DELETE_FORBIDDEN_MESSAGE =
            "Só quem escreveu o comentário ou o autor da publicação pode excluí-lo";

    static final int POST_BODY_MAX_LENGTH = 4000;
    static final int COMMENT_BODY_MAX_LENGTH = 2000;

    /**
     * Reaproveita o endpoint autenticado de avatar já existente
     * ({@code GET /communities/users/{userId}/avatar}), que resolve a foto de
     * perfil ativa a partir do User — sem duplicar a lógica de imagem aqui.
     */
    static final String AVATAR_URL_PREFIX = "/communities/users/";
    static final String AVATAR_URL_SUFFIX = "/avatar";
    static final String POST_MEDIA_URL_PREFIX = "/users/posts/";
    static final String COMMUNITY_POST_MEDIA_URL_PREFIX = "/communities/posts/";
    static final String POST_MEDIA_URL_SUFFIX = "/media";
    static final String COMMUNITY_ICON_URL_PREFIX = "/communities/";
    static final String COMMUNITY_ICON_URL_SUFFIX = "/icon";

    @Inject
    OidImageService oidImageService;

    // ------------------------------------------------------------- follow

    @Override
    @Transactional
    public FollowActionResponse follow(User currentUser, UUID targetUserProfileId) {
        UserProfile follower = requireUserProfile(currentUser);
        UserProfile followed = requireTargetProfile(targetUserProfileId);

        if (followed.id.equals(follower.id)) {
            throw new IllegalArgumentException(SELF_FOLLOW_FORBIDDEN_MESSAGE);
        }

        // Idempotente de propósito: a UI pode disparar toques repetidos; repetir "seguir" é seguro.
        if (UserFollow.findPair(follower, followed) == null) {
            UserFollow follow = new UserFollow();
            follow.id = UUIDv7Generator.generate();
            follow.follower = follower;
            follow.followed = followed;
            follow.createdAt = Instant.now();
            follow.persist();
        }

        return toActionResponse(follower, followed, true);
    }

    @Override
    @Transactional
    public FollowActionResponse unfollow(User currentUser, UUID targetUserProfileId) {
        UserProfile follower = requireUserProfile(currentUser);
        UserProfile followed = requireTargetProfile(targetUserProfileId);

        UserFollow existing = UserFollow.findPair(follower, followed);
        if (existing != null) {
            existing.delete();
        }

        return toActionResponse(follower, followed, false);
    }

    @Override
    public FollowPageResponse listFollowing(User currentUser, Integer page, Integer size) {
        UserProfile me = requireUserProfile(currentUser);
        int resolvedPage = PageParams.resolvePage(page);
        int resolvedSize = PageParams.resolveSize(size);

        PanacheQuery<UserFollow> query = UserFollow.queryFollowing(me);
        long total = query.count();
        List<FollowedProfileSummaryResponse> profiles = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(follow -> toSummary(follow.followed, true))
                .toList();

        return toFollowPage(profiles, resolvedPage, resolvedSize, total);
    }

    @Override
    public FollowPageResponse listFollowers(User currentUser, Integer page, Integer size) {
        UserProfile me = requireUserProfile(currentUser);
        int resolvedPage = PageParams.resolvePage(page);
        int resolvedSize = PageParams.resolveSize(size);

        PanacheQuery<UserFollow> query = UserFollow.queryFollowers(me);
        long total = query.count();
        List<FollowedProfileSummaryResponse> profiles = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(follow -> toSummary(follow.follower, UserFollow.findPair(me, follow.follower) != null))
                .toList();

        return toFollowPage(profiles, resolvedPage, resolvedSize, total);
    }

    @Override
    public FollowStatsResponse getFollowStats(User currentUser, UUID userProfileId) {
        UserProfile target = requireTargetProfile(userProfileId);
        UserProfile me = resolveUserProfile(currentUser);
        boolean followedByCurrentUser = me != null
                && !me.id.equals(target.id)
                && UserFollow.findPair(me, target) != null;

        return new FollowStatsResponse(
                target.id,
                UserFollow.countFollowers(target),
                UserFollow.countFollowing(target),
                followedByCurrentUser
        );
    }

    // -------------------------------------------------------------- posts

    @Override
    @Transactional
    public UserPostResponse createPost(User author, String body, byte[] imageBytes) {
        requireUserProfile(author);

        Post post = new Post();
        post.id = UUIDv7Generator.generate();
        post.origin = PostOrigin.PERSONAL;
        post.community = null;
        post.author = author;
        post.body = requireBody(body);
        post.createdAt = Instant.now();
        post.active = true;
        if (imageBytes != null && imageBytes.length > 0) {
            post.mediaOid = oidImageService.toOidBlob(oidImageService.compressToJpeg(imageBytes));
        }
        post.persist();

        return toPostResponse(post, author);
    }

    @Override
    @Transactional
    public UserPostResponse updatePost(User currentUser, UUID postId, String body) {
        requireUserProfile(currentUser);
        Post post = requireActivePersonalPost(postId);

        if (!post.author.id.equals(currentUser.id)) {
            throw new ForbiddenException(POST_UPDATE_FORBIDDEN_MESSAGE);
        }

        // Só o texto muda; a imagem fica como está.
        post.body = requireBody(body);
        post.editedAt = Instant.now();

        return toPostResponse(post, currentUser);
    }

    @Override
    @Transactional
    public void deletePost(User currentUser, UUID postId) {
        Post post = requireActivePersonalPost(postId);

        if (!post.author.id.equals(currentUser.id)) {
            throw new ForbiddenException(POST_DELETE_FORBIDDEN_MESSAGE);
        }

        // Soft delete: a linha continua na tabela (denúncias/moderação continuam apontando para ela).
        post.active = false;
    }

    @Override
    public UserFeedPageResponse getFeed(User currentUser, Integer page, Integer size) {
        UserProfile me = requireUserProfile(currentUser);
        int resolvedPage = PageParams.resolvePage(page);
        int resolvedSize = PageParams.resolveSize(size);

        // Feed ranqueado (HomeFeedRankingPolicy): quem eu sigo e minhas comunidades
        // pesam mais, mas perfis com interesses parecidos e comunidades PÚBLICAS
        // afins entram como sugestão — o feed só fica vazio se não existir
        // nenhuma publicação elegível no sistema. Meus posts pessoais ficam de
        // fora de propósito: eles vivem na aba Perfil.
        List<UUID> followedUserIds = UserFollow.listFollowedUserIds(me);
        List<UUID> memberCommunityIds = CommunityMembership.listCommunityIdsByUser(currentUser);
        List<Integer> interestTypeIds = me.interestTypes.stream().map(interest -> interest.id).toList();
        List<Integer> communityCategoryIds = CommunityMembership.listCategoryIdsByUser(currentUser);

        HomeFeedRankingPolicy.Context context = new HomeFeedRankingPolicy.Context(
                currentUser.id,
                followedUserIds,
                memberCommunityIds,
                interestTypeIds,
                communityCategoryIds,
                hiddenAuthorUserIds(currentUser)
        );

        // size + 1 para saber hasNext sem COUNT (padrão de feed infinito).
        int offset = resolvedPage * resolvedSize;
        List<HomeFeedRankingPolicy.RankedPost> ranked =
                HomeFeedRankingPolicy.rank(Post.getEntityManager(), context, offset, resolvedSize + 1);
        boolean hasNext = ranked.size() > resolvedSize;
        List<HomeFeedRankingPolicy.RankedPost> pageRows = ranked.stream().limit(resolvedSize).toList();

        Map<UUID, FeedSource> sources = new HashMap<>();
        for (HomeFeedRankingPolicy.RankedPost row : pageRows) {
            sources.put(row.postId(), row.source());
        }
        List<Post> posts = Post.listActiveByIdsInOrder(pageRows.stream().map(HomeFeedRankingPolicy.RankedPost::postId).toList());

        return new UserFeedPageResponse(toPostResponses(posts, currentUser, sources), resolvedPage, resolvedSize, hasNext);
    }

    /**
     * Autores que nunca devem aparecer no feed de {@code viewer}. Hoje ninguém:
     * todos os perfis são públicos. Semana 04 (bloqueio / perfil privado)
     * pluga aqui — ponto único, junto de
     * {@link HomeFeedRankingPolicy#authorVisibilityFilter()}.
     */
    private List<UUID> hiddenAuthorUserIds(User viewer) {
        return List.of();
    }

    // ------------------------------------------------------ likes / comments

    @Override
    @Transactional
    public UserPostLikeResponse likePost(User currentUser, UUID postId) {
        requireUserProfile(currentUser);
        Post post = requireActivePersonalPost(postId);

        // Idempotente: toques repetidos não duplicam a curtida.
        if (PostLike.findByPostAndUser(post, currentUser) == null) {
            PostLike like = new PostLike();
            like.id = UUIDv7Generator.generate();
            like.post = post;
            like.user = currentUser;
            like.createdAt = Instant.now();
            like.persist();
        }

        return new UserPostLikeResponse(post.id, PostLike.countByPost(post), true);
    }

    @Override
    @Transactional
    public UserPostLikeResponse unlikePost(User currentUser, UUID postId) {
        requireUserProfile(currentUser);
        Post post = requireActivePersonalPost(postId);

        PostLike existing = PostLike.findByPostAndUser(post, currentUser);
        if (existing != null) {
            existing.delete();
        }

        return new UserPostLikeResponse(post.id, PostLike.countByPost(post), false);
    }

    @Override
    public UserPostCommentPageResponse getComments(User currentUser, UUID postId, Integer page, Integer size) {
        Post post = requireActivePersonalPost(postId);
        int resolvedPage = PageParams.resolvePage(page);
        int resolvedSize = PageParams.resolveSize(size);

        PanacheQuery<PostComment> query = PostComment.queryByPost(post);
        long total = query.count();
        List<UserPostCommentResponse> comments = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(comment -> toCommentResponse(comment, currentUser))
                .toList();

        PageResponse<UserPostCommentResponse> envelope = PageResponse.of(comments, resolvedPage, resolvedSize, total);
        return new UserPostCommentPageResponse(
                envelope.content(),
                envelope.page(),
                envelope.size(),
                envelope.totalElements(),
                envelope.hasNext()
        );
    }

    @Override
    @Transactional
    public UserPostCommentResponse createComment(User currentUser, UUID postId, String body) {
        requireUserProfile(currentUser);
        Post post = requireActivePersonalPost(postId);

        PostComment comment = new PostComment();
        comment.id = UUIDv7Generator.generate();
        comment.post = post;
        comment.author = currentUser;
        comment.body = requireCommentBody(body);
        comment.createdAt = Instant.now();
        comment.persist();

        return toCommentResponse(comment, currentUser);
    }

    @Override
    @Transactional
    public void deleteComment(User currentUser, UUID postId, UUID commentId) {
        Post post = requireActivePersonalPost(postId);

        if (commentId == null) {
            throw new IllegalArgumentException(COMMENT_NOT_FOUND_MESSAGE);
        }
        PostComment comment = PostComment.findByIdAndPost(commentId, post);
        if (comment == null) {
            throw new NoSuchElementException(COMMENT_NOT_FOUND_MESSAGE);
        }

        boolean commentAuthor = comment.author.id.equals(currentUser.id);
        boolean postAuthor = post.author.id.equals(currentUser.id);
        if (!commentAuthor && !postAuthor) {
            throw new ForbiddenException(COMMENT_DELETE_FORBIDDEN_MESSAGE);
        }

        comment.delete();
    }

    @Override
    public byte[] getPostMedia(User currentUser, UUID postId) {
        Post post = requireActivePersonalPost(postId);
        if (post.mediaOid == null) {
            throw new NoSuchElementException(POST_MEDIA_NOT_FOUND_MESSAGE);
        }
        return oidImageService.readOidBlob(post.mediaOid);
    }

    @Override
    public UserFeedPageResponse listPostsByProfile(User currentUser, UUID userProfileId, Integer page, Integer size) {
        UserProfile target = requireTargetProfile(userProfileId);
        int resolvedPage = PageParams.resolvePage(page);
        int resolvedSize = PageParams.resolveSize(size);

        return toFeedPage(Post.queryPersonalByAuthor(target.user), currentUser, resolvedPage, resolvedSize);
    }

    // ------------------------------------------------------------ helpers

    /**
     * Busca {@code size + 1} e corta o último para saber {@code hasNext} sem um
     * COUNT por página (padrão de feed infinito).
     */
    private UserFeedPageResponse toFeedPage(PanacheQuery<Post> query, User currentUser, int page, int size) {
        List<Post> rows = query.range(page * size, page * size + size).list();
        boolean hasNext = rows.size() > size;
        List<Post> pageRows = rows.stream().limit(size).toList();

        return new UserFeedPageResponse(toPostResponses(pageRows, currentUser, Map.of()), page, size, hasNext);
    }

    /**
     * Contadores da página inteira em quatro queries (likes, comentários,
     * "curti", "comentei") em vez de quatro por post.
     */
    private List<UserPostResponse> toPostResponses(List<Post> posts, User currentUser, Map<UUID, FeedSource> sources) {
        List<UUID> postIds = posts.stream().map(post -> post.id).toList();
        Map<UUID, Long> likeCounts = PostLike.countByPostIds(postIds);
        Map<UUID, Long> commentCounts = PostComment.countByPostIds(postIds);
        Set<UUID> likedIds = PostLike.listPostIdsByUser(currentUser, postIds);
        Set<UUID> commentedIds = PostComment.listPostIdsByUser(currentUser, postIds);
        // Autores repetem muito numa página: resolve cada um uma vez só.
        Map<UUID, UserPostAuthorResponse> authors = new HashMap<>();

        return posts.stream()
                .map(post -> toPostResponse(
                        post,
                        authors.computeIfAbsent(post.author.id, ignored -> toAuthorResponse(post.author)),
                        likeCounts.getOrDefault(post.id, 0L),
                        commentCounts.getOrDefault(post.id, 0L),
                        likedIds.contains(post.id),
                        commentedIds.contains(post.id),
                        sources.get(post.id)
                ))
                .toList();
    }

    private FollowPageResponse toFollowPage(List<FollowedProfileSummaryResponse> profiles, int page, int size, long total) {
        PageResponse<FollowedProfileSummaryResponse> envelope = PageResponse.of(profiles, page, size, total);
        return new FollowPageResponse(
                envelope.content(),
                envelope.page(),
                envelope.size(),
                envelope.totalElements(),
                envelope.totalPages(),
                envelope.hasNext()
        );
    }

    private FollowActionResponse toActionResponse(UserProfile follower, UserProfile followed, boolean following) {
        return new FollowActionResponse(
                followed.id,
                following,
                UserFollow.countFollowers(followed),
                UserFollow.countFollowing(follower)
        );
    }

    /** Versão de um post só (criação/edição): contadores direto do banco. */
    UserPostResponse toPostResponse(Post post, User currentUser) {
        return toPostResponse(
                post,
                toAuthorResponse(post.author),
                PostLike.countByPost(post),
                PostComment.countByPost(post),
                currentUser != null && PostLike.findByPostAndUser(post, currentUser) != null,
                currentUser != null && PostComment.existsByPostAndUser(post, currentUser),
                null
        );
    }

    private UserPostResponse toPostResponse(
            Post post,
            UserPostAuthorResponse author,
            long likesCount,
            long commentsCount,
            boolean likedByCurrentUser,
            boolean commentedByCurrentUser,
            FeedSource feedSource
    ) {
        boolean communityPost = post.origin == PostOrigin.COMMUNITY && post.community != null;
        String mediaUrl = post.mediaOid == null
                ? null
                : (communityPost ? COMMUNITY_POST_MEDIA_URL_PREFIX : POST_MEDIA_URL_PREFIX) + post.id + POST_MEDIA_URL_SUFFIX;

        return new UserPostResponse(
                post.id,
                post.origin,
                communityPost ? toCommunityResponse(post.community) : null,
                author,
                post.body,
                mediaUrl,
                post.createdAt,
                post.editedAt,
                likesCount,
                commentsCount,
                likedByCurrentUser,
                commentedByCurrentUser,
                feedSource
        );
    }

    private UserPostCommunityResponse toCommunityResponse(Community community) {
        return new UserPostCommunityResponse(
                community.id,
                community.name,
                community.iconOid == null ? null : COMMUNITY_ICON_URL_PREFIX + community.id + COMMUNITY_ICON_URL_SUFFIX
        );
    }

    private UserPostCommentResponse toCommentResponse(PostComment comment, User currentUser) {
        return new UserPostCommentResponse(
                comment.id,
                toAuthorResponse(comment.author),
                comment.body,
                comment.createdAt,
                currentUser != null && comment.author.id.equals(currentUser.id)
        );
    }

    private UserPostAuthorResponse toAuthorResponse(User author) {
        UserProfile profile = UserProfile.findByUser(author);
        return new UserPostAuthorResponse(
                profile == null ? null : profile.id,
                author.id,
                buildDisplayName(author),
                resolveAvatarUrl(profile)
        );
    }

    private FollowedProfileSummaryResponse toSummary(UserProfile profile, boolean followedByCurrentUser) {
        return new FollowedProfileSummaryResponse(
                profile.id,
                profile.user.id,
                buildDisplayName(profile.user),
                resolveAvatarUrl(profile),
                followedByCurrentUser
        );
    }

    private String resolveAvatarUrl(UserProfile profile) {
        if (profile == null || UserProfileImage.findActiveProfilePicture(profile) == null) {
            return null;
        }
        return AVATAR_URL_PREFIX + profile.user.id + AVATAR_URL_SUFFIX;
    }

    static String buildDisplayName(User user) {
        String first = user.name == null ? "" : user.name.trim();
        String last = user.lastName == null ? "" : user.lastName.trim();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return user.email != null ? user.email : user.id.toString();
    }

    private UserProfile requireUserProfile(User user) {
        UserProfile profile = resolveUserProfile(user);
        if (profile == null) {
            throw new IllegalStateException(PROFILE_REQUIRED_MESSAGE);
        }
        return profile;
    }

    private UserProfile resolveUserProfile(User user) {
        return user == null ? null : UserProfile.findByUser(user);
    }

    private UserProfile requireTargetProfile(UUID userProfileId) {
        if (userProfileId == null) {
            throw new IllegalArgumentException(TARGET_PROFILE_REQUIRED_MESSAGE);
        }
        UserProfile profile = UserProfile.findById(userProfileId);
        if (profile == null) {
            throw new NoSuchElementException(TARGET_PROFILE_NOT_FOUND_MESSAGE);
        }
        return profile;
    }

    private Post requireActivePersonalPost(UUID postId) {
        if (postId == null) {
            throw new IllegalArgumentException(POST_NOT_FOUND_MESSAGE);
        }
        Post post = Post.findActivePersonalById(postId);
        if (post == null) {
            throw new NoSuchElementException(POST_NOT_FOUND_MESSAGE);
        }
        return post;
    }

    static String requireBody(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(POST_BODY_REQUIRED_MESSAGE);
        }
        String trimmed = value.trim();
        if (trimmed.length() > POST_BODY_MAX_LENGTH) {
            throw new IllegalArgumentException(POST_BODY_TOO_LONG_MESSAGE);
        }
        return trimmed;
    }

    static String requireCommentBody(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(COMMENT_BODY_REQUIRED_MESSAGE);
        }
        String trimmed = value.trim();
        if (trimmed.length() > COMMENT_BODY_MAX_LENGTH) {
            throw new IllegalArgumentException(COMMENT_BODY_TOO_LONG_MESSAGE);
        }
        return trimmed;
    }
}
