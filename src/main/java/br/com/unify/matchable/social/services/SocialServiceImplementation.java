package br.com.unify.matchable.social.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.dto.PageParams;
import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.common.exceptions.ForbiddenException;
import br.com.unify.matchable.common.image.OidImageService;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.social.dto.FollowActionResponse;
import br.com.unify.matchable.social.dto.FollowPageResponse;
import br.com.unify.matchable.social.dto.FollowStatsResponse;
import br.com.unify.matchable.social.dto.FollowedProfileSummaryResponse;
import br.com.unify.matchable.social.dto.UserFeedPageResponse;
import br.com.unify.matchable.social.dto.UserPostAuthorResponse;
import br.com.unify.matchable.social.dto.UserPostResponse;
import br.com.unify.matchable.social.entity.UserFollow;
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

    static final int POST_BODY_MAX_LENGTH = 4000;

    /**
     * Reaproveita o endpoint autenticado de avatar já existente
     * ({@code GET /communities/users/{userId}/avatar}), que resolve a foto de
     * perfil ativa a partir do User — sem duplicar a lógica de imagem aqui.
     */
    static final String AVATAR_URL_PREFIX = "/communities/users/";
    static final String AVATAR_URL_SUFFIX = "/avatar";
    static final String POST_MEDIA_URL_PREFIX = "/users/posts/";
    static final String POST_MEDIA_URL_SUFFIX = "/media";

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

        return toPostResponse(post);
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

        // Seguidos + eu. Sem seguir ninguém o feed mostra só os próprios posts (não é erro).
        List<UUID> authorUserIds = new ArrayList<>(UserFollow.listFollowedUserIds(me));
        authorUserIds.add(currentUser.id);

        return toFeedPage(Post.queryPersonalByAuthorIds(authorUserIds), resolvedPage, resolvedSize);
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

        return toFeedPage(Post.queryPersonalByAuthor(target.user), resolvedPage, resolvedSize);
    }

    // ------------------------------------------------------------ helpers

    /**
     * Busca {@code size + 1} e corta o último para saber {@code hasNext} sem um
     * COUNT por página (padrão de feed infinito).
     */
    private UserFeedPageResponse toFeedPage(PanacheQuery<Post> query, int page, int size) {
        List<Post> rows = query.range(page * size, page * size + size).list();
        boolean hasNext = rows.size() > size;
        List<UserPostResponse> posts = rows.stream()
                .limit(size)
                .map(this::toPostResponse)
                .toList();

        return new UserFeedPageResponse(posts, page, size, hasNext);
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

    UserPostResponse toPostResponse(Post post) {
        return new UserPostResponse(
                post.id,
                toAuthorResponse(post.author),
                post.body,
                post.mediaOid == null ? null : POST_MEDIA_URL_PREFIX + post.id + POST_MEDIA_URL_SUFFIX,
                post.createdAt
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
}
