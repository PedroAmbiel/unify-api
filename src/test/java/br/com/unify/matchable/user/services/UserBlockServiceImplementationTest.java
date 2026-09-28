package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.social.entity.UserFollow;
import br.com.unify.matchable.user.dto.UserBlockResponse;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** Efeitos colaterais do bloqueio no banco real (transação revertida por teste). */
@QuarkusTest
class UserBlockServiceImplementationTest {

    @Inject
    UserBlockService service;

    @Test
    @TestTransaction
    void blockRemovesFollowsInBothDirections() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        follow(ana.profile, beto.profile);
        follow(beto.profile, ana.profile);

        UserBlockResponse response = service.blockUser(ana.user, beto.profile.id);

        assertEquals(beto.profile.id, response.userProfileId());
        assertEquals("Beto Bloqueio", response.name());
        assertNull(UserFollow.findPair(ana.profile, beto.profile));
        assertNull(UserFollow.findPair(beto.profile, ana.profile));
        assertTrue(service.isBlockedEitherDirection(ana.user.id, beto.user.id));
        assertTrue(service.isBlockedEitherDirection(beto.user.id, ana.user.id), "o efeito é simétrico");
    }

    @Test
    @TestTransaction
    void blockClosesPendingMatchWithoutDeletingTheRow() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        UserPossibleMatch pending = new UserPossibleMatch();
        pending.id = UUIDv7Generator.generate();
        pending.starterProfile = beto.profile;
        pending.pendingProfile = ana.profile;
        pending.createdAt = Instant.now();
        pending.starterAccepted = true;
        pending.persist();

        service.blockUser(ana.user, beto.profile.id);

        // Nunca delete em user_possible_matches (V9: cascade para conversations): vira recusa.
        UserPossibleMatch after = UserPossibleMatch.findById(pending.id);
        assertNotNull(after);
        assertNotNull(after.declinedAt);
        assertEquals(ana.profile.id, after.declinedByProfile.id);
        assertTrue(UserPossibleMatch.listInboundPending(ana.profile).isEmpty());
    }

    // Exceção dentro de um método @Transactional deixa a transação do teste abortada: cada
    // teste abaixo termina no assertThrows que toca o banco, sem acesso ao banco depois dele.

    @Test
    @TestTransaction
    void blockTwiceConflicts() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");

        service.blockUser(ana.user, beto.profile.id);
        IllegalStateException duplicate = assertThrows(
                IllegalStateException.class, () -> service.blockUser(ana.user, beto.profile.id));
        assertEquals(UserBlockServiceImplementation.ALREADY_BLOCKED_MESSAGE, duplicate.getMessage());
    }

    @Test
    @TestTransaction
    void blockRejectsMissingTargetAndSelf() {
        Fixture ana = fixture("Ana");

        assertThrows(IllegalArgumentException.class, () -> service.blockUser(ana.user, null));
        IllegalArgumentException self = assertThrows(
                IllegalArgumentException.class, () -> service.blockUser(ana.user, ana.profile.id));
        assertEquals(UserBlockServiceImplementation.SELF_BLOCK_MESSAGE, self.getMessage());
    }

    @Test
    @TestTransaction
    void blockUnknownProfileIsNotFound() {
        Fixture ana = fixture("Ana");

        assertThrows(NoSuchElementException.class, () -> service.blockUser(ana.user, UUID.randomUUID()));
    }

    @Test
    @TestTransaction
    void listAndRelatedIdsSeparateDirectionFromEffect() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        Fixture caio = fixture("Caio");

        service.blockUser(ana.user, beto.profile.id);
        service.blockUser(caio.user, ana.profile.id);

        assertEquals(1, service.listBlocked(ana.user).size(), "só quem EU bloqueei");
        assertEquals(beto.profile.id, service.listBlocked(ana.user).get(0).userProfileId());
        assertEquals(Set.of(beto.user.id, caio.user.id), service.relatedUserIds(ana.user.id));

        service.unblockUser(ana.user, beto.profile.id);
        assertFalse(service.isBlockedEitherDirection(ana.user.id, beto.user.id));
        // Desbloquear não mexe no bloqueio que OUTRA pessoa fez.
        assertTrue(service.isBlockedEitherDirection(ana.user.id, caio.user.id));
        assertThrows(NoSuchElementException.class, () -> service.unblockUser(ana.user, beto.profile.id));
    }

    // ------------------------------------------------------------ fixtures

    private record Fixture(User user, UserProfile profile) {
    }

    private static void follow(UserProfile follower, UserProfile followed) {
        UserFollow follow = new UserFollow();
        follow.id = UUIDv7Generator.generate();
        follow.follower = follower;
        follow.followed = followed;
        follow.createdAt = Instant.now();
        follow.persist();
    }

    private static Fixture fixture(String name) {
        User user = new User();
        user.id = UUIDv7Generator.generate();
        user.name = name;
        user.lastName = "Bloqueio";
        user.email = "block-" + UUID.randomUUID() + "@example.com";
        user.password = "hash";
        user.birthdate = LocalDate.now().minusYears(30);
        user.verified = true;
        user.lastUpdatedAt = Instant.now();
        user.persist();

        UserProfile profile = new UserProfile();
        profile.id = UUIDv7Generator.generate();
        profile.user = user;
        profile.bio = "bio";
        profile.persist();
        return new Fixture(user, profile);
    }
}
