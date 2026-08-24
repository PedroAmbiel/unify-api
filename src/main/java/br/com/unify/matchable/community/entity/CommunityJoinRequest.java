package br.com.unify.matchable.community.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Solicitação pendente de entrada em comunidade privada. A presença de uma
 * linha significa "pendente": aprovar vira membership e remove a linha;
 * recusar (ou cancelar) apenas remove a linha.
 */
@Entity
@Table(
    name = "community_join_requests",
    uniqueConstraints = @UniqueConstraint(name = "uq_community_join_request_community_profile", columnNames = { "fk_community", "fk_user_profile" })
)
public class CommunityJoinRequest extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_community", nullable = false, foreignKey = @ForeignKey(name = "fk_community_join_requests_community"))
    public Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_profile", nullable = false, foreignKey = @ForeignKey(name = "fk_community_join_requests_user_profile"))
    public UserProfile userProfile;

    @Column(name = "requested_at", nullable = false)
    public Instant requestedAt;

    public static CommunityJoinRequest findByCommunityAndUser(Community community, User user) {
        return find("community = ?1 and userProfile.user = ?2", community, user).firstResult();
    }

    public static CommunityJoinRequest findByIdAndCommunity(UUID id, Community community) {
        return find("id = ?1 and community = ?2", id, community).firstResult();
    }

    /** Ordenacao estavel para paginacao: solicitacoes mais antigas primeiro. */
    public static PanacheQuery<CommunityJoinRequest> queryByCommunity(Community community) {
        return find("community = ?1 order by requestedAt asc, id asc", community);
    }

    public static long countByCommunity(Community community) {
        return count("community", community);
    }
}
