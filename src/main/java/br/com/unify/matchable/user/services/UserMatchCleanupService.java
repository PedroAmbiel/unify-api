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

    /**
     * Retenção da linha de recusa. É MAIOR que a carência de propósito: passada a carência
     * o perfil volta ao feed, mas penalizado (MatchScoringPolicy.PENALTY_RESHOWN_AFTER_COOLDOWN),
     * e essa penalidade depende da linha ainda existir. Apagar pela carência anulava a penalidade.
     */
    @ConfigProperty(name = "unify.match.decline-retention-days", defaultValue = "180")
    int declineRetentionDays;

    /**
     * Apaga apenas recusas antigas o bastante para não influenciarem mais o ranking.
     *
     * ANTES: delete("pendingAccepted = false") a cada 10h — o que fazia todo perfil recusado
     * voltar ao feed em no máximo 10 horas, porque a exclusão do feed depende da linha existir.
     */
    @Scheduled(every = "24h", identity = "expired-match-declines-cleanup")
    @Transactional
    void deleteExpiredDeclines() {
        Instant threshold = Instant.now().minus(Duration.ofDays(Math.max(0, declineRetentionDays)));
        long removed = UserPossibleMatch.deleteExpiredDeclines(threshold);
        if (removed > 0) {
            LOG.infof("Removidas %d recusas de match fora da janela de retenção (%d dias).",
                    removed, declineRetentionDays);
        }
    }
}
