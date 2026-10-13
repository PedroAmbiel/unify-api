package br.com.unify.matchable.notification.entity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Notificação in-app (e origem do push). Migration:
 * V21__create_notifications_and_push_tokens.sql.
 *
 * <p>Não lida = {@code readAt} nulo. As consultas de leitura recebem o
 * conjunto de {@code users.id} bloqueados (nos dois sentidos) e escondem os
 * avisos causados por eles — o filtro é na leitura, a linha continua no banco.
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_recipient_created", columnList = "fk_recipient_user, created_at desc, id desc"),
                @Index(name = "idx_notifications_recipient_type_reference", columnList = "fk_recipient_user, type, reference_id")
        }
)
@Check(constraints = "event_count >= 1")
public class Notification extends PanacheEntityBase {

    /** Sentinela para {@code not in (:hidden)} com lista vazia (Hibernate não aceita IN vazio). */
    private static final UUID NO_USER = new UUID(0L, 0L);

    private static final String VISIBLE_TO_RECIPIENT =
            "recipient.id = :recipientId and (actor is null or actor.id not in :hidden)";

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_recipient_user", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_recipient_user"))
    public User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "fk_actor_user", foreignKey = @ForeignKey(name = "fk_notifications_actor_user"))
    public User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    public NotificationType type;

    @Column(name = "reference_id")
    public UUID referenceId;

    @Column(name = "context_id")
    public UUID contextId;

    @Column(name = "title", nullable = false, length = 140)
    public String title;

    @Column(name = "body", nullable = false, length = 500)
    public String body;

    /** Eventos agrupados nesta linha (mensagens não lidas da mesma conversa). */
    @Column(name = "event_count", nullable = false)
    public int eventCount = 1;

    @Column(name = "read_at")
    public Instant readAt;

    /** Momento do último evento agrupado: é por ele que a caixa de entrada ordena. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public boolean isRead() {
        return readAt != null;
    }

    public static PanacheQuery<Notification> queryVisibleTo(User recipient, Collection<UUID> hiddenActorIds) {
        return find(
                VISIBLE_TO_RECIPIENT + " order by createdAt desc, id desc",
                visibilityParameters(recipient, hiddenActorIds)
        );
    }

    public static long countUnreadVisibleTo(User recipient, Collection<UUID> hiddenActorIds) {
        return count(VISIBLE_TO_RECIPIENT + " and readAt is null", visibilityParameters(recipient, hiddenActorIds));
    }

    public static Notification findByIdAndRecipient(UUID id, User recipient) {
        return find("id = ?1 and recipient = ?2", id, recipient).firstResult();
    }

    /** Marca TODAS as não lidas do destinatário (inclusive as escondidas por bloqueio). */
    public static long markAllAsRead(User recipient, Instant readAt) {
        return update("readAt = ?1 where recipient = ?2 and readAt is null", readAt, recipient);
    }

    public static long markAsReadByReference(User recipient, NotificationType type, UUID referenceId, Instant readAt) {
        return update(
                "readAt = ?1 where recipient = ?2 and type = ?3 and referenceId = ?4 and readAt is null",
                readAt, recipient, type, referenceId
        );
    }

    /** Linha não lida do mesmo destino (agrupamento de mensagens). */
    public static Notification findUnreadByReference(User recipient, NotificationType type, UUID referenceId) {
        return find(
                "recipient = ?1 and type = ?2 and referenceId = ?3 and readAt is null order by createdAt desc",
                recipient, type, referenceId
        ).firstResult();
    }

    /** Já existe aviso do mesmo ator para o mesmo destino desde {@code since}? (deduplicação) */
    public static boolean existsSince(User recipient, NotificationType type, UUID referenceId, User actor, Instant since) {
        return count(
                "recipient = ?1 and type = ?2 and referenceId = ?3 and actor = ?4 and createdAt >= ?5",
                recipient, type, referenceId, actor, since
        ) > 0;
    }

    private static Parameters visibilityParameters(User recipient, Collection<UUID> hiddenActorIds) {
        Collection<UUID> hidden = hiddenActorIds == null || hiddenActorIds.isEmpty()
                ? List.of(NO_USER)
                : hiddenActorIds;
        return Parameters.with("recipientId", recipient.id).and("hidden", hidden);
    }
}
