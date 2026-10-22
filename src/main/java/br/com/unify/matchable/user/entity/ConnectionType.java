package br.com.unify.matchable.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "connection_types")
public class ConnectionType extends LookupEntity {
}
