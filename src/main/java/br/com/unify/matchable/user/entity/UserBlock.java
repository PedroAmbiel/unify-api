package br.com.unify.matchable.user.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

/**
 * Bloqueio entre usuários. Migration: V16__create_user_blocks.sql.
 *
 * <p>A linha é direcional (quem bloqueou quem, para a tela "Pessoas
 * bloqueadas"), mas o EFEITO é simétrico: discovery, perfil público, feed,
 * follow e chat tratam o par como bloqueado em qualquer direção. Modelado por
 * {@link User} (não {@link UserProfile}) porque o autor de post e o dono da
 * privacidade já são {@code User}.
 */
@Entity
@Table(
        name = "user_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_blocks_blocker_blocked", columnNames = { "fk_blocker", "fk_blocked" })
)
@Check(constraints = "fk_blocker <> fk_blocked")
public class UserBlock extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_blocker", nullable = false, foreignKey = @ForeignKey(name = "fk_user_blocks_blocker"))
    public User blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_blocked", nullable = false, foreignKey = @ForeignKey(name = "fk_user_blocks_blocked"))
    public User blocked;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static UserBlock findByBlockerAndBlocked(User blocker, User blocked) {
        return find("blocker = ?1 and blocked = ?2", blocker, blocked).firstResult();
    }

    public static boolean existsEitherDirection(UUID userIdA, UUID userIdB) {
        if (userIdA == null || userIdB == null) {
            return false;
        }
        return count(
                "(blocker.id = ?1 and blocked.id = ?2) or (blocker.id = ?2 and blocked.id = ?1)",
                userIdA, userIdB
        ) > 0;
    }

    public static List<UserBlock> listByBlocker(User blocker) {
        return list("blocker = ?1 order by createdAt desc, id desc", blocker);
    }

    /**
     * {@code users.id} de todos que eu bloqueei OU que me bloquearam. Duas
     * projeções simples em vez de um {@code case when} em HQL (sintaxe frágil
     * entre versões do Hibernate).
     */
    public static Set<UUID> listRelatedUserIds(UUID userId) {
        Set<UUID> related = new LinkedHashSet<>();
        if (userId == null) {
            return related;
        }
        related.addAll(find("select b.blocked.id from UserBlock b where b.blocker.id = ?1", userId)
                .project(UUID.class)
                .list());
        related.addAll(find("select b.blocker.id from UserBlock b where b.blocked.id = ?1", userId)
                .project(UUID.class)
                .list());
        return related;
    }
}
