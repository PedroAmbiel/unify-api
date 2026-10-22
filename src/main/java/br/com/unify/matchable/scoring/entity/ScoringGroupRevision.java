package br.com.unify.matchable.scoring.entity;

import java.time.Instant;

import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Revisão dos pesos de um grupo (V33). Cada save do backoffice trava esta
 * linha, confere a revisão que o admin estava editando (409 se outra pessoa
 * salvou antes) e soma 1.
 */
@Entity
@Table(name = "scoring_group_revisions")
public class ScoringGroupRevision extends PanacheEntityBase {

    /** Nome da constante de {@code ScoringGroup} (MATCH, FEED, COMMUNITY_DISCOVERY). */
    @Id
    @Column(name = "scoring_group", length = 40, nullable = false, updatable = false)
    public String scoringGroup;

    @Column(name = "revision", nullable = false)
    public long revision;

    /** Nulo enquanto o grupo nunca foi alterado pelo backoffice. */
    @Column(name = "updated_at")
    public Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_updated_by", foreignKey = @ForeignKey(name = "fk_scoring_group_revisions_updated_by"))
    public User updatedBy;
}
