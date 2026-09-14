package br.com.unify.matchable.post.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "post_likes",
        uniqueConstraints = @UniqueConstraint(name = "uq_post_likes_post_user", columnNames = { "fk_post", "fk_user" })
)
public class PostLike extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_post", nullable = false, foreignKey = @ForeignKey(name = "fk_post_likes_post"))
    public Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false, foreignKey = @ForeignKey(name = "fk_post_likes_user"))
    public User user;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static PostLike findByPostAndUser(Post post, User user) {
        return find("post = ?1 and user = ?2", post, user).firstResult();
    }

    public static long countByPost(Post post) {
        return count("post", post);
    }
}