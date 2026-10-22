package br.com.unify.matchable.user.services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Marca o dia de uso de cada pessoa em {@code user_activity_days} (V36), base
 * dos indicadores de atividade do dashboard. Chamado em toda requisição
 * autenticada (BannedUserFilter), mas grava no máximo uma vez por usuário por
 * dia em cada instância: a memória guarda o último dia gravado e o banco
 * ignora repetidos ({@code on conflict do nothing}).
 *
 * Falha ao gravar nunca derruba a requisição: a métrica perde um ponto, o app
 * segue funcionando.
 */
@ApplicationScoped
public class UserActivityTracker {

    private static final Logger LOG = Logger.getLogger(UserActivityTracker.class);

    /** Mesmo fuso das séries do dashboard. */
    static final ZoneId LOCAL_ZONE = ZoneId.of("America/Sao_Paulo");

    /** Teto da memória de dedupe; ao passar, recomeça (custo: uma escrita repetida por pessoa). */
    static final int MAX_TRACKED_USERS = 50_000;

    private final Map<UUID, LocalDate> lastRecordedDay = new ConcurrentHashMap<>();

    @Inject
    EntityManager entityManager;

    Clock clock = Clock.system(LOCAL_ZONE);

    public void recordActivity(UUID userId) {
        if (userId == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate previous = lastRecordedDay.put(userId, today);
        if (today.equals(previous)) {
            return;
        }
        if (lastRecordedDay.size() > MAX_TRACKED_USERS) {
            lastRecordedDay.clear();
            lastRecordedDay.put(userId, today);
        }

        try {
            QuarkusTransaction.requiringNew().run(() -> entityManager
                    .createNativeQuery("insert into user_activity_days (fk_user, activity_date) "
                            + "values (:userId, :day) on conflict do nothing")
                    .setParameter("userId", userId)
                    .setParameter("day", today)
                    .executeUpdate());
        } catch (RuntimeException exception) {
            lastRecordedDay.remove(userId, today);
            LOG.debugf(exception, "Não foi possível registrar a atividade do usuário %s", userId);
        }
    }
}
