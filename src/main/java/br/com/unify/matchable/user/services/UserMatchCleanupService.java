package br.com.unify.matchable.user.services;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.unify.matchable.user.entity.UserPossibleMatch;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserMatchCleanupService {

    private static final Logger LOG = Logger.getLogger(UserMatchCleanupService.class);

    @ConfigProperty(name = "unify.match.decline-cooldown-days", defaultValue = "30")
    int declineCooldownDays;

    /**
     * Apaga apenas recusas cuja carência já expirou.
     *
     * ANTES: delete("pendingAccepted = false") a cada 10h — o que fazia todo perfil recusado
     * voltar ao feed em no máximo 10 horas, porque a exclusão do feed depende da linha existir.
     */
    @Scheduled(every = "24h", identity = "expired-match-declines-cleanup")
    @Transactional
    void deleteExpiredDeclines() {
        Instant threshold = Instant.now().minus(Duration.ofDays(declineCooldownDays));
        long removed = UserPossibleMatch.deleteExpiredDeclines(threshold);
        if (removed > 0) {
            LOG.infof("Removidas %d recusas de match expiradas (carência de %d dias).",
                    removed, declineCooldownDays);
        }
    }
}
