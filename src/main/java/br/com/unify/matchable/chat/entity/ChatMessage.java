package br.com.unify.matchable.chat.entity;

import java.sql.Blob;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.unify.matchable.chat.enums.ChatMessageType;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_messages_conversation_created_at",
                       columnList = "fk_conversation, created_at"),
                @Index(name = "idx_chat_messages_conversation_updated_at",
                       columnList = "fk_conversation, updated_at"),
                @Index(name = "idx_chat_messages_sender", columnList = "fk_sender_user_profile"),
                @Index(name = "idx_chat_messages_read_at", columnList = "read_at")
        }
)
public class ChatMessage extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "fk_conversation",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_messages_conversation")
    )
    public Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "fk_sender_user_profile",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_messages_sender_user_profile")
    )
    public UserProfile sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    public ChatMessageType type;

    /** Texto da mensagem (TEXT) ou legenda opcional de mídia. Nulo para mídia sem legenda. */
    @Column(name = "body", length = 4000)
    public String body;

    /** Bytes da mídia. Imagem: JPEG comprimido pelo OidImageService. Áudio: bytes originais. */
    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "media_oid", columnDefinition = "oid")
    public Blob mediaOid;

    @Column(name = "media_content_type", length = 100)
    public String mediaContentType;

    @Column(name = "media_size_bytes")
    public Long mediaSizeBytes;

    /** Duração em segundos — usada no rótulo de acessibilidade ("áudio de 12 segundos"). */
    @Column(name = "media_duration_seconds")
    public Integer mediaDurationSeconds;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /**
     * Momento em que o destinatário BUSCOU a mensagem (lista de conversas ou conversa
     * aberta). Nulo = só enviada. Segundo estado do indicador "enviada / entregue / vista".
     */
    @Column(name = "delivered_at")
    public Instant deliveredAt;

    /** Momento em que o destinatário abriu a conversa. Nulo = não lida. */
    @Column(name = "read_at")
    public Instant readAt;

    /** Última edição do texto pelo remetente. Nulo = nunca editada. */
    @Column(name = "edited_at")
    public Instant editedAt;

    /**
     * Exclusão LÓGICA: a linha continua no histórico (o cliente mostra "Mensagem
     * apagada"), mas texto e mídia são zerados. Nulo = mensagem viva.
     */
    @Column(name = "deleted_at")
    public Instant deletedAt;

    /**
     * Cursor do polling incremental. Muda em QUALQUER alteração visível (entrega,
     * leitura, edição, exclusão), para que o outro participante receba a mudança
     * na próxima rodada de {@code ?since=}.
     */
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    // ---- consultas ------------------------------------------------------

    public static PanacheQuery<ChatMessage> findByConversationNewestFirst(Conversation conversation) {
        return find(
                "conversation",
                Sort.by("createdAt", Sort.Direction.Descending).and("id", Sort.Direction.Descending),
                conversation
        );
    }

    /**
     * Mensagens criadas OU alteradas depois de {@code since}, em ordem de criação
     * (modo polling). Compara com {@code updatedAt}, não com {@code createdAt}: assim
     * entrega, leitura, edição e exclusão também chegam ao outro lado.
     */
    public static List<ChatMessage> listSince(Conversation conversation, Instant since) {
        return list(
                "conversation = ?1 and updatedAt > ?2 order by createdAt asc, id asc",
                conversation,
                since
        );
    }

    public static ChatMessage findInConversation(Conversation conversation, UUID messageId) {
        if (conversation == null || messageId == null) {
            return null;
        }
        return find("conversation = ?1 and id = ?2", conversation, messageId).firstResult();
    }

    public static ChatMessage findLastOfConversation(Conversation conversation) {
        return findByConversationNewestFirst(conversation).firstResult();
    }

    /**
     * Não lidas recebidas pelo perfil informado (ou seja, enviadas pelo OUTRO
     * participante). Mensagens apagadas não contam.
     */
    public static long countUnreadFor(Conversation conversation, UserProfile reader) {
        return count(
                "conversation = ?1 and sender <> ?2 and readAt is null and deletedAt is null",
                conversation,
                reader
        );
    }

    public static long countAllUnreadFor(UserProfile reader) {
        return count(
                "(conversation.participantOne = ?1 or conversation.participantTwo = ?1) "
                        + "and sender <> ?1 and readAt is null and deletedAt is null",
                reader
        );
    }

    /** Marca como lidas (e, por consequência, entregues) as mensagens recebidas pelo leitor. */
    public static long markAsRead(Conversation conversation, UserProfile reader, Instant readAt) {
        return update(
                "readAt = ?1, deliveredAt = coalesce(deliveredAt, ?1), updatedAt = ?1 "
                        + "where conversation = ?2 and sender <> ?3 and readAt is null",
                readAt,
                conversation,
                reader
        );
    }

    /** Marca como entregues as mensagens que o leitor acabou de buscar nesta conversa. */
    public static long markAsDelivered(Conversation conversation, UserProfile reader, Instant deliveredAt) {
        return update(
                "deliveredAt = ?1, updatedAt = ?1 "
                        + "where conversation = ?2 and sender <> ?3 and deliveredAt is null",
                deliveredAt,
                conversation,
                reader
        );
    }

    /** Idem, para TODAS as conversas do leitor (usado ao listar as conversas). */
    public static long markAllAsDeliveredFor(UserProfile reader, Instant deliveredAt) {
        return update(
                "deliveredAt = ?1, updatedAt = ?1 "
                        + "where sender <> ?2 and deliveredAt is null "
                        + "and conversation in (select c from Conversation c "
                        + "where c.participantOne = ?2 or c.participantTwo = ?2)",
                deliveredAt,
                reader
        );
    }

    // ---- helpers --------------------------------------------------------

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
