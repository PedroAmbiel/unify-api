package br.com.unify.matchable.user.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.unify.matchable.auth.entity.ActiveConnection;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
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

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    public UserProfile profile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<ActiveConnection> activeConnections;

    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }
}
