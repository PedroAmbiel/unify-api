package br.com.unify.matchable.notification.services;

import static br.com.unify.matchable.notification.NotificationFixtures.block;
import static br.com.unify.matchable.notification.NotificationFixtures.notificationsOf;
import static br.com.unify.matchable.notification.NotificationFixtures.person;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.notification.NotificationFixtures.Person;
import br.com.unify.matchable.notification.dto.NotificationResponse;
import br.com.unify.matchable.notification.dto.PushTokenRegisterRequest;
import br.com.unify.matchable.notification.dto.PushTokenResponse;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.entity.UserPushToken;
import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.notification.enums.PushPlatform;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Regras do serviço no banco real (transação revertida por teste).
 *
 * <p>ATENÇÃO: exceção lançada por método {@code @Transactional} aborta a
 * {@code @TestTransaction}; por isso cada {@code assertThrows} é a ÚLTIMA
 * coisa do teste.
 */
@QuarkusTest
class NotificationServiceImplementationTest {

    @Inject
    NotificationService service;

    @Test
    @TestTransaction
    void neverNotifiesTheActorItselfNorABlockedPair() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        Person caio = person("Caio");
        block(caio.user(), ana.user());

        assertNull(service.emitPostLiked(ana.user(), ana.user(), UUIDv7Generator.generate(), null), "curtir o próprio post");
        assertNull(service.emitNewFollower(ana.user(), caio.user(), caio.profile().id), "bloqueio vale nos dois sentidos");
        assertNull(service.emitNewFollower(caio.user(), ana.user(), ana.profile().id));

        Notification created = service.emitNewFollower(ana.user(), beto.user(), beto.profile().id);
        assertNotNull(created);
        assertEquals(NotificationType.NEW_FOLLOWER, created.type);
        assertEquals(beto.profile().id, created.referenceId, "referência = perfil de quem seguiu (rota /users/[id])");
        assertEquals("Beto Aviso começou a seguir você.", created.body);
        assertEquals(1, notificationsOf(ana.user()).size());
    }

    @Test
    @TestTransaction
    void sameActorAndTargetIsDeduplicatedInsideTheWindow() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        UUID postId = UUIDv7Generator.generate();

        assertNotNull(service.emitPostLiked(ana.user(), beto.user(), postId, null));
        // curtir → descurtir → curtir de novo: um aviso só.
        assertNull(service.emitPostLiked(ana.user(), beto.user(), postId, null));
        // Outro post (ou outra pessoa) é outro aviso.
        assertNotNull(service.emitPostLiked(ana.user(), beto.user(), UUIDv7Generator.generate(), null));
        // Comentário é conteúdo novo: nunca deduplica.
        assertNotNull(service.emitPostCommented(ana.user(), beto.user(), postId, null, "Que legal!"));
        assertNotNull(service.emitPostCommented(ana.user(), beto.user(), postId, null, "Que legal!"));

        assertEquals(4, notificationsOf(ana.user()).size());
    }

    @Test
    @TestTransaction
    void unreadMessagesOfTheSameConversationGroupIntoOneNotification() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        UUID conversationId = UUIDv7Generator.generate();

        Notification first = service.emitNewMessage(ana.user(), beto.user(), conversationId, beto.profile().id, "Oi!");
        service.emitNewMessage(ana.user(), beto.user(), conversationId, beto.profile().id, "Tudo bem?");
        Notification third = service.emitNewMessage(ana.user(), beto.user(), conversationId, beto.profile().id, "Vamos ao cinema?");

        assertEquals(first.id, third.id, "mesma linha enquanto não lida");
        List<Notification> messages = notificationsOf(ana.user(), NotificationType.NEW_MESSAGE);
        assertEquals(1, messages.size());
        assertEquals(3, messages.get(0).eventCount);
        assertEquals("Beto Aviso", messages.get(0).title);
        assertEquals("3 novas mensagens. Última: Vamos ao cinema?", messages.get(0).body);
        assertEquals(beto.profile().id, messages.get(0).contextId);

        // Abriu a conversa: o aviso sai do sino e a próxima mensagem abre outro.
        service.markReadByReference(ana.user(), NotificationType.NEW_MESSAGE, conversationId);
        assertEquals(0, service.getUnreadCount(ana.user()).unreadCount());

        Notification next = service.emitNewMessage(ana.user(), beto.user(), conversationId, beto.profile().id, "Oi de novo");
        assertFalse(next.id.equals(first.id));
        assertEquals(1, next.eventCount);
        assertEquals("Oi de novo", next.body);
    }

    @Test
    @TestTransaction
    void pageAndUnreadCountHideNotificationsFromBlockedPeople() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        Person caio = person("Caio");
        service.emitNewFollower(ana.user(), beto.user(), beto.profile().id);
        service.emitNewFollower(ana.user(), caio.user(), caio.profile().id);
        service.emitReportUpdate(ana.user(), UUIDv7Generator.generate(), "resolvida");

        assertEquals(3, service.getUnreadCount(ana.user()).unreadCount());

        block(ana.user(), caio.user());

        PageResponse<NotificationResponse> page = service.getPage(ana.user(), 0, 20);
        assertEquals(2, page.totalElements());
        assertTrue(page.content().stream().noneMatch(item -> item.actor() != null
                && caio.user().id.equals(item.actor().userId())));
        assertEquals(2, service.getUnreadCount(ana.user()).unreadCount());

        NotificationResponse report = page.content().stream()
                .filter(item -> item.type() == NotificationType.REPORT_UPDATE)
                .findFirst()
                .orElseThrow();
        assertNull(report.actor(), "aviso do sistema não tem ator");

        NotificationResponse follower = page.content().stream()
                .filter(item -> item.type() == NotificationType.NEW_FOLLOWER)
                .findFirst()
                .orElseThrow();
        assertEquals(beto.profile().id, follower.actor().userProfileId());
        assertEquals("Beto Aviso", follower.actor().name());
        assertNull(follower.actor().avatarUrl(), "sem foto ativa: sem URL (nada de 404 no app)");
    }

    @Test
    @TestTransaction
    void markAsReadAndMarkAllAsRead() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        Notification first = service.emitNewFollower(ana.user(), beto.user(), beto.profile().id);
        service.emitPostLiked(ana.user(), beto.user(), UUIDv7Generator.generate(), null);
        service.emitPostLiked(ana.user(), beto.user(), UUIDv7Generator.generate(), null);

        NotificationResponse read = service.markAsRead(ana.user(), first.id);
        assertTrue(read.read());
        assertEquals(2, service.getUnreadCount(ana.user()).unreadCount());

        assertEquals(2, service.markAllAsRead(ana.user()).updatedCount());
        assertEquals(0, service.getUnreadCount(ana.user()).unreadCount());
        assertEquals(0, service.markAllAsRead(ana.user()).updatedCount(), "idempotente");
    }

    @Test
    @TestTransaction
    void notificationOfAnotherPersonIsNotFound() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        Notification forAna = service.emitNewFollower(ana.user(), beto.user(), beto.profile().id);

        assertThrows(NoSuchElementException.class, () -> service.markAsRead(beto.user(), forAna.id));
    }

    @Test
    @TestTransaction
    void sameDeviceTokenChangesOwnerInsteadOfDuplicating() {
        Person ana = person("Ana");
        Person beto = person("Beto");

        PushTokenResponse first = service.registerPushToken(ana.user(), new PushTokenRegisterRequest(" fcm-token-1 ", "android"));
        assertEquals(PushPlatform.ANDROID, first.platform());

        PushTokenResponse again = service.registerPushToken(beto.user(), new PushTokenRegisterRequest("fcm-token-1", "ANDROID"));

        assertEquals(first.id(), again.id(), "mesma linha: o token é do aparelho");
        assertEquals(1L, UserPushToken.count("token", "fcm-token-1"));
        assertEquals(beto.user().id, UserPushToken.findByToken("fcm-token-1").user.id);
        assertTrue(UserPushToken.listByUser(ana.user()).isEmpty());
    }

    /**
     * No primeiro boot o app manda o mesmo token duas vezes quase juntas
     * (registro inicial + listener de rotação). Cada chamada roda na própria
     * transação, como duas requisições: nenhuma pode estourar
     * uq_user_push_tokens_token. Sem {@code @TestTransaction} porque as
     * transações precisam ser independentes (dados ficam commitados).
     *
     * <p>Corrida depende de timing: aquece uma conexão por thread antes e
     * repete várias rodadas para a disputa acontecer de fato.
     */
    @Test
    void concurrentRegistrationOfTheSameTokenKeepsOneRow() throws Exception {
        Person ana = QuarkusTransaction.requiringNew().call(() -> person("Ana"));
        int threads = 8;
        int rounds = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            raceRegistrations(executor, threads, i -> "fcm-warmup-" + UUID.randomUUID(), ana);

            for (int round = 0; round < rounds; round++) {
                String token = "fcm-race-" + UUID.randomUUID();
                Set<UUID> ids = raceRegistrations(executor, threads, i -> token, ana);

                assertEquals(1, ids.size(), "todas as chamadas devolvem a mesma linha");
                assertEquals(1L, QuarkusTransaction.requiringNew().call(() -> UserPushToken.count("token", token)));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Set<UUID> raceRegistrations(
            ExecutorService executor, int threads, IntFunction<String> tokenOf, Person owner) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PushTokenResponse>> responses = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String token = tokenOf.apply(i);
            responses.add(executor.submit(() -> {
                start.await();
                return service.registerPushToken(owner.user(), new PushTokenRegisterRequest(token, "ANDROID"));
            }));
        }
        start.countDown();

        Set<UUID> ids = new HashSet<>();
        for (Future<PushTokenResponse> response : responses) {
            ids.add(response.get(30, TimeUnit.SECONDS).id());
        }
        return ids;
    }

    @Test
    @TestTransaction
    void removePushTokenOnlyRemovesMyOwnToken() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        PushTokenResponse anaToken = service.registerPushToken(ana.user(), new PushTokenRegisterRequest("fcm-ana", "ANDROID"));
        PushTokenResponse betoToken = service.registerPushToken(beto.user(), new PushTokenRegisterRequest("fcm-beto", "ANDROID"));

        service.removePushToken(ana.user(), anaToken.id());
        assertNull(UserPushToken.findByToken("fcm-ana"));

        assertThrows(NoSuchElementException.class, () -> service.removePushToken(ana.user(), betoToken.id()));
    }

    @Test
    @TestTransaction
    void registerRejectsUnknownPlatform() {
        Person ana = person("Ana");

        assertThrows(IllegalArgumentException.class,
                () -> service.registerPushToken(ana.user(), new PushTokenRegisterRequest("fcm-token", "WINDOWS_PHONE")));
    }
}
