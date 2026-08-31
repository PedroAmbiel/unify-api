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

    /** Momento em que o destinatário abriu a conversa. Nulo = não lida. */
    @Column(name = "read_at")
    public Instant readAt;

    // ---- consultas ------------------------------------------------------

    public static PanacheQuery<ChatMessage> findByConversationNewestFirst(Conversation conversation) {
        return find(
                "conversation",
                Sort.by("createdAt", Sort.Direction.Descending).and("id", Sort.Direction.Descending),
                conversation
        );
    }

    /** Mensagens criadas DEPOIS de {@code since}, em ordem crescente (modo polling). */
    public static List<ChatMessage> listSince(Conversation conversation, Instant since) {
        return list(
                "conversation = ?1 and createdAt > ?2 order by createdAt asc, id asc",
                conversation,
                since
        );
    }

    public static ChatMessage findLastOfConversation(Conversation conversation) {
        return findByConversationNewestFirst(conversation).firstResult();
    }

    /** Não lidas recebidas pelo perfil informado (ou seja, enviadas pelo OUTRO participante). */
    public static long countUnreadFor(Conversation conversation, UserProfile reader) {
        return count(
                "conversation = ?1 and sender <> ?2 and readAt is null",
                conversation,
                reader
        );
    }

    public static long countAllUnreadFor(UserProfile reader) {
        return count(
                "(conversation.participantOne = ?1 or conversation.participantTwo = ?1) "
                        + "and sender <> ?1 and readAt is null",
                reader
        );
    }

    public static long markAsRead(Conversation conversation, UserProfile reader, Instant readAt) {
        return update(
                "readAt = ?1 where conversation = ?2 and sender <> ?3 and readAt is null",
                readAt,
                conversation,
                reader
        );
    }
}
