package br.com.unify.matchable.community.entity;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "community_categories")
public class CommunityCategory extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public Integer id;

    @Column(name = "description", nullable = false, unique = true)
    public String description;

    @Column(name = "ionic_icon", length = 60)
    public String ionicIcon;

    public static List<CommunityCategory> listAllOrderedByDescription() {
        return list("order by description asc");
    }
}
