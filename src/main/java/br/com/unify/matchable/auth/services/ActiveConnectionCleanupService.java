package br.com.unify.matchable.auth.services;

import br.com.unify.matchable.auth.entity.ActiveConnection;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class ActiveConnectionCleanupService {

    @ConfigProperty(name = "unify.auth.revoked-connection-retention-days", defaultValue = "7")
    long retentionDays;

    @Scheduled(every = "1h")
    @Transactional
    void deleteRevokedOrExpiredConnections() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        ActiveConnection.deleteRevokedOrExpiredBefore(cutoff);
    }
}
