package br.com.unify.matchable.user.entity;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false, unique = true)
    public User user;

    @Column(name = "bio")
    public String bio;

    @Column(name = "avatar_url")
    public String avatarUrl;
}
