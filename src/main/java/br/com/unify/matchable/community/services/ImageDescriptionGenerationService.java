package br.com.unify.matchable.community.services;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.post.entity.PostComment;
import br.com.unify.matchable.social.services.SocialService;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * On-demand AI description, generated once and reused: the first result is stored in
 * {@code image_description} and later requests never reach the provider.
 * Authorization and Blob loading finish before the outbound call; saving runs in its own short transaction.
 */
@ApplicationScoped
public class ImageDescriptionGenerationService {
    @Inject CommunityService communityService;
    @Inject SocialService socialService;
    @Inject ImageVisionService vision;

    /** {@code generated} is false when a stored (author or earlier AI) description was returned. */
    public record Result(String description, boolean generated) {}

    /** What was read under authorization; {@code editedAt} detects an image swap during analysis. */
    protected record Snapshot(byte[] image, String saved, Instant editedAt) {}

    public Result describeCommunityPost(User user, UUID postId) {
        return describe(() -> postSnapshot(postId, communityService.getPostMedia(user, postId)),
            (snapshot, description) -> savePostDescription(postId, snapshot, description));
    }

    public Result describePersonalPost(User user, UUID postId) {
        return describe(() -> postSnapshot(postId, socialService.getPostMedia(user, postId)),
            (snapshot, description) -> savePostDescription(postId, snapshot, description));
    }

    public Result describeComment(User user, UUID postId, UUID commentId) {
        return describe(() -> {
            byte[] image = communityService.getCommentMedia(user, postId, commentId);
            PostComment comment = PostComment.findById(commentId);
            return new Snapshot(image, comment == null ? null : comment.imageDescription, null);
        }, (snapshot, description) -> saveCommentDescription(commentId, description));
    }

    protected Result describe(Supplier<Snapshot> load, DescriptionWriter save) {
        Snapshot snapshot = inNewTransaction(load);
        if (snapshot.saved() != null && !snapshot.saved().isBlank())
            return new Result(snapshot.saved().trim(), false);
        String description = vision.describe(snapshot.image());
        return new Result(inNewTransaction(() -> save.write(snapshot, description)), true);
    }

    protected <T> T inNewTransaction(Supplier<T> work) {
        return QuarkusTransaction.requiringNew().call(work::get);
    }

    private Snapshot postSnapshot(UUID postId, byte[] image) {
        Post post = Post.findById(postId);
        return new Snapshot(image, post == null ? null : post.imageDescription, post == null ? null : post.editedAt);
    }

    private String savePostDescription(UUID postId, Snapshot snapshot, String description) {
        Post post = Post.findById(postId);
        if (post == null || post.mediaOid == null) return description;
        // Another request may have finished first; keep a single stored text.
        if (post.imageDescription != null && !post.imageDescription.isBlank()) return post.imageDescription;
        // An edit during analysis may have replaced the image: never attach the old description to it.
        if (Objects.equals(post.editedAt, snapshot.editedAt())) post.imageDescription = description;
        return description;
    }

    private String saveCommentDescription(UUID commentId, String description) {
        PostComment comment = PostComment.findById(commentId);
        if (comment == null || comment.mediaOid == null) return description;
        if (comment.imageDescription != null && !comment.imageDescription.isBlank()) return comment.imageDescription;
        comment.imageDescription = description;
        return description;
    }

    @FunctionalInterface
    protected interface DescriptionWriter {
        String write(Snapshot snapshot, String description);
    }
}
