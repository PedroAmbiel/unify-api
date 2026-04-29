package br.com.unify.matchable.user.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "interest_types")
public class InterestType extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public Integer id;

    @Column(name = "description", nullable = false, unique = true)
    public String description;
}