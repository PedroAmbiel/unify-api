package br.com.unify.matchable.community.services;

import java.util.UUID;

import br.com.unify.matchable.community.dto.CommunityCommentResponse;
import br.com.unify.matchable.community.dto.CommunityCommentsResponse;
import br.com.unify.matchable.community.dto.CommunityFeedResponse;
import br.com.unify.matchable.community.dto.CommunityLikeResponse;
import br.com.unify.matchable.community.dto.CommunityMemberResponse;
import br.com.unify.matchable.community.dto.CommunityMembersResponse;
import br.com.unify.matchable.community.dto.CommunityMembershipResponse;
import br.com.unify.matchable.community.dto.CommunityPageResponse;
import br.com.unify.matchable.community.dto.CommunityPostResponse;
import br.com.unify.matchable.community.dto.CommunitySummaryResponse;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.user.entity.User;

public interface CommunityService {

    CommunityPageResponse listCommunities(User user, Integer page, Integer size);

    CommunityPageResponse searchCommunities(User user, String query, Integer page, Integer size);

    CommunitySummaryResponse createCommunity(User user, String name, String description, byte[] iconBytes);

    void deleteCommunity(User user, UUID communityId);

    CommunityFeedResponse getFeed(User user, UUID communityId);

    CommunityMembershipResponse joinCommunity(User user, UUID communityId);

    CommunityMembershipResponse leaveCommunity(User user, UUID communityId);

    CommunityMembersResponse listMembers(User user, UUID communityId);

    CommunityMemberResponse updateMemberRole(User user, UUID communityId, UUID targetUserProfileId, CommunityMemberRole role);

    CommunityPostResponse createPost(User user, UUID communityId, String body, byte[] imageBytes);

    void deletePost(User user, UUID postId);

    CommunityLikeResponse likePost(User user, UUID postId);

    CommunityLikeResponse unlikePost(User user, UUID postId);

    CommunityLikeResponse deleteLike(User user, UUID postId, UUID targetUserId);

    CommunityCommentsResponse getComments(User user, UUID postId);

    CommunityCommentResponse createComment(User user, UUID postId, String body);

    void deleteComment(User user, UUID postId, UUID commentId);

    byte[] getCommunityIcon(UUID communityId);

    byte[] getPostMedia(UUID postId);

    byte[] getAuthorAvatar(UUID userId);
}