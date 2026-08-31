package br.com.unify.matchable.user.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
                @Index(name = "idx_user_possible_matches_pending_answer", columnList = "pending_accepted"),
                @Index(name = "idx_user_possible_matches_declined_at", columnList = "declined_at")
        }
)
public class UserPossibleMatch extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_starter_user_profile", nullable = false, foreignKey = @ForeignKey(name = "fk_user_possible_matches_starter_user_profile"))
    public UserProfile starterProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_pending_user_profile", nullable = false, foreignKey = @ForeignKey(name = "fk_user_possible_matches_pending_user_profile"))
    public UserProfile pendingProfile;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "starter_accepted", nullable = false)
    public boolean starterAccepted;

    @Column(name = "pending_accepted")
    public Boolean pendingAccepted;

    /**
     * Momento em que o match foi recusado. Enquanto estiver dentro da carência
     * (unify.match.decline-cooldown-days) o perfil recusado não volta ao feed de descoberta.
     */
    @Column(name = "declined_at")
    public Instant declinedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "fk_declined_by_user_profile",
            foreignKey = @ForeignKey(name = "fk_user_possible_matches_declined_by_user_profile")
    )
    public UserProfile declinedByProfile;

    public static UserPossibleMatch findByStarterAndPending(UserProfile starterProfile, UserProfile pendingProfile) {
        return find("starterProfile = ?1 and pendingProfile = ?2", starterProfile, pendingProfile).firstResult();
    }

    public static List<UserPossibleMatch> listInboundPending(UserProfile pendingProfile) {
        return list("pendingProfile = ?1 and pendingAccepted is null order by createdAt desc", pendingProfile);
    }

    public static List<UserPossibleMatch> listOutboundPending(UserProfile starterProfile) {
        return list("starterProfile = ?1 and pendingAccepted is null order by createdAt desc", starterProfile);
    }

    public static PanacheQuery<UserPossibleMatch> findConfirmedForProfile(UserProfile profile) {
        return find(
                "(starterProfile = ?1 or pendingProfile = ?1) and starterAccepted = true and pendingAccepted = true order by createdAt desc",
                profile
        );
    }

    public static List<UserPossibleMatch> listConfirmedForProfile(UserProfile profile) {
        return findConfirmedForProfile(profile).list();
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

    /** Recusas cujo período de carência já expirou — só essas podem ser apagadas. */
    public static long deleteExpiredDeclines(Instant threshold) {
        return delete("declinedAt is not null and declinedAt < ?1", threshold);
    }

    /** Perfis que o usuário recusou (ou que o recusaram) e ainda estão em carência. */
    public static List<UserPossibleMatch> listActiveDeclines(UserProfile profile, Instant threshold) {
        return list(
                "(starterProfile = ?1 or pendingProfile = ?1) and declinedAt is not null and declinedAt >= ?2",
                profile,
                threshold
        );
    }

    /**
     * Recusas cuja carência já expirou: o perfil volta ao pool de descoberta, mas
     * penalizado (MatchScoringPolicy.PENALTY_RESHOWN_AFTER_COOLDOWN).
     */
    public static List<UserPossibleMatch> listExpiredDeclines(UserProfile profile, Instant threshold) {
        return list(
                "(starterProfile = ?1 or pendingProfile = ?1) and declinedAt is not null and declinedAt < ?2",
                profile,
                threshold
        );
    }

    public static UserPossibleMatch findBetween(UserProfile first, UserProfile second) {
        return find(
                "(starterProfile = ?1 and pendingProfile = ?2) or (starterProfile = ?2 and pendingProfile = ?1)",
                first,
                second
        ).firstResult();
    }
}