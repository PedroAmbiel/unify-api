package br.com.unify.matchable.user.entity;

import java.sql.Blob;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Áudio de apresentação do perfil (frente D, semana 04). Migration:
 * V17__create_user_profile_audios.sql.
 *
 * <p>Uma linha por perfil. Substituir = apagar a linha antiga e inserir outra
 * com id novo, para que a URL {@code /audio/{audioId}} seja imutável e
 * cacheável (mesmo raciocínio de {@link UserProfileImage}). Sem coleção em
 * {@link UserProfile}: consultar sempre pelas queries estáticas abaixo.
 */
@Entity
@Table(name = "user_profile_audios")
@Check(constraints = "duration_seconds between 1 and 60 and size_bytes > 0")
public class UserProfileAudio extends PanacheEntityBase {

    /** Teto de produto, espelhado no check da V17. */
    public static final int MAX_DURATION_SECONDS = 60;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(
            name = "fk_user_profile",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_user_profile_audios_user_profile")
    )
    public UserProfile userProfile;

    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "oid", nullable = false, columnDefinition = "oid")
    public Blob oid;

    @Column(name = "content_type", nullable = false, length = 100)
    public String contentType;

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;

    @Column(name = "duration_seconds", nullable = false)
    public int durationSeconds;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static UserProfileAudio findByUserProfile(UserProfile profile) {
        return find("userProfile = ?1", profile).firstResult();
    }

    public static UserProfileAudio findByIdAndUserProfile(UUID id, UserProfile profile) {
        return find("id = ?1 and userProfile = ?2", id, profile).firstResult();
    }
}
