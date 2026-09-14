package br.com.unify.matchable.post.entity;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Contagem de curtidas de uma página de posts em UMA query (sem N+1). */
    public static Map<UUID, Long> countByPostIds(Collection<UUID> postIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return counts;
        }
        List<Object[]> rows = getEntityManager()
                .createQuery("select l.post.id, count(l) from PostLike l where l.post.id in :ids group by l.post.id", Object[].class)
                .setParameter("ids", postIds)
                .getResultList();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** Ids (dentro de {@code postIds}) que o usuário curtiu, em UMA query. */
    public static Set<UUID> listPostIdsByUser(User user, Collection<UUID> postIds) {
        if (user == null || postIds == null || postIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(getEntityManager()
                .createQuery("select l.post.id from PostLike l where l.user = :user and l.post.id in :ids", UUID.class)
                .setParameter("user", user)
                .setParameter("ids", postIds)
                .getResultList());
    }
}