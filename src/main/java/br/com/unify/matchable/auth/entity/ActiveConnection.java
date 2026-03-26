package br.com.unify.matchable.auth.entity;

import java.time.Instant;
import java.util.List;
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

@Entity
@Table(name = "active_connections")
public class ActiveConnection extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false)
    public User user;

    @Column(name = "refresh_token", nullable = false, unique = true)
    public String refreshToken;

    @Column(name = "device_info")
    public String deviceInfo;

    @Column(name = "ip_address")
    public String ipAddress;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    public boolean revoked;

    public static List<ActiveConnection> findByUser(User user) {
        return find("user = ?1 and revoked = false", user).list();
    }

    public static ActiveConnection findByRefreshTokenHash(String hash) {
        return find("refreshTokenHash = ?1 and revoked = false", hash).firstResult();
    }

    public static void revokeAllForUser(User user) {
        update("revoked = true where user = ?1 and revoked = false", user);
    }
}
