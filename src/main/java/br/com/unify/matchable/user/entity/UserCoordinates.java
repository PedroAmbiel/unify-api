package br.com.unify.matchable.user.entity;

import java.math.BigDecimal;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "user_coordinates",
        indexes = {
                @Index(name = "idx_user_coordinates_profile", columnList = "fk_user_profile"),
                @Index(name = "idx_user_coordinates_active", columnList = "active")
        }
)
public class UserCoordinates extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_profile", nullable = false)
    public UserProfile userProfile;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    public BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    public BigDecimal longitude;

    @Column(name = "active", nullable = false)
    public boolean active;
}