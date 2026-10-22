package br.com.unify.matchable.community.entity;

import java.sql.Blob;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.unify.matchable.community.enums.CommunityPrivacy;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "communities")
public class Community extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "description", length = 2000)
    public String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_owner_user", nullable = false, foreignKey = @ForeignKey(name = "fk_communities_owner_user"))
    public User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_category", foreignKey = @ForeignKey(name = "fk_communities_category"))
    public CommunityCategory category;

    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "icon_oid", columnDefinition = "oid")
    public Blob iconOid;

    @Column(name = "active", nullable = false)
    public boolean active = true;

    @Column(name = "featured", nullable = false)
    public boolean featured;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy", nullable = false, length = 20)
    public CommunityPrivacy privacy = CommunityPrivacy.PUBLIC;

    /**
     * Semana 07 (V36): criação da comunidade, base de "comunidades criadas" no
     * dashboard. As anteriores à V36 herdaram a entrada mais antiga entre os
     * membros. O default cobre os inserts por SQL dos seeds de %dev/%test.
     */
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    public static Community findFeedCommunity() {
        Community featuredCommunity = find("active = true and featured = true order by id").firstResult();
        return featuredCommunity != null ? featuredCommunity : find("active = true order by id").firstResult();
    }

    public static Community findActiveById(UUID id) {
        return find("id = ?1 and active = true", id).firstResult();
    }

    @PrePersist
    void fillCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}