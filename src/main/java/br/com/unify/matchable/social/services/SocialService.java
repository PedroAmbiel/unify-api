package br.com.unify.matchable.social.services;

import java.util.UUID;

import br.com.unify.matchable.social.dto.FollowActionResponse;
import br.com.unify.matchable.social.dto.FollowPageResponse;
import br.com.unify.matchable.social.dto.FollowStatsResponse;
import br.com.unify.matchable.social.dto.UserFeedPageResponse;
import br.com.unify.matchable.social.dto.UserPostCommentPageResponse;
import br.com.unify.matchable.social.dto.UserPostCommentResponse;
import br.com.unify.matchable.social.dto.UserPostLikeResponse;
import br.com.unify.matchable.social.dto.UserPostResponse;
import br.com.unify.matchable.user.entity.User;

public interface SocialService {

    FollowActionResponse follow(User currentUser, UUID targetUserProfileId);

    FollowActionResponse unfollow(User currentUser, UUID targetUserProfileId);

    FollowPageResponse listFollowing(User currentUser, Integer page, Integer size);

    FollowPageResponse listFollowers(User currentUser, Integer page, Integer size);

    FollowStatsResponse getFollowStats(User currentUser, UUID userProfileId);

    UserPostResponse createPost(User author, String body, byte[] imageBytes);

    /** Só o autor edita, e só o texto; marca {@code editedAt}. */
    UserPostResponse updatePost(User currentUser, UUID postId, String body);

    void deletePost(User currentUser, UUID postId);

    /**
     * Feed ranqueado da aba Início (ver {@code HomeFeedRankingPolicy}): posts de
     * quem eu sigo e das comunidades em que sou membro, misturados com sugestões
     * de perfis com interesses parecidos e de comunidades PÚBLICAS afins. Cada
     * item traz {@code feedSource}. Meus posts pessoais NÃO entram (ficam no perfil).
     */
    UserFeedPageResponse getFeed(User currentUser, Integer page, Integer size);

    UserPostLikeResponse likePost(User currentUser, UUID postId);

    UserPostLikeResponse unlikePost(User currentUser, UUID postId);

    UserPostCommentPageResponse getComments(User currentUser, UUID postId, Integer page, Integer size);

    UserPostCommentResponse createComment(User currentUser, UUID postId, String body);

    /** Autor do comentário ou autor do post. */
    void deleteComment(User currentUser, UUID postId, UUID commentId);

    byte[] getPostMedia(User currentUser, UUID postId);

    UserFeedPageResponse listPostsByProfile(User currentUser, UUID userProfileId, Integer page, Integer size);
}
