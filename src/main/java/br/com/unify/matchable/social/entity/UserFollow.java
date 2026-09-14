package br.com.unify.matchable.social.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import br.com.unify.matchable.user.entity.UserProfile;
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
import jakarta.persistence.UniqueConstraint;

/** Migration: V13__create_user_follows_and_posts.sql. */
@Entity
@Table(
        name = "user_follows",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_follows_pair", columnNames = {"fk_follower", "fk_followed"})
)
@Check(constraints = "fk_follower <> fk_followed")
public class UserFollow extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_follower", nullable = false, foreignKey = @ForeignKey(name = "fk_user_follows_follower"))
    public UserProfile follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_followed", nullable = false, foreignKey = @ForeignKey(name = "fk_user_follows_followed"))
    public UserProfile followed;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static UserFollow findPair(UserProfile follower, UserProfile followed) {
        return find("follower = ?1 and followed = ?2", follower, followed).firstResult();
    }

    public static long countFollowing(UserProfile follower) {
        return count("follower", follower);
    }

    public static long countFollowers(UserProfile followed) {
        return count("followed", followed);
    }

    public static PanacheQuery<UserFollow> queryFollowing(UserProfile follower) {
        return find("follower = ?1 order by createdAt desc, id desc", follower);
    }

    public static PanacheQuery<UserFollow> queryFollowers(UserProfile followed) {
        return find("followed = ?1 order by createdAt desc, id desc", followed);
    }

    /** Ids de {@code User} dos perfis seguidos: o autor de post é User, não UserProfile. */
    public static List<UUID> listFollowedUserIds(UserProfile follower) {
        return find("select f.followed.user.id from UserFollow f where f.follower = ?1", follower)
                .project(UUID.class)
                .list();
    }
}
