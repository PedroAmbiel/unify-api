package br.com.unify.matchable.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "disabilities")
public class Disability extends LookupEntity {

    @Column(name = "ionic_icon", nullable = false)
    public String ionicIcon;
}
