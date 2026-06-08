package br.com.unify.matchable.community.entity;

import java.time.Instant;
import java.util.List;
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

@Entity
@Table(name = "community_post_comments")
public class CommunityPostComment extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_post", nullable = false, foreignKey = @ForeignKey(name = "fk_community_post_comments_post"))
    public CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false, foreignKey = @ForeignKey(name = "fk_community_post_comments_user"))
    public User author;

    @Column(name = "body", nullable = false, length = 2000)
    public String body;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static List<CommunityPostComment> listByPost(CommunityPost post) {
        return list("post = ?1 order by createdAt asc, id asc", post);
    }

    public static long countByPost(CommunityPost post) {
        return count("post", post);
    }

    public static boolean existsByPostAndUser(CommunityPost post, User user) {
        return count("post = ?1 and author = ?2", post, user) > 0;
    }

    public static CommunityPostComment findByIdAndPost(UUID id, CommunityPost post) {
        return find("id = ?1 and post = ?2", id, post).firstResult();
    }
}