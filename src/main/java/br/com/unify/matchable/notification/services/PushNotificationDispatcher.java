package br.com.unify.matchable.notification.services;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Ponte entre a transação de negócio e a entrega do push.
 *
 * <p>{@code during = AFTER_SUCCESS}: o observer só roda se a transação que
 * criou a notificação COMMITAR (rollback = nenhum push). Ele ainda roda na
 * thread da requisição, então só agenda o trabalho numa virtual thread (Java
 * 25, sem pool extra) — a resposta HTTP de quem curtiu/mandou mensagem não
 * espera o FCM.
 */
@ApplicationScoped
public class PushNotificationDispatcher {

    private static final Logger LOG = Logger.getLogger(PushNotificationDispatcher.class);

    @ConfigProperty(name = "unify.push.enabled", defaultValue = "true")
    boolean pushEnabled;

    @Inject
    PushDispatchService pushDispatchService;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    void onNotification(@Observes(during = TransactionPhase.AFTER_SUCCESS) NotificationDispatchEvent event) {
        if (!pushEnabled || event == null || event.notificationId() == null) {
            return;
        }

        executor.submit(() -> {
            try {
                pushDispatchService.dispatch(event.notificationId());
            } catch (Exception exception) {
                LOG.warn("Falha ao entregar push da notificação " + event.notificationId(), exception);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
