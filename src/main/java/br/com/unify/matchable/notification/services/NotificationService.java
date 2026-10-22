package br.com.unify.matchable.notification.services;

import java.util.UUID;

import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.notification.dto.MarkAllReadResponse;
import br.com.unify.matchable.notification.dto.NotificationResponse;
import br.com.unify.matchable.notification.dto.PushTokenRegisterRequest;
import br.com.unify.matchable.notification.dto.PushTokenResponse;
import br.com.unify.matchable.notification.dto.UnreadCountResponse;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.user.entity.User;

/**
 * Caixa de entrada + emissão de notificações.
 *
 * <p>Os métodos {@code emit*} são chamados DENTRO da transação do evento de
 * origem (match, mensagem, follow, curtida...) e nunca lançam exceção de
 * regra de negócio: devolvem {@code null} quando não há o que avisar (ator =
 * destinatário, par bloqueado, aviso repetido dentro da janela de
 * deduplicação). Uma exceção aqui marcaria a transação de origem para
 * rollback — a curtida não pode falhar por causa do aviso.
 *
 * <p>O push sai depois do commit ({@link PushNotificationDispatcher}).
 */
public interface NotificationService {

    PageResponse<NotificationResponse> getPage(User user, Integer page, Integer size);

    UnreadCountResponse getUnreadCount(User user);

    NotificationResponse markAsRead(User user, UUID notificationId);

    MarkAllReadResponse markAllAsRead(User user);

    PushTokenResponse registerPushToken(User user, PushTokenRegisterRequest request);

    void removePushToken(User user, UUID pushTokenId);

    Notification emitNewMatch(User recipient, User otherUser, UUID matchId, UUID otherUserProfileId);

    /** Mensagens não lidas da mesma conversa viram UMA notificação com contador. */
    Notification emitNewMessage(User recipient, User sender, UUID conversationId, UUID senderUserProfileId, String preview);

    Notification emitNewFollower(User recipient, User follower, UUID followerUserProfileId);

    Notification emitFollowRequest(User recipient, User requester, UUID requesterUserProfileId);

    Notification emitFollowRequestAccepted(User recipient, User accepter, UUID accepterUserProfileId);

    /** {@code communityId} nulo = post pessoal. */
    Notification emitPostLiked(User recipient, User liker, UUID postId, UUID communityId);

    Notification emitPostCommented(User recipient, User commenter, UUID postId, UUID communityId, String commentPreview);

    /**
     * Para a moderação (semana 06): avisa QUEM DENUNCIOU quando a denúncia
     * sai de OPEN/UNDER_REVIEW. {@code statusLabel} já em português
     * ("resolvida", "arquivada").
     */
    Notification emitReportUpdate(User recipient, UUID reportId, String statusLabel);

    /**
     * Para a moderação (semana 07): avisa o AUTOR que a publicação foi removida.
     * O texto cita o começo do post para a pessoa saber qual foi; o motivo
     * registrado pelo moderador é interno e não vai no aviso.
     */
    Notification emitPostRemoved(User author, UUID postId, UUID communityId, String postBody);

    /**
     * Marca como lidos os avisos de um destino que perderam o sentido: abrir a
     * conversa ({@code NEW_MESSAGE} + conversa), responder um pedido para
     * seguir ({@code FOLLOW_REQUEST} + perfil de quem pediu).
     */
    void markReadByReference(User user, NotificationType type, UUID referenceId);
}
