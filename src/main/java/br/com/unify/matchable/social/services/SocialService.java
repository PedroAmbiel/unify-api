package br.com.unify.matchable.social.services;

import java.util.UUID;

import br.com.unify.matchable.social.dto.FollowActionResponse;
import br.com.unify.matchable.social.dto.FollowPageResponse;
import br.com.unify.matchable.social.dto.FollowStatsResponse;
import br.com.unify.matchable.social.dto.UserFeedPageResponse;
import br.com.unify.matchable.social.dto.UserPostResponse;
import br.com.unify.matchable.user.entity.User;

public interface SocialService {

    FollowActionResponse follow(User currentUser, UUID targetUserProfileId);

    FollowActionResponse unfollow(User currentUser, UUID targetUserProfileId);

    FollowPageResponse listFollowing(User currentUser, Integer page, Integer size);

    FollowPageResponse listFollowers(User currentUser, Integer page, Integer size);

    FollowStatsResponse getFollowStats(User currentUser, UUID userProfileId);

    UserPostResponse createPost(User author, String body, byte[] imageBytes);

    void deletePost(User currentUser, UUID postId);

    UserFeedPageResponse getFeed(User currentUser, Integer page, Integer size);

    byte[] getPostMedia(User currentUser, UUID postId);

    UserFeedPageResponse listPostsByProfile(User currentUser, UUID userProfileId, Integer page, Integer size);
}
