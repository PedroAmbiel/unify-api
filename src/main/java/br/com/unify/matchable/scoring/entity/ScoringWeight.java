package br.com.unify.matchable.scoring.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.Check;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Peso salvo de uma dimensão de pontuação (V33). Sem linha, vale o padrão do
 * código ({@code ScoringDimension.defaultWeight()}).
 */
@Entity
@Table(name = "scoring_weights")
// Espelha ck_scoring_weights_weight (V33) no schema que o Hibernate gera em %dev/%test.
@Check(name = "ck_scoring_weights_weight", constraints = "weight >= 0")
public class ScoringWeight extends PanacheEntityBase {

    /**
     * Nome da constante de {@code ScoringDimension}. Texto, não enum JPA: uma
     * dimensão aposentada no código não pode derrubar a leitura dos pesos.
     */
    @Id
    @Column(name = "dimension", length = 60, nullable = false, updatable = false)
    public String dimension;

    @Column(name = "weight", nullable = false, precision = 7, scale = 2)
    public BigDecimal weight;
}
