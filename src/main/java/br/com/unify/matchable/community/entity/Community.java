package br.com.unify.matchable.community.entity;

import java.sql.Blob;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
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
    @JoinColumn(name = "fk_owner_user", nullable = false)
    public User owner;

    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "icon_oid", columnDefinition = "oid")
    public Blob iconOid;

    @Column(name = "active", nullable = false)
    public boolean active = true;

    @Column(name = "featured", nullable = false)
    public boolean featured;

    public static Community findFeedCommunity() {
        Community featuredCommunity = find("active = true and featured = true order by id").firstResult();
        return featuredCommunity != null ? featuredCommunity : find("active = true order by id").firstResult();
    }

    public static Community findActiveById(UUID id) {
        return find("id = ?1 and active = true", id).firstResult();
    }
}