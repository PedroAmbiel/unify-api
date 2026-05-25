package br.com.unify.matchable.community.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "community_post_likes",
        uniqueConstraints = @UniqueConstraint(name = "uq_community_post_like_post_user", columnNames = { "fk_post", "fk_user" })
)
public class CommunityPostLike extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_post", nullable = false)
    public CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false)
    public User user;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static CommunityPostLike findByPostAndUser(CommunityPost post, User user) {
        return find("post = ?1 and user = ?2", post, user).firstResult();
    }

    public static long countByPost(CommunityPost post) {
        return count("post", post);
    }
}