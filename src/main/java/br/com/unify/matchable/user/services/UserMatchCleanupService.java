package br.com.unify.matchable.user.services;

import br.com.unify.matchable.user.entity.UserPossibleMatch;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserMatchCleanupService {

    @Scheduled(every = "10h")
    @Transactional
    void deleteDeclinedPendingMatches() {
        UserPossibleMatch.deleteDeclinedPendingMatches();
    }
}