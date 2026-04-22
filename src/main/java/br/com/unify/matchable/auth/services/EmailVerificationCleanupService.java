package br.com.unify.matchable.auth.services;

import br.com.unify.matchable.auth.entity.EmailVerificationCode;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class EmailVerificationCleanupService {

    @Scheduled(every = "1h")
    @Transactional
    void deleteExpiredCodes() {
        EmailVerificationCode.deleteExpired(Instant.now());
    }
}