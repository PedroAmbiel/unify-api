package br.com.unify.matchable.auth.services;

import br.com.unify.matchable.auth.entity.PasswordResetToken;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class PasswordResetCleanupService {

    @Scheduled(every = "1h")
    @Transactional
    void deleteExpiredTokens() {
        PasswordResetToken.deleteExpired(Instant.now());
    }
}