package br.com.unify.matchable.user.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false, unique = true)
    public User user;

    @Column(name = "bio")
    public String bio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_gender")
    public Gender gender;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_profile_disabilities",
        joinColumns = @JoinColumn(name = "fk_user_profile"),
        inverseJoinColumns = @JoinColumn(name = "fk_disability")
    )
    @OrderBy("id")
    public Set<Disability> disabilities = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_profile_accessibility_needs",
        joinColumns = @JoinColumn(name = "fk_user_profile"),
        inverseJoinColumns = @JoinColumn(name = "fk_accessibility_need")
    )
    @OrderBy("id")
    public Set<AccessibilityNeed> accessibilityNeeds = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_autonomy_level")
    public AutonomyLevel autonomyLevel;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_profile_communication_forms",
        joinColumns = @JoinColumn(name = "fk_user_profile"),
        inverseJoinColumns = @JoinColumn(name = "fk_communication_form")
    )
    @OrderBy("id")
    public Set<CommunicationForm> communicationForms = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_profile_lifestyle_types",
        joinColumns = @JoinColumn(name = "fk_user_profile"),
        inverseJoinColumns = @JoinColumn(name = "fk_lifestyle_type")
    )
    @OrderBy("id")
    public Set<LifestyleType> lifestyleTypes = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_energy_level")
    public EnergyLevel energyLevel;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_profile_interest_types",
        joinColumns = @JoinColumn(name = "fk_user_profile"),
        inverseJoinColumns = @JoinColumn(name = "fk_interest_type")
    )
    @OrderBy("id")
    public Set<InterestType> interestTypes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("id desc")
    public List<UserCoordinates> coordinates = new ArrayList<>();

    @OneToOne(mappedBy = "userProfile", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    public UserMatchPreference matchPreference;

    public static UserProfile findByUser(User user) {
    return find("user", user).firstResult();
    }

    public UserCoordinates getActiveCoordinate() {
    return coordinates.stream()
        .filter(coordinate -> coordinate.active)
        .findFirst()
        .orElse(null);
    }
}
