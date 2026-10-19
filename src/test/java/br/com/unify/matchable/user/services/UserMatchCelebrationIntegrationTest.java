package br.com.unify.matchable.user.services;

import static br.com.unify.matchable.notification.NotificationFixtures.block;
import static br.com.unify.matchable.notification.NotificationFixtures.notificationsOf;
import static br.com.unify.matchable.notification.NotificationFixtures.person;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.notification.NotificationFixtures.Person;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.user.dto.MatchDecisionRequest;
import br.com.unify.matchable.user.dto.MatchDecisionResponse;
import br.com.unify.matchable.user.dto.MutualMatchSummaryResponse;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Fila da tela "Deu match": quem curtiu primeiro também vê a comemoração, uma
 * única vez, e quem respondeu (que já viu na resposta do POST) nunca a recebe
 * de novo.
 */
@QuarkusTest
class UserMatchCelebrationIntegrationTest {

    @Inject
    UserMatchService userMatchService;

    @Test
    @TestTransaction
    void onlyTheFirstLikerIsQueuedOnceThePairBecomesMutual() {
        Person ana = person("Ana");
        Person beto = person("Beto");

        userMatchService.registerDecision(ana.user(), new MatchDecisionRequest(beto.profile().id, true));
        assertTrue(userMatchService.getUnseenMutualMatches(ana.user()).isEmpty(), "curtida sem reciprocidade não comemora");

        MatchDecisionResponse mutual = userMatchService.registerDecision(
                beto.user(), new MatchDecisionRequest(ana.profile().id, true));

        List<MutualMatchSummaryResponse> forAna = userMatchService.getUnseenMutualMatches(ana.user());
        assertEquals(1, forAna.size());
        assertEquals(mutual.id(), forAna.get(0).matchId());
        assertEquals(beto.profile().id, forAna.get(0).userProfileId(), "a tela mostra a outra pessoa");
        assertEquals("Beto Aviso", forAna.get(0).fullName());
        assertTrue(userMatchService.getUnseenMutualMatches(beto.user()).isEmpty(),
                "quem respondeu já viu a comemoração na resposta do POST");

        UserPossibleMatch stored = UserPossibleMatch.findById(mutual.id());
        assertNotNull(stored.matchedAt);
        assertNotNull(stored.pendingMatchSeenAt);
    }

    @Test
    @TestTransaction
    void seenMatchLeavesTheQueueForGoodAndClearsTheBell() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        UUID matchId = mutualMatch(ana, beto);

        userMatchService.markMutualMatchSeen(ana.user(), matchId);
        userMatchService.markMutualMatchSeen(ana.user(), matchId);

        assertTrue(userMatchService.getUnseenMutualMatches(ana.user()).isEmpty());
        Notification forAna = notificationsOf(ana.user(), NotificationType.NEW_MATCH).get(0);
        // UPDATE em lote não atualiza a entidade já carregada na sessão: confere no banco.
        assertEquals(1L, Notification.count("id = ?1 and readAt is not null", forAna.id));
    }

    @Test
    @TestTransaction
    void respondentCanAlsoConfirmWithoutSideEffectsOnTheFirstLiker() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        UUID matchId = mutualMatch(ana, beto);

        userMatchService.markMutualMatchSeen(beto.user(), matchId);

        assertEquals(1, userMatchService.getUnseenMutualMatches(ana.user()).size(),
                "a confirmação de um lado não apaga a comemoração do outro");
    }

    @Test
    @TestTransaction
    void strangersAndPendingLikesCannotBeMarkedAsSeen() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        Person caio = person("Caio");
        UUID matchId = mutualMatch(ana, beto);
        MatchDecisionResponse pending = userMatchService.registerDecision(
                caio.user(), new MatchDecisionRequest(ana.profile().id, true));

        assertThrows(NoSuchElementException.class, () -> userMatchService.markMutualMatchSeen(caio.user(), matchId));
        assertThrows(NoSuchElementException.class, () -> userMatchService.markMutualMatchSeen(caio.user(), pending.id()));
        assertThrows(NoSuchElementException.class, () -> userMatchService.markMutualMatchSeen(ana.user(), UUID.randomUUID()));
        assertEquals(1, userMatchService.getUnseenMutualMatches(ana.user()).size());
    }

    @Test
    @TestTransaction
    void blockedPairIsNotCelebrated() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        mutualMatch(ana, beto);

        block(beto.user(), ana.user());

        assertTrue(userMatchService.getUnseenMutualMatches(ana.user()).isEmpty());
    }

    @Test
    @TestTransaction
    void mutualMatchesFromBeforeTheQueueExistedAreNeverCelebrated() {
        Person ana = person("Ana");
        Person beto = person("Beto");

        // Linha como a V23 deixa os matches antigos sem backfill (matched_at nulo).
        UserPossibleMatch legacy = new UserPossibleMatch();
        legacy.id = UUIDv7Generator.generate();
        legacy.starterProfile = ana.profile();
        legacy.pendingProfile = beto.profile();
        legacy.createdAt = Instant.now();
        legacy.starterAccepted = true;
        legacy.pendingAccepted = true;
        legacy.persistAndFlush();

        assertTrue(userMatchService.getUnseenMutualMatches(ana.user()).isEmpty());
        assertTrue(userMatchService.getUnseenMutualMatches(beto.user()).isEmpty());
    }

    @Test
    @TestTransaction
    void queueComesOldestFirst() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        Person caio = person("Caio");
        UUID first = mutualMatch(ana, beto);
        UUID second = mutualMatch(ana, caio);

        List<MutualMatchSummaryResponse> forAna = userMatchService.getUnseenMutualMatches(ana.user());

        assertEquals(List.of(first, second), forAna.stream().map(MutualMatchSummaryResponse::matchId).toList());
    }

    /** {@code starter} curte primeiro, {@code respondent} fecha o match. */
    private UUID mutualMatch(Person starter, Person respondent) {
        userMatchService.registerDecision(starter.user(), new MatchDecisionRequest(respondent.profile().id, true));
        return userMatchService.registerDecision(
                respondent.user(), new MatchDecisionRequest(starter.profile().id, true)).id();
    }
}
