package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.dto.MatchDecisionRequest;
import br.com.unify.matchable.user.dto.MatchDecisionResponse;
import br.com.unify.matchable.user.dto.PotentialMatchesRequest;
import br.com.unify.matchable.user.entity.Gender;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Teste de integração do discovery contra Postgres real, chamando o serviço direto (sem HTTP).
 *
 * Padrão inédito no repositório: {@link TestTransaction} — cada teste roda numa transação que é
 * revertida no fim, então não há vazamento de fixture entre execuções. As três queries de
 * candidato são NATIVAS; para não depender do flush implícito do Hibernate antes de uma native
 * query, todo builder de fixture termina com {@code entityManager.flush()} explícito.
 *
 * Regra anti-flake: nenhuma asserção é sobre CONTAGEM de resultados (o banco local pode ter
 * dados de outras suítes) — só sobre presença/ausência de ids específicos desta execução.
 */
@QuarkusTest
class UserMatchDiscoveryIntegrationTest {

    private static final int GENDER_MAN = 2;
    private static final PotentialMatchesRequest NO_ALREADY_USED = new PotentialMatchesRequest(List.of());

    @Inject
    UserMatchServiceImplementation userMatchService;

    @Inject
    EntityManager entityManager;

    /** Marcador único da execução: entra no nome dos usuários criados. */
    private final String runId = UUID.randomUUID().toString();

    // ---------------------------------------------------------------- T1

    @Test
    @TestTransaction
    void declinedByCandidateIsNotOfferedAsPriorityInbound() {
        Fixture alice = createProfile("Alice");
        Fixture bruno = createProfile("Bruno");
        Fixture carla = createProfile("Carla");

        // Bruno recusa Alice; Carla convida Alice.
        userMatchService.registerDecision(bruno.user(), new MatchDecisionRequest(alice.profileId(), false));
        userMatchService.registerDecision(carla.user(), new MatchDecisionRequest(alice.profileId(), true));
        entityManager.flush();

        List<UUID> feed = userMatchService.getPotentialMatches(alice.user(), NO_ALREADY_USED);

        assertFalse(feed.contains(bruno.profileId()), "quem recusou Alice não pode voltar como convite/candidato");
        assertTrue(feed.contains(carla.profileId()), "convite legítimo continua no feed (controle positivo)");
    }

    // ---------------------------------------------------------------- T2

    @Test
    @TestTransaction
    void likingBackSomeoneWhoDeclinedWithinCooldownIsRejectedAndDoesNotCreateDeadPair() {
        Fixture alice = createProfile("Alice");
        Fixture bruno = createProfile("Bruno");

        userMatchService.registerDecision(bruno.user(), new MatchDecisionRequest(alice.profileId(), false));
        entityManager.flush();

        UserPossibleMatch pair = UserPossibleMatch.findBetween(bruno.profile(), alice.profile());
        assertNotNull(pair);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> userMatchService.registerDecision(alice.user(), new MatchDecisionRequest(bruno.profileId(), true))
        );
        assertTrue(exception.getMessage().contains("não está disponível"), exception.getMessage());

        // Estado do par lido da instância gerenciada (a transação já está rollback-only):
        // continua sendo uma recusa viva, e NÃO um par morto (false + true).
        assertEquals(bruno.profileId(), pair.starterProfile.id);
        assertFalse(pair.starterAccepted);
        assertEquals(null, pair.pendingAccepted);
        assertNotNull(pair.declinedAt);
    }

    // ---------------------------------------------------------------- T3

    @Test
    @TestTransaction
    void expiredDeclineAllowsPairToRestartAndReachMutualMatch() {
        Fixture alice = createProfile("Alice");
        Fixture bruno = createProfile("Bruno");

        userMatchService.registerDecision(bruno.user(), new MatchDecisionRequest(alice.profileId(), false));
        entityManager.flush();

        UserPossibleMatch pair = UserPossibleMatch.findBetween(bruno.profile(), alice.profile());
        assertNotNull(pair);
        UUID pairId = pair.id;
        pair.declinedAt = Instant.now().minus(Duration.ofDays(365));
        entityManager.flush();

        // Alice decide agora: a MESMA linha é reaproveitada, invertida — nunca delete
        // (V9:17 tem `on delete cascade` de conversations para user_possible_matches).
        MatchDecisionResponse restarted = userMatchService.registerDecision(
                alice.user(), new MatchDecisionRequest(bruno.profileId(), true));
        entityManager.flush();

        assertEquals(pairId, restarted.id(), "reapresentação reutiliza a linha, não cria outra");
        assertEquals(alice.profileId(), restarted.starterProfileId());
        assertTrue(restarted.starterAccepted());
        assertEquals(null, restarted.pendingAccepted());
        assertFalse(restarted.mutualMatch());

        MatchDecisionResponse mutual = userMatchService.registerDecision(
                bruno.user(), new MatchDecisionRequest(alice.profileId(), true));
        entityManager.flush();

        assertEquals(pairId, mutual.id());
        assertTrue(mutual.mutualMatch(), "par recomeçado após a carência precisa conseguir virar match");
        assertNotNull(UserPossibleMatch.findById(pairId));
    }

    // ---------------------------------------------------------------- T4

    @Test
    @TestTransaction
    void alreadySeenProfileDoesNotComeBack() {
        Fixture alice = createProfile("Alice");
        Fixture davi = createProfile("Davi");
        entityManager.flush();

        List<UUID> firstFeed = userMatchService.getPotentialMatches(alice.user(), NO_ALREADY_USED);
        assertTrue(firstFeed.contains(davi.profileId()), "controle positivo: perfil novo aparece");

        List<UUID> secondFeed = userMatchService.getPotentialMatches(
                alice.user(), new PotentialMatchesRequest(List.of(davi.profileId())));
        assertFalse(secondFeed.contains(davi.profileId()), "perfil já visto não volta");
    }

    // ---------------------------------------------------------------- T5

    @Test
    @TestTransaction
    void filterVisibleProfileIdsAppliesTheSameExclusionsToTheInboundPath() {
        Fixture alice = createProfile("Alice");
        Fixture bruno = createProfile("Bruno");
        Fixture carla = createProfile("Carla");
        Fixture davi = createProfile("Davi");

        userMatchService.registerDecision(bruno.user(), new MatchDecisionRequest(alice.profileId(), false));
        userMatchService.registerDecision(carla.user(), new MatchDecisionRequest(alice.profileId(), true));
        entityManager.flush();

        List<UUID> visible = userMatchService.filterVisibleProfileIds(
                alice.user(), List.of(bruno.profileId(), carla.profileId(), davi.profileId(), alice.profileId()));

        assertFalse(visible.contains(bruno.profileId()), "recusa em carência é excluída");
        assertFalse(visible.contains(alice.profileId()), "o próprio perfil nunca é visível");
        assertTrue(visible.contains(carla.profileId()), "convite recebido não é excluído");
        assertTrue(visible.contains(davi.profileId()), "perfil sem relação alguma continua visível");
    }

    // ---------------------------------------------------------------- fixture

    private record Fixture(User user, UserProfile profile) {
        UUID profileId() {
            return profile.id;
        }
    }

    /**
     * Fixture mínima exigida pelas queries de candidato: usuário verificado, com data de
     * nascimento dentro da faixa etária default (18..99) e perfil COM gênero — perfil sem
     * gênero é filtrado por `up.fk_gender in (...) or up.fk_gender = 4`.
     *
     * Nenhuma coordenada é criada: o viewer cai no modo `no-location` (permitido por
     * unify.match.allow-discovery-without-location=true) e os candidatos entram pela cota de
     * perfis sem coordenada. Sem preferências: o serviço aplica defaults em memória.
     */
    private Fixture createProfile(String name) {
        User user = new User();
        user.id = UUIDv7Generator.generate();
        user.name = name + "-" + runId;
        user.lastName = "Discovery";
        user.email = "discovery-" + UUID.randomUUID() + "@example.com";
        user.password = "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvwxyz01234";
        user.birthdate = LocalDate.now().minusYears(28);
        user.verified = true;
        user.lastUpdatedAt = Instant.now();
        user.persist();

        UserProfile profile = new UserProfile();
        profile.id = UUIDv7Generator.generate();
        profile.user = user;
        profile.bio = "Perfil de teste do discovery " + runId;
        profile.gender = Gender.findById(GENDER_MAN);
        profile.persist();

        // Native query não declara query spaces: garante o flush em vez de confiar no implícito.
        entityManager.flush();

        return new Fixture(user, profile);
    }
}
