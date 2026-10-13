package br.com.unify.matchable.notification.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import br.com.unify.matchable.notification.enums.PushPlatform;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Token de push (FCM) de um aparelho. Migration:
 * V21__create_notifications_and_push_tokens.sql.
 *
 * <p>O token identifica o APARELHO, não a pessoa: é único no banco e, quando
 * outra conta loga no mesmo aparelho, a linha troca de dono (ver
 * {@code NotificationServiceImplementation.registerPushToken}).
 */
@Entity
@Table(
        name = "user_push_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_push_tokens_token", columnNames = "token"),
        indexes = @Index(name = "idx_user_push_tokens_user", columnList = "fk_user")
)
public class UserPushToken extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_user", nullable = false, foreignKey = @ForeignKey(name = "fk_user_push_tokens_user"))
    public User user;

    @Column(name = "token", nullable = false, length = 512)
    public String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    public PushPlatform platform;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    public static UserPushToken findByToken(String token) {
        return find("token", token).firstResult();
    }

    public static UserPushToken findByIdAndUser(UUID id, User user) {
        return find("id = ?1 and user = ?2", id, user).firstResult();
    }

    public static List<UserPushToken> listByUser(User user) {
        return list("user = ?1 order by updatedAt desc", user);
    }

    public static List<UserPushToken> listByUserId(UUID userId) {
        return list("user.id = ?1 order by updatedAt desc", userId);
    }
}
