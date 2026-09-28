package br.com.unify.matchable.user.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import br.com.unify.matchable.user.enums.FeedVisibility;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Privacidade do usuário. Migration: V15__create_user_privacy_settings.sql.
 *
 * <p>Criação preguiçosa: quem nunca abriu a tela de privacidade NÃO tem linha
 * aqui e vale como os defaults abaixo. Toda consulta fora do service deve
 * tratar "sem linha" como default — em SQL, {@code not exists (... = false)}
 * ou {@code left join + coalesce}; nunca {@code inner join}, que excluiria
 * todo mundo por padrão.
 */
@Entity
@Table(name = "user_privacy_settings")
public class UserPrivacySettings extends PanacheEntityBase {

    public static final boolean DEFAULT_DISCOVERABLE = true;
    public static final FeedVisibility DEFAULT_FEED_VISIBILITY = FeedVisibility.PUBLIC;
    public static final boolean DEFAULT_SHOW_DISTANCE = true;
    public static final boolean DEFAULT_SHOW_AGE = true;
    public static final boolean DEFAULT_FOLLOW_APPROVAL_REQUIRED = false;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_user", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_user_privacy_settings_user"))
    public User user;

    @ColumnDefault("true")
    @Column(name = "discoverable", nullable = false)
    public boolean discoverable = DEFAULT_DISCOVERABLE;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PUBLIC'")
    @Column(name = "feed_visibility", nullable = false, length = 20)
    public FeedVisibility feedVisibility = DEFAULT_FEED_VISIBILITY;

    @ColumnDefault("true")
    @Column(name = "show_distance", nullable = false)
    public boolean showDistance = DEFAULT_SHOW_DISTANCE;

    @ColumnDefault("true")
    @Column(name = "show_age", nullable = false)
    public boolean showAge = DEFAULT_SHOW_AGE;

    /** V19: seguir esta conta vira pedido (UserFollowRequest) até o dono aceitar. */
    @ColumnDefault("false")
    @Column(name = "follow_approval_required", nullable = false)
    public boolean followApprovalRequired = DEFAULT_FOLLOW_APPROVAL_REQUIRED;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    public static UserPrivacySettings findByUser(User user) {
        return find("user", user).firstResult();
    }

    /** {@code showAge} efetivo de {@code user} (default quando não há linha). Vale para toda tela que mostra a idade de outra pessoa. */
    public static boolean showsAge(User user) {
        UserPrivacySettings settings = user == null ? null : findByUser(user);
        return settings == null ? DEFAULT_SHOW_AGE : settings.showAge;
    }

    /** {@code followApprovalRequired} efetivo de {@code user} (default quando não há linha). */
    public static boolean requiresFollowApproval(User user) {
        UserPrivacySettings settings = user == null ? null : findByUser(user);
        return settings == null ? DEFAULT_FOLLOW_APPROVAL_REQUIRED : settings.followApprovalRequired;
    }

    /**
     * Posts pessoais só para seguidores: {@code feedVisibility = FOLLOWERS_ONLY} OU conta
     * privada ({@code followApprovalRequired}). Gêmeo em SQL:
     * {@code HomeFeedRankingPolicy.authorVisibilityFilter()}.
     */
    public static boolean restrictsPostsToFollowers(User user) {
        UserPrivacySettings settings = user == null ? null : findByUser(user);
        return settings != null
                && (settings.feedVisibility == FeedVisibility.FOLLOWERS_ONLY || settings.followApprovalRequired);
    }

    /** Visibilidade efetiva do feed pessoal de {@code user} (default quando não há linha). */
    public static FeedVisibility feedVisibilityOf(User user) {
        UserPrivacySettings settings = user == null ? null : findByUser(user);
        return settings == null ? DEFAULT_FEED_VISIBILITY : settings.feedVisibility;
    }
}
