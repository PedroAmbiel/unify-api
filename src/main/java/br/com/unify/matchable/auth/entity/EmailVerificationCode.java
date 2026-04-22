package br.com.unify.matchable.auth.entity;

import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "email_verification_codes",
        indexes = {
                @Index(name = "idx_email_verification_user_code", columnList = "fk_user, code_hash"),
                @Index(name = "idx_email_verification_expires_at", columnList = "expires_at")
        }
)
public class EmailVerificationCode extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false)
    public User user;

    @Column(name = "code_hash", nullable = false)
    public String codeHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "disabled_at")
    public Instant disabledAt;

    @Column(name = "consumed_at")
    public Instant consumedAt;

    public static EmailVerificationCode findUsableCode(User user, String codeHash, Instant now) {
        return find(
                "user = ?1 and codeHash = ?2 and disabledAt is null and consumedAt is null and expiresAt > ?3",
                user,
                codeHash,
                now
        ).firstResult();
    }

    public static long disablePendingForUser(User user, Instant now) {
        return update(
                "disabledAt = ?1 where user = ?2 and disabledAt is null and consumedAt is null",
                now,
                user
        );
    }

        public static long deleteExpired(Instant now) {
                return delete("expiresAt <= ?1", now);
        }
}