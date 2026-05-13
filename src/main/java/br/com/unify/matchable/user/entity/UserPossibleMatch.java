package br.com.unify.matchable.user.entity;

import java.time.Instant;
import java.util.List;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "user_possible_matches",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_possible_matches_starter_pending",
                        columnNames = { "fk_starter_user_profile", "fk_pending_user_profile" }
                )
        },
        indexes = {
                @Index(name = "idx_user_possible_matches_starter", columnList = "fk_starter_user_profile"),
                @Index(name = "idx_user_possible_matches_pending", columnList = "fk_pending_user_profile"),
                @Index(name = "idx_user_possible_matches_pending_answer", columnList = "pending_accepted")
        }
)
public class UserPossibleMatch extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_starter_user_profile", nullable = false)
    public UserProfile starterProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_pending_user_profile", nullable = false)
    public UserProfile pendingProfile;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "starter_accepted", nullable = false)
    public boolean starterAccepted;

    @Column(name = "pending_accepted")
    public Boolean pendingAccepted;

    public static UserPossibleMatch findByStarterAndPending(UserProfile starterProfile, UserProfile pendingProfile) {
        return find("starterProfile = ?1 and pendingProfile = ?2", starterProfile, pendingProfile).firstResult();
    }

    public static List<UserPossibleMatch> listInboundPending(UserProfile pendingProfile) {
        return list("pendingProfile = ?1 and pendingAccepted is null order by createdAt desc", pendingProfile);
    }

    public static List<UserPossibleMatch> listOutboundPending(UserProfile starterProfile) {
        return list("starterProfile = ?1 and pendingAccepted is null order by createdAt desc", starterProfile);
    }

    public static List<UserPossibleMatch> listConfirmedForProfile(UserProfile profile) {
        return list(
                "(starterProfile = ?1 or pendingProfile = ?1) and starterAccepted = true and pendingAccepted = true order by createdAt desc",
                profile
        );
    }

    public static List<UserPossibleMatch> listAllRelatedToProfile(UserProfile profile) {
        return list("starterProfile = ?1 or pendingProfile = ?1 order by createdAt desc", profile);
    }

    public static boolean existsConfirmedBetween(UserProfile firstProfile, UserProfile secondProfile) {
        return count(
                "((starterProfile = ?1 and pendingProfile = ?2) or (starterProfile = ?2 and pendingProfile = ?1)) and starterAccepted = true and pendingAccepted = true",
                firstProfile,
                secondProfile
        ) > 0;
    }

    public static long deleteDeclinedPendingMatches() {
        return delete("pendingAccepted = false");
    }
}