package br.com.unify.matchable.community.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "community_memberships",
    uniqueConstraints = @UniqueConstraint(name = "uq_community_membership_community_profile", columnNames = { "fk_community", "fk_user_profile" })
)
public class CommunityMembership extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_community", nullable = false)
    public Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_profile", nullable = false)
    public UserProfile userProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    public CommunityMemberRole role = CommunityMemberRole.MEMBER;

    @Column(name = "joined_at", nullable = false)
    public Instant joinedAt;

    public static CommunityMembership findByCommunityAndUser(Community community, User user) {
        return find("community = ?1 and userProfile.user = ?2", community, user).firstResult();
    }

    public static CommunityMembership findByCommunityAndUserProfile(Community community, UserProfile userProfile) {
        return find("community = ?1 and userProfile = ?2", community, userProfile).firstResult();
    }

    public static CommunityMembership findByCommunityAndUserProfileId(Community community, UUID userProfileId) {
        return find("community = ?1 and userProfile.id = ?2", community, userProfileId).firstResult();
    }

    public static long countByCommunity(Community community) {
        return count("community", community);
    }

    public static List<CommunityMembership> listByCommunity(Community community) {
        return list("community = ?1 order by joinedAt asc, id asc", community);
    }
}