package br.com.unify.matchable.chat.entity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Conversa 1:1 entre dois perfis que formaram um match mútuo.
 *
 * <p>A conversa é 1:1 com {@link UserPossibleMatch} (constraint única): isso garante
 * estruturalmente que só existe conversa onde existe match e dá um ponto único de
 * verificação de autorização.
 */
@Entity
@Table(
        name = "conversations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_conversations_user_possible_match",
                        columnNames = { "fk_user_possible_match" }
                )
        },
        indexes = {
                @Index(name = "idx_conversations_participant_one", columnList = "fk_participant_one_user_profile"),
                @Index(name = "idx_conversations_participant_two", columnList = "fk_participant_two_user_profile"),
                @Index(name = "idx_conversations_last_message_at", columnList = "last_message_at")
        }
)
public class Conversation extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "fk_user_possible_match",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_conversations_user_possible_match")
    )
    public UserPossibleMatch possibleMatch;

    /** Participante com o menor UUID do par (regra determinística — ver ChatServiceImplementation). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "fk_participant_one_user_profile",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversations_participant_one_user_profile")
    )
    public UserProfile participantOne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "fk_participant_two_user_profile",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversations_participant_two_user_profile")
    )
    public UserProfile participantTwo;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_message_at")
    public Instant lastMessageAt;

    // ---- consultas ------------------------------------------------------

    public static Conversation findByPossibleMatch(UserPossibleMatch possibleMatch) {
        if (possibleMatch == null) {
            return null;
        }
        return find("possibleMatch", possibleMatch).firstResult();
    }

    public static PanacheQuery<Conversation> findForParticipant(UserProfile profile) {
        return find(
                "participantOne = ?1 or participantTwo = ?1 "
                        + "order by coalesce(lastMessageAt, createdAt) desc, id desc",
                profile
        );
    }

    public static List<Conversation> listForParticipant(UserProfile profile) {
        return findForParticipant(profile).list();
    }

    // ---- helpers --------------------------------------------------------

    public boolean hasParticipant(UserProfile profile) {
        if (profile == null) {
            return false;
        }
        return (participantOne != null && Objects.equals(participantOne.id, profile.id))
                || (participantTwo != null && Objects.equals(participantTwo.id, profile.id));
    }

    public UserProfile otherParticipant(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return participantOne != null && Objects.equals(participantOne.id, profile.id)
                ? participantTwo
                : participantOne;
    }
}
