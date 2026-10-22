package br.com.unify.matchable.user.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

import br.com.unify.matchable.auth.entity.ActiveConnection;
import br.com.unify.matchable.user.enums.UserRole;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
// Espelha ck_users_ban_consistency (V28) no schema que o Hibernate gera em %dev/%test.
@Check(name = "ck_users_ban_consistency", constraints = "(banned = false and banned_at is null and ban_reason is null) "
        + "or (banned = true and banned_at is not null and ban_reason is not null)")
public class User extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "last_name", nullable = false)
    public String lastName;

    @Column(name = "email", unique = true, nullable = false)
    public String email;

    @Column(name = "cellphone", unique = true)
    public String cellphone;

    @Column(name = "password", nullable = false)
    public String password;

    @Column(name = "birthdate", nullable = false)
    public LocalDate birthdate;

    @Column(name = "verified", nullable = false)
    public boolean verified;

    @Column(name = "last_updated_at", nullable = false)
    public Instant lastUpdatedAt;

    /**
     * Semana 06 (V27). Só muda por configuração (AdminBootstrapService) ou SQL
     * direto: nenhum endpoint escreve aqui. O default no banco mantém os seeds
     * e o cadastro funcionando sem informar o papel.
     */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'USER'")
    @Column(name = "role", nullable = false, length = 20)
    public UserRole role = UserRole.USER;

    /** Semana 06 (V28): conta suspensa pelo backoffice. Ver ck_users_ban_consistency. */
    @ColumnDefault("false")
    @Column(name = "banned", nullable = false)
    public boolean banned;

    @Column(name = "banned_at")
    public Instant bannedAt;

    @Column(name = "ban_reason", length = 500)
    public String banReason;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    public UserProfile profile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<ActiveConnection> activeConnections;

    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
