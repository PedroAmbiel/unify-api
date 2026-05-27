package br.com.unify.matchable.community.entity;

import java.sql.Blob;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.unify.matchable.user.entity.User;
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
@Table(name = "community_posts")
public class CommunityPost extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_community", nullable = false, foreignKey = @ForeignKey(name = "fk_community_posts_community"))
    public Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false, foreignKey = @ForeignKey(name = "fk_community_posts_user"))
    public User author;

    @Column(name = "body", nullable = false, length = 4000)
    public String body;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "media_oid", columnDefinition = "oid")
    public Blob mediaOid;

    public static List<CommunityPost> listByCommunity(Community community) {
        return list("community = ?1 order by createdAt desc, id desc", community);
    }

    public static CommunityPost findByIdWithActiveCommunity(UUID id) {
        return find("id = ?1 and community.active = true", id).firstResult();
    }
}