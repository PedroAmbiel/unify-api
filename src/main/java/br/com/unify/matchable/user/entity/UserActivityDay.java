package br.com.unify.matchable.user.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Dia (America/Sao_Paulo) em que a pessoa usou o app autenticada (V36). Uma
 * linha por usuário por dia: é a base de "usuários ativos", "inativos",
 * frequência de uso e retenção no dashboard. Gravada pelo
 * {@code UserActivityTracker}; nunca editada.
 */
@Entity
@Table(name = "user_activity_days")
@IdClass(UserActivityDay.Key.class)
public class UserActivityDay extends PanacheEntityBase {

    @Id
    @Column(name = "fk_user", nullable = false, updatable = false)
    public UUID userId;

    @Id
    @Column(name = "activity_date", nullable = false, updatable = false)
    public LocalDate activityDate;

    public static class Key implements Serializable {

        public UUID userId;
        public LocalDate activityDate;

        public Key() {
        }

        public Key(UUID userId, LocalDate activityDate) {
            this.userId = userId;
            this.activityDate = activityDate;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(userId, key.userId)
                    && Objects.equals(activityDate, key.activityDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, activityDate);
        }
    }
}
