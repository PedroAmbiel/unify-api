package br.com.unify.matchable.user.entity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import br.com.unify.matchable.user.enums.SimilarityPreference;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_match_preferences")
public class UserMatchPreference extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_profile", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_user_match_preferences_user_profile"))
    public UserProfile userProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_connection_type", foreignKey = @ForeignKey(name = "fk_user_match_preferences_connection_type"))
    public ConnectionType connectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "accessibility_need_similarity")
    public SimilarityPreference accessibilityNeedSimilarity;

    @Enumerated(EnumType.STRING)
    @Column(name = "autonomy_compatibility")
    public SimilarityPreference autonomyCompatibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifestyle_similarity")
    public SimilarityPreference lifestyleSimilarity;

    @Enumerated(EnumType.STRING)
    @Column(name = "love_language_similarity")
    public SimilarityPreference loveLanguageSimilarity;

    @Enumerated(EnumType.STRING)
    @Column(name = "energy_level_similarity")
    public SimilarityPreference energyLevelSimilarity;

    @Column(name = "min_age")
    public Integer minAge;

    @Column(name = "max_age")
    public Integer maxAge;

    @Column(name = "max_match_distance_km")
    public Integer maxMatchDistanceKm;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_match_preference_desired_genders",
            joinColumns = @JoinColumn(name = "fk_user_match_preference"),
            inverseJoinColumns = @JoinColumn(name = "fk_gender"),
            foreignKey = @ForeignKey(name = "fk_user_match_preference_desired_genders_user_match_preference"),
            inverseForeignKey = @ForeignKey(name = "fk_user_match_preference_desired_genders_gender")
    )
    @OrderBy("id")
    public Set<Gender> desiredGenders = new LinkedHashSet<>();

    public static UserMatchPreference findByUserProfile(UserProfile userProfile) {
        return find("userProfile", userProfile).firstResult();
    }
}