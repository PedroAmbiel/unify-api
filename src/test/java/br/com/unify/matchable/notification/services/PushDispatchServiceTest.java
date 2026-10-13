package br.com.unify.matchable.notification.services;

import static br.com.unify.matchable.notification.NotificationFixtures.person;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.notification.NotificationFixtures.Person;
import br.com.unify.matchable.notification.dto.PushTokenRegisterRequest;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.entity.UserPushToken;
import br.com.unify.matchable.notification.push.PushDeliveryResult;
import br.com.unify.matchable.notification.push.FcmPushGateway;
import br.com.unify.matchable.notification.push.PushGateway;
import br.com.unify.matchable.notification.push.PushMessage;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Entrega do push sem rede: o gateway FCM é trocado por um stub
 * ({@code QuarkusMock}, sem Mockito). Os dados precisam estar COMMITADOS — o
 * despacho lê numa transação própria, como em produção (depois do commit da
 * origem) —, por isso aqui não há {@code @TestTransaction}.
 */
@QuarkusTest
class PushDispatchServiceTest {

    @Inject
    PushDispatchService pushDispatchService;

    @Inject
    NotificationService notificationService;

    private StubGateway gateway;

    @BeforeEach
    void installStubGateway() {
        gateway = new StubGateway();
        QuarkusMock.installMockForType(gateway, PushGateway.class);
    }

    @Test
    void sendsToEveryDeviceOfTheRecipientAndDropsDeadTokens() {
        String liveToken = "live-" + UUID.randomUUID();
        String deadToken = "dead-" + UUID.randomUUID();
        gateway.deadTokens.add(deadToken);

        UUID notificationId = QuarkusTransaction.requiringNew().call(() -> {
            Person ana = person("Ana");
            Person beto = person("Beto");
            notificationService.registerPushToken(ana.user(), new PushTokenRegisterRequest(liveToken, "ANDROID"));
            notificationService.registerPushToken(ana.user(), new PushTokenRegisterRequest(deadToken, "ANDROID"));
            notificationService.registerPushToken(beto.user(), new PushTokenRegisterRequest("beto-" + UUID.randomUUID(), "ANDROID"));
            return notificationService.emitNewFollower(ana.user(), beto.user(), beto.profile().id).id;
        });

        pushDispatchService.dispatch(notificationId);

        assertEquals(2, gateway.sent.size(), "só os aparelhos da destinatária");
        PushMessage message = gateway.sent.get(0);
        assertEquals("Novo seguidor", message.title());
        assertEquals("Beto Aviso começou a seguir você.", message.body());
        assertEquals("NEW_FOLLOWER", message.data().get("type"));
        assertEquals(notificationId.toString(), message.data().get("notificationId"));
        assertNotNull(message.data().get("referenceId"));
        assertEquals("notification-" + notificationId, message.tag());

        QuarkusTransaction.requiringNew().run(() -> {
            assertNull(UserPushToken.findByToken(deadToken), "DeviceNotRegistered/UNREGISTERED apaga o token");
            assertNotNull(UserPushToken.findByToken(liveToken));
        });
    }

    @Test
    void alreadyReadNotificationIsNotPushed() {
        UUID notificationId = QuarkusTransaction.requiringNew().call(() -> {
            Person ana = person("Ana");
            Person beto = person("Beto");
            notificationService.registerPushToken(ana.user(), new PushTokenRegisterRequest("token-" + UUID.randomUUID(), "ANDROID"));
            Notification notification = notificationService.emitNewFollower(ana.user(), beto.user(), beto.profile().id);
            notification.readAt = Instant.now();
            return notification.id;
        });

        pushDispatchService.dispatch(notificationId);

        assertTrue(gateway.sent.isEmpty());
    }

    @Test
    void messagesOfTheSameConversationShareTheTrayTag() {
        UUID conversationId = UUID.randomUUID();
        UUID notificationId = QuarkusTransaction.requiringNew().call(() -> {
            Person ana = person("Ana");
            Person beto = person("Beto");
            notificationService.registerPushToken(ana.user(), new PushTokenRegisterRequest("token-" + UUID.randomUUID(), "ANDROID"));
            return notificationService.emitNewMessage(ana.user(), beto.user(), conversationId, beto.profile().id, "Oi!").id;
        });

        pushDispatchService.dispatch(notificationId);

        assertEquals(1, gateway.sent.size());
        assertEquals("conversation-" + conversationId, gateway.sent.get(0).tag());
        assertEquals("Oi!", gateway.sent.get(0).body());
    }

    /**
     * Responde SENT para todos, menos os tokens marcados como mortos. Estende
     * o bean real porque o {@code QuarkusMock} troca a instância do bean
     * {@code FcmPushGateway} (precisa ser atribuível a ele).
     */
    private static final class StubGateway extends FcmPushGateway {

        final List<PushMessage> sent = new CopyOnWriteArrayList<>();
        final List<String> deadTokens = new CopyOnWriteArrayList<>();

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public List<PushDeliveryResult> send(List<PushMessage> messages) {
            List<PushDeliveryResult> results = new ArrayList<>();
            for (PushMessage message : messages) {
                sent.add(message);
                results.add(deadTokens.contains(message.token())
                        ? PushDeliveryResult.invalidToken(message.token(), "UNREGISTERED")
                        : PushDeliveryResult.sent(message.token()));
            }
            return results;
        }
    }
}
