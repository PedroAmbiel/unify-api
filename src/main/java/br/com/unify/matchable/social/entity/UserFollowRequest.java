package br.com.unify.matchable.social.entity;

import java.time.Instant;
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

/**
 * Pedido para seguir uma conta que exige aprovação. Migration:
 * V19__follow_requests.sql. Só existe enquanto está pendente: aceitar vira
 * {@link UserFollow} e apaga o pedido; recusar/cancelar apaga o pedido.
 */
@Entity
@Table(
        name = "user_follow_requests",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_follow_requests_pair", columnNames = { "fk_requester", "fk_target" })
)
@Check(constraints = "fk_requester <> fk_target")
public class UserFollowRequest extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_requester", nullable = false, foreignKey = @ForeignKey(name = "fk_user_follow_requests_requester"))
    public UserProfile requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target", nullable = false, foreignKey = @ForeignKey(name = "fk_user_follow_requests_target"))
    public UserProfile target;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static UserFollowRequest findPair(UserProfile requester, UserProfile target) {
        return find("requester = ?1 and target = ?2", requester, target).firstResult();
    }

    public static PanacheQuery<UserFollowRequest> queryReceived(UserProfile target) {
        return find("target = ?1 order by createdAt desc, id desc", target);
    }

    public static PanacheQuery<UserFollowRequest> querySent(UserProfile requester) {
        return find("requester = ?1 order by createdAt desc, id desc", requester);
    }

    public static long countReceived(UserProfile target) {
        return count("target", target);
    }

    public static long deleteBetween(UserProfile first, UserProfile second) {
        return delete(
                "(requester = ?1 and target = ?2) or (requester = ?2 and target = ?1)",
                first, second
        );
    }
}
