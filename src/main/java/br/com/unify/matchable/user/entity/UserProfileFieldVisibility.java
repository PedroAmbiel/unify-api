package br.com.unify.matchable.user.entity;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import br.com.unify.matchable.user.enums.ProfileField;
import br.com.unify.matchable.user.enums.ProfileFieldVisibility;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Visibilidade de uma parte do perfil. Migration:
 * V18__create_user_profile_field_visibilities.sql.
 *
 * <p>Só existem linhas para partes que NÃO são {@code PUBLIC}: voltar para
 * público apaga a linha. Sem linha = público, então usuários antigos não
 * precisam de migração de dados e a leitura do perfil é uma query só.
 */
@Entity
@Table(
        name = "user_profile_field_visibilities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_profile_field_visibilities_user_field",
                columnNames = { "fk_user", "field" }
        )
)
public class UserProfileFieldVisibility extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_user", nullable = false, foreignKey = @ForeignKey(name = "fk_user_profile_field_visibilities_user"))
    public User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 40)
    public ProfileField field;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    public ProfileFieldVisibility visibility;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    public static UserProfileFieldVisibility findByUserAndField(User user, ProfileField field) {
        return find("user = ?1 and field = ?2", user, field).firstResult();
    }

    /** Mapa completo (todas as partes), com PUBLIC onde não há linha. Uma query. */
    public static Map<ProfileField, ProfileFieldVisibility> resolveAll(User user) {
        Map<ProfileField, ProfileFieldVisibility> resolved = new EnumMap<>(ProfileField.class);
        for (ProfileField field : ProfileField.values()) {
            resolved.put(field, ProfileFieldVisibility.PUBLIC);
        }
        if (user == null) {
            return resolved;
        }
        UserProfileFieldVisibility.<UserProfileFieldVisibility>list("user", user)
                .forEach(row -> resolved.put(row.field, row.visibility));
        return resolved;
    }
}
