package br.com.unify.matchable.post.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_comments")
public class PostComment extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_post", nullable = false, foreignKey = @ForeignKey(name = "fk_post_comments_post"))
    public Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false, foreignKey = @ForeignKey(name = "fk_post_comments_user"))
    public User author;

    @Column(name = "body", nullable = false, length = 2000)
    public String body;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static List<PostComment> listByPost(Post post) {
        return queryByPost(post).list();
    }

    /** Ordenacao estavel para paginacao: cronologica (indice created_at). */
    public static PanacheQuery<PostComment> queryByPost(Post post) {
        return find("post = ?1 order by createdAt asc, id asc", post);
    }

    public static long countByPost(Post post) {
        return count("post", post);
    }

    public static boolean existsByPostAndUser(Post post, User user) {
        return count("post = ?1 and author = ?2", post, user) > 0;
    }

    public static PostComment findByIdAndPost(UUID id, Post post) {
        return find("id = ?1 and post = ?2", id, post).firstResult();
    }
}