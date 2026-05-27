package br.com.unify.matchable.user.entity;

import java.sql.Blob;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profile_images")
public class UserProfileImage extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "oid", nullable = false, columnDefinition = "oid")
    public Blob oid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_profile", nullable = false, foreignKey = @ForeignKey(name = "fk_user_profile_images_user_profile"))
    public UserProfile userProfile;

    @Column(name = "is_profile_pic", nullable = false)
    public boolean profilePicture;

    @Column(name = "active", nullable = false)
    public boolean active = true;

    public static UserProfileImage findActiveProfilePicture(UserProfile profile) {
        return find("userProfile = ?1 and active = true and profilePicture = true", profile).firstResult();
    }

    public static List<UserProfileImage> listActiveGalleryImages(UserProfile profile) {
        return list("userProfile = ?1 and active = true and profilePicture = false order by id desc", profile);
    }

    public static long countActiveGalleryImages(UserProfile profile) {
        return count("userProfile = ?1 and active = true and profilePicture = false", profile);
    }

    public static UserProfileImage findByIdAndUserProfile(UUID id, UserProfile profile) {
        return find("id = ?1 and userProfile = ?2", id, profile).firstResult();
    }

    public static UserProfileImage findActiveByIdAndUserProfile(UUID id, UserProfile profile) {
        return find("id = ?1 and userProfile = ?2 and active = true", id, profile).firstResult();
    }
}