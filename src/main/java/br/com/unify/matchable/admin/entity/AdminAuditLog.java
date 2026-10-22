package br.com.unify.matchable.admin.entity;

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

/** Trilha de auditoria do backoffice (V32). Só inserção: nunca editada. */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_admin_user", nullable = false, foreignKey = @ForeignKey(name = "fk_admin_audit_log_admin_user"))
    public User adminUser;

    @Column(name = "action", nullable = false, length = 60)
    public String action;

    @Column(name = "target_type", nullable = false, length = 40)
    public String targetType;

    @Column(name = "target_id", nullable = false, length = 80)
    public String targetId;

    @Column(name = "details", length = 1000)
    public String details;

    @Column(name = "ip_address", length = 64)
    public String ipAddress;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
