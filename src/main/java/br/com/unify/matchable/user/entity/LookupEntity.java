package br.com.unify.matchable.user.entity;

import org.hibernate.annotations.ColumnDefault;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Base dos catálogos de seleção (gêneros, pronomes, interesses... e categorias
 * de comunidade). Semana 06: todos ganharam {@code active} (V29) para o
 * backoffice desativar um item sem quebrar os perfis que já o usam.
 *
 * O id continua manual: os seeds usam ids fixos e itens criados pelo
 * backoffice pegam o próximo valor de {@code seq_<tabela>_id} (V30).
 */
@MappedSuperclass
public abstract class LookupEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public Integer id;

    @Column(name = "description", nullable = false, unique = true)
    public String description;

    @ColumnDefault("true")
    @Column(name = "active", nullable = false)
    public boolean active = true;
}
