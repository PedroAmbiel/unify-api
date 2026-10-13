package br.com.unify.matchable.notification.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.dto.PageParams;
import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.notification.dto.MarkAllReadResponse;
import br.com.unify.matchable.notification.dto.NotificationActorResponse;
import br.com.unify.matchable.notification.dto.NotificationResponse;
import br.com.unify.matchable.notification.dto.PushTokenRegisterRequest;
import br.com.unify.matchable.notification.dto.PushTokenResponse;
import br.com.unify.matchable.notification.dto.UnreadCountResponse;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.entity.UserPushToken;
import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.notification.enums.PushPlatform;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.entity.UserProfileImage;
import br.com.unify.matchable.user.services.UserBlockService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NotificationServiceImplementation implements NotificationService {

    static final String NOTIFICATION_NOT_FOUND_MESSAGE = "Notificação não encontrada";
    static final String NOTIFICATION_ID_REQUIRED_MESSAGE = "O identificador da notificação é obrigatório";
    static final String PUSH_TOKEN_REQUIRED_MESSAGE = "O token de push é obrigatório";
    static final String PUSH_TOKEN_TOO_LONG_MESSAGE = "O token de push excede o tamanho máximo";
    static final String PUSH_PLATFORM_INVALID_MESSAGE = "A plataforma do aparelho deve ser ANDROID ou IOS";
    static final String PUSH_TOKEN_NOT_FOUND_MESSAGE = "Token de push não encontrado";

    static final int PUSH_TOKEN_MAX_LENGTH = 512;
    static final int TITLE_MAX_LENGTH = 140;
    static final int BODY_MAX_LENGTH = 500;
    static final int MESSAGE_PREVIEW_MAX_LENGTH = 120;
    static final int COMMENT_PREVIEW_MAX_LENGTH = 100;
    static final String FALLBACK_NAME = "Alguém";

    static final String AVATAR_URL_PREFIX = "/communities/users/";
    static final String AVATAR_URL_SUFFIX = "/avatar";

    /**
     * Curtir/descurtir/curtir, seguir/deixar/seguir: o mesmo aviso do mesmo
     * ator para o mesmo destino dentro desta janela não se repete.
     */
    @ConfigProperty(name = "unify.notification.dedupe-window-hours", defaultValue = "24")
    long dedupeWindowHours;

    @Inject
    UserBlockService userBlockService;

    @Inject
    Event<NotificationDispatchEvent> dispatchEvent;

    // ------------------------------------------------------------ caixa de entrada

    @Override
    public PageResponse<NotificationResponse> getPage(User user, Integer page, Integer size) {
        int resolvedPage = PageParams.resolvePage(page);
        int resolvedSize = PageParams.resolveSize(size);

        PanacheQuery<Notification> query = Notification.queryVisibleTo(user, hiddenActorIds(user));
        long total = query.count();
        List<Notification> rows = query.page(Page.of(resolvedPage, resolvedSize)).list();

        return PageResponse.of(toResponses(rows), resolvedPage, resolvedSize, total);
    }

    @Override
    public UnreadCountResponse getUnreadCount(User user) {
        return new UnreadCountResponse(Notification.countUnreadVisibleTo(user, hiddenActorIds(user)));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(User user, UUID notificationId) {
        if (notificationId == null) {
            throw new IllegalArgumentException(NOTIFICATION_ID_REQUIRED_MESSAGE);
        }

        // De outra pessoa = "não existe" (404), igual a id inexistente.
        Notification notification = Notification.findByIdAndRecipient(notificationId, user);
        if (notification == null) {
            throw new NoSuchElementException(NOTIFICATION_NOT_FOUND_MESSAGE);
        }

        if (notification.readAt == null) {
            notification.readAt = Instant.now();
        }
        return toResponses(List.of(notification)).get(0);
    }

    @Override
    @Transactional
    public MarkAllReadResponse markAllAsRead(User user) {
        return new MarkAllReadResponse(Notification.markAllAsRead(user, Instant.now()));
    }

    @Override
    @Transactional
    public void markReadByReference(User user, NotificationType type, UUID referenceId) {
        if (user == null || type == null || referenceId == null) {
            return;
        }
        Notification.markAsReadByReference(user, type, referenceId, Instant.now());
    }

    // ------------------------------------------------------------ tokens de push

    @Override
    @Transactional
    public PushTokenResponse registerPushToken(User user, PushTokenRegisterRequest request) {
        String token = request == null || request.token() == null ? "" : request.token().trim();
        if (token.isEmpty()) {
            throw new IllegalArgumentException(PUSH_TOKEN_REQUIRED_MESSAGE);
        }
        if (token.length() > PUSH_TOKEN_MAX_LENGTH) {
            throw new IllegalArgumentException(PUSH_TOKEN_TOO_LONG_MESSAGE);
        }
        PushPlatform platform = PushPlatform.parse(request.platform());
        if (platform == null) {
            throw new IllegalArgumentException(PUSH_PLATFORM_INVALID_MESSAGE);
        }

        Instant now = Instant.now();
        UserPushToken existing = UserPushToken.findByToken(token);
        if (existing != null) {
            // Mesmo aparelho, outra conta (ou reinstalação): o token muda de
            // dono. Quem logou por último é quem recebe.
            existing.user = user;
            existing.platform = platform;
            existing.updatedAt = now;
            return toTokenResponse(existing);
        }

        UserPushToken pushToken = new UserPushToken();
        pushToken.id = UUIDv7Generator.generate();
        pushToken.user = user;
        pushToken.token = token;
        pushToken.platform = platform;
        pushToken.createdAt = now;
        pushToken.updatedAt = now;
        pushToken.persist();
        return toTokenResponse(pushToken);
    }

    @Override
    @Transactional
    public void removePushToken(User user, UUID pushTokenId) {
        if (pushTokenId == null) {
            throw new IllegalArgumentException(PUSH_TOKEN_NOT_FOUND_MESSAGE);
        }
        UserPushToken pushToken = UserPushToken.findByIdAndUser(pushTokenId, user);
        if (pushToken == null) {
            // O aparelho pode ter trocado de dono depois do registro: para quem
            // pede a remoção, o token simplesmente não existe mais.
            throw new NoSuchElementException(PUSH_TOKEN_NOT_FOUND_MESSAGE);
        }
        pushToken.delete();
    }

    // ------------------------------------------------------------ emissão

    @Override
    @Transactional
    public Notification emitNewMatch(User recipient, User otherUser, UUID matchId, UUID otherUserProfileId) {
        String name = displayName(otherUser);
        return emitDeduplicated(
                recipient, otherUser, NotificationType.NEW_MATCH, matchId, otherUserProfileId,
                "Novo match!",
                name + " também curtiu você. Vocês deram match!"
        );
    }

    @Override
    @Transactional
    public Notification emitNewMessage(
            User recipient, User sender, UUID conversationId, UUID senderUserProfileId, String preview) {

        if (!shouldNotify(recipient, sender) || conversationId == null) {
            return null;
        }

        String name = displayName(sender);
        String safePreview = truncate(preview == null || preview.isBlank() ? "Nova mensagem" : preview.trim(),
                MESSAGE_PREVIEW_MAX_LENGTH);

        Notification grouped = Notification.findUnreadByReference(recipient, NotificationType.NEW_MESSAGE, conversationId);
        if (grouped != null) {
            grouped.eventCount = grouped.eventCount + 1;
            grouped.actor = sender;
            grouped.contextId = senderUserProfileId;
            grouped.title = truncate(name, TITLE_MAX_LENGTH);
            grouped.body = truncate(grouped.eventCount + " novas mensagens. Última: " + safePreview, BODY_MAX_LENGTH);
            grouped.createdAt = Instant.now();
            fireDispatch(grouped);
            return grouped;
        }

        return persist(recipient, sender, NotificationType.NEW_MESSAGE, conversationId, senderUserProfileId,
                name, safePreview);
    }

    @Override
    @Transactional
    public Notification emitNewFollower(User recipient, User follower, UUID followerUserProfileId) {
        return emitDeduplicated(
                recipient, follower, NotificationType.NEW_FOLLOWER, followerUserProfileId, null,
                "Novo seguidor",
                displayName(follower) + " começou a seguir você."
        );
    }

    @Override
    @Transactional
    public Notification emitFollowRequest(User recipient, User requester, UUID requesterUserProfileId) {
        return emitDeduplicated(
                recipient, requester, NotificationType.FOLLOW_REQUEST, requesterUserProfileId, null,
                "Pedido para seguir",
                displayName(requester) + " pediu para seguir você."
        );
    }

    @Override
    @Transactional
    public Notification emitFollowRequestAccepted(User recipient, User accepter, UUID accepterUserProfileId) {
        return emitDeduplicated(
                recipient, accepter, NotificationType.FOLLOW_REQUEST_ACCEPTED, accepterUserProfileId, null,
                "Pedido aceito",
                displayName(accepter) + " aceitou seu pedido para seguir."
        );
    }

    @Override
    @Transactional
    public Notification emitPostLiked(User recipient, User liker, UUID postId, UUID communityId) {
        return emitDeduplicated(
                recipient, liker, NotificationType.POST_LIKED, postId, communityId,
                "Curtiram sua publicação",
                displayName(liker) + " curtiu sua publicação."
        );
    }

    @Override
    @Transactional
    public Notification emitPostCommented(
            User recipient, User commenter, UUID postId, UUID communityId, String commentPreview) {

        if (!shouldNotify(recipient, commenter) || postId == null) {
            return null;
        }

        String preview = commentPreview == null || commentPreview.isBlank()
                ? ""
                : ": " + truncate(commentPreview.trim(), COMMENT_PREVIEW_MAX_LENGTH);
        // Comentário é conteúdo novo a cada vez: sem deduplicação.
        return persist(recipient, commenter, NotificationType.POST_COMMENTED, postId, communityId,
                "Novo comentário",
                displayName(commenter) + " comentou na sua publicação" + preview);
    }

    @Override
    @Transactional
    public Notification emitReportUpdate(User recipient, UUID reportId, String statusLabel) {
        if (recipient == null || reportId == null) {
            return null;
        }
        String status = statusLabel == null || statusLabel.isBlank() ? "atualizada" : statusLabel.trim();
        return persist(recipient, null, NotificationType.REPORT_UPDATE, reportId, null,
                "Atualização da sua denúncia",
                "Sua denúncia foi " + status + ". Obrigado por ajudar a manter a Unify segura.");
    }

    // ------------------------------------------------------------ apoio

    private Notification emitDeduplicated(
            User recipient, User actor, NotificationType type, UUID referenceId, UUID contextId,
            String title, String body) {

        if (!shouldNotify(recipient, actor) || referenceId == null) {
            return null;
        }

        Instant since = Instant.now().minus(Duration.ofHours(dedupeWindowHours));
        if (Notification.existsSince(recipient, type, referenceId, actor, since)) {
            return null;
        }

        return persist(recipient, actor, type, referenceId, contextId, title, body);
    }

    /**
     * Ponto ÚNICO de "vale avisar?": nunca a própria pessoa e nunca entre um
     * par bloqueado (em qualquer direção).
     */
    private boolean shouldNotify(User recipient, User actor) {
        if (recipient == null || recipient.id == null) {
            return false;
        }
        if (actor == null) {
            return true;
        }
        if (Objects.equals(recipient.id, actor.id)) {
            return false;
        }
        return !userBlockService.isBlockedEitherDirection(recipient.id, actor.id);
    }

    private Notification persist(
            User recipient, User actor, NotificationType type, UUID referenceId, UUID contextId,
            String title, String body) {

        Notification notification = new Notification();
        notification.id = UUIDv7Generator.generate();
        notification.recipient = recipient;
        notification.actor = actor;
        notification.type = type;
        notification.referenceId = referenceId;
        notification.contextId = contextId;
        notification.title = truncate(title, TITLE_MAX_LENGTH);
        notification.body = truncate(body, BODY_MAX_LENGTH);
        notification.eventCount = 1;
        notification.readAt = null;
        notification.createdAt = Instant.now();
        notification.persist();

        fireDispatch(notification);
        return notification;
    }

    /**
     * Evento síncrono observado com {@code during = AFTER_SUCCESS}: o push só
     * sai se a transação de origem commitar (curtida desfeita por rollback não
     * vira push), e quem faz I/O de rede é outra thread.
     */
    private void fireDispatch(Notification notification) {
        dispatchEvent.fire(new NotificationDispatchEvent(notification.id));
    }

    private Set<UUID> hiddenActorIds(User user) {
        return userBlockService.relatedUserIds(user.id);
    }

    /**
     * Ator de cada linha em DUAS queries por página (perfil e foto ativa),
     * não duas por notificação.
     */
    private List<NotificationResponse> toResponses(List<Notification> notifications) {
        Set<UUID> actorIds = new HashSet<>();
        for (Notification notification : notifications) {
            if (notification.actor != null) {
                actorIds.add(notification.actor.id);
            }
        }

        Map<UUID, UUID> profileIdByUserId = loadProfileIds(actorIds);
        Set<UUID> usersWithPhoto = loadUsersWithProfilePicture(actorIds);

        return notifications.stream()
                .map(notification -> toResponse(notification, profileIdByUserId, usersWithPhoto))
                .toList();
    }

    private NotificationResponse toResponse(
            Notification notification, Map<UUID, UUID> profileIdByUserId, Set<UUID> usersWithPhoto) {

        User actor = notification.actor;
        NotificationActorResponse actorResponse = actor == null
                ? null
                : new NotificationActorResponse(
                        actor.id,
                        profileIdByUserId.get(actor.id),
                        displayName(actor),
                        usersWithPhoto.contains(actor.id) ? AVATAR_URL_PREFIX + actor.id + AVATAR_URL_SUFFIX : null
                );

        return new NotificationResponse(
                notification.id,
                notification.type,
                notification.referenceId,
                notification.contextId,
                notification.title,
                notification.body,
                notification.eventCount,
                notification.isRead(),
                notification.createdAt,
                actorResponse
        );
    }

    private Map<UUID, UUID> loadProfileIds(Collection<UUID> userIds) {
        Map<UUID, UUID> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = UserProfile.getEntityManager()
                .createQuery("select p.user.id, p.id from UserProfile p where p.user.id in :userIds", Object[].class)
                .setParameter("userIds", userIds)
                .getResultList();
        for (Object[] row : rows) {
            result.put((UUID) row[0], (UUID) row[1]);
        }
        return result;
    }

    private Set<UUID> loadUsersWithProfilePicture(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(UserProfileImage.find(
                        "select i.userProfile.user.id from UserProfileImage i "
                                + "where i.userProfile.user.id in ?1 and i.active = true and i.profilePicture = true",
                        userIds)
                .project(UUID.class)
                .list());
    }

    private PushTokenResponse toTokenResponse(UserPushToken pushToken) {
        return new PushTokenResponse(pushToken.id, pushToken.platform, pushToken.updatedAt);
    }

    /** Nome exibível SEM fallback para e-mail (o aviso aparece na tela bloqueada). */
    static String displayName(User user) {
        if (user == null) {
            return FALLBACK_NAME;
        }
        String first = user.name == null ? "" : user.name.trim();
        String last = user.lastName == null ? "" : user.lastName.trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? FALLBACK_NAME : full;
    }

    static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}
