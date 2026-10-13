package br.com.unify.matchable.notification.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jboss.logging.Logger;

import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.entity.UserPushToken;
import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.notification.push.PushDeliveryResult;
import br.com.unify.matchable.notification.push.PushGateway;
import br.com.unify.matchable.notification.push.PushMessage;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

/**
 * Entrega o push de UMA notificação já commitada.
 *
 * <p>Roda numa virtual thread, fora da requisição que gerou o evento — único
 * lugar do backend com esse formato, por isso o comentário:
 * <ul>
 *   <li>{@code @ActivateRequestContext}: sem requisição HTTP não há contexto
 *       de request ativo, e o Panache/Hibernate precisa dele.</li>
 *   <li>Duas transações curtas ({@code QuarkusTransaction.requiringNew()}),
 *       uma para LER e outra para APAGAR tokens mortos, e a chamada de rede
 *       ao FCM no meio, FORA de transação: um FCM lento não segura conexão
 *       do pool aberta.</li>
 * </ul>
 */
@ApplicationScoped
public class PushDispatchService {

    private static final Logger LOG = Logger.getLogger(PushDispatchService.class);

    @Inject
    PushGateway pushGateway;

    @ActivateRequestContext
    public void dispatch(UUID notificationId) {
        if (notificationId == null || !pushGateway.isEnabled()) {
            return;
        }

        List<PushMessage> messages = QuarkusTransaction.requiringNew().call(() -> buildMessages(notificationId));
        if (messages.isEmpty()) {
            return;
        }

        List<PushDeliveryResult> results = pushGateway.send(messages);

        List<String> deadTokens = new ArrayList<>();
        for (PushDeliveryResult result : results) {
            if (result.status() == PushDeliveryResult.Status.INVALID_TOKEN) {
                deadTokens.add(result.token());
            }
        }
        if (!deadTokens.isEmpty()) {
            QuarkusTransaction.requiringNew().run(() -> deleteTokens(deadTokens));
            LOG.infof("%d token(s) de push inválido(s) removido(s)", deadTokens.size());
        }
    }

    List<PushMessage> buildMessages(UUID notificationId) {
        Notification notification = Notification.findById(notificationId);
        // Já lida (ex.: a pessoa estava com a conversa aberta): não incomoda.
        if (notification == null || notification.isRead()) {
            return List.of();
        }

        List<UserPushToken> tokens = UserPushToken.listByUserId(notification.recipient.id);
        if (tokens.isEmpty()) {
            return List.of();
        }

        Map<String, String> data = buildData(notification);
        String tag = buildTag(notification);

        return tokens.stream()
                .map(token -> new PushMessage(token.token, notification.title, notification.body, data, tag))
                .toList();
    }

    void deleteTokens(List<String> tokens) {
        for (String token : tokens) {
            UserPushToken pushToken = UserPushToken.findByToken(token);
            if (pushToken != null) {
                pushToken.delete();
            }
        }
    }

    /** Vira {@code content.data} no app: o mesmo destino que a lista in-app usa. */
    static Map<String, String> buildData(Notification notification) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationId", notification.id.toString());
        data.put("type", notification.type.name());
        if (notification.referenceId != null) {
            data.put("referenceId", notification.referenceId.toString());
        }
        if (notification.contextId != null) {
            data.put("contextId", notification.contextId.toString());
        }
        return data;
    }

    /** Mensagens da mesma conversa substituem umas às outras na bandeja. */
    static String buildTag(Notification notification) {
        if (notification.type == NotificationType.NEW_MESSAGE && notification.referenceId != null) {
            return "conversation-" + notification.referenceId;
        }
        return "notification-" + notification.id;
    }
}
