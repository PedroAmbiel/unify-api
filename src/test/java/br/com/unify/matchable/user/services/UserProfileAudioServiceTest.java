package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.media.AudioContent;
import br.com.unify.matchable.user.dto.UserProfileAudioResponse;
import br.com.unify.matchable.user.dto.UserPublicProfileResponse;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserBlock;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.entity.UserProfileAudio;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** Frente D — áudio de apresentação no banco real (transação revertida por teste). */
@QuarkusTest
class UserProfileAudioServiceTest {

    private static final byte[] AUDIO = new byte[] { 0, 0, 0, 24, 102, 116, 121, 112, 77, 52, 65, 32 };

    @Inject
    UserProfileService service;

    @Test
    @TestTransaction
    void uploadCreatesAndSecondUploadReplacesWithNewId() {
        Fixture ana = fixture("Ana", true);

        UserProfileAudioResponse first = service.uploadPresentationAudio(ana.user, "audio/m4a", AUDIO, 30);
        assertEquals(30, first.durationSeconds());
        assertEquals("audio/m4a", first.contentType());
        assertEquals("/users/me/profile/audio/" + first.id(), first.url());
        assertEquals(first.id(), service.getProfile(ana.user).presentationAudio().id());

        UserProfileAudioResponse second = service.uploadPresentationAudio(ana.user, "audio/mp4; codecs=mp4a", AUDIO, 45);
        assertNotEquals(first.id(), second.id(), "substituir gera id novo (URL imutável)");
        assertEquals("audio/mp4", second.contentType());
        assertEquals(1, UserProfileAudio.count("userProfile", ana.profile));
        assertEquals(second.id(), service.getPresentationAudio(ana.user).audio().id());

        AudioContent content = service.getPresentationAudioContent(ana.user, second.id());
        assertArrayEquals(AUDIO, content.bytes());
        assertEquals("audio/mp4", content.contentType());
        assertThrows(NoSuchElementException.class, () -> service.getPresentationAudioContent(ana.user, first.id()));
    }

    @Test
    @TestTransaction
    void invalidAudioIsRejectedAndNothingIsPersisted() {
        Fixture ana = fixture("Ana", true);
        assertNull(service.getPresentationAudio(ana.user).audio());

        // A validação roda antes de qualquer acesso ao banco (nada é persistido). Sem consulta
        // depois dos assertThrows: a exceção deixa a transação do teste abortada.
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadPresentationAudio(ana.user, "audio/m4a", AUDIO, 61));
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadPresentationAudio(ana.user, "audio/m4a", AUDIO, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadPresentationAudio(ana.user, "audio/ogg", AUDIO, 10));
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadPresentationAudio(ana.user, "audio/m4a", new byte[0], 10));
    }

    @Test
    @TestTransaction
    void deleteRemovesTheRowAndSecondDeleteIsNotFound() {
        Fixture ana = fixture("Ana", true);
        service.uploadPresentationAudio(ana.user, "audio/m4a", AUDIO, 12);

        service.deletePresentationAudio(ana.user);

        assertEquals(0, UserProfileAudio.count("userProfile", ana.profile));
        assertNull(service.getProfile(ana.user).presentationAudio());
        assertThrows(NoSuchElementException.class, () -> service.deletePresentationAudio(ana.user));
    }

    @Test
    @TestTransaction
    void publicAudioFollowsThePublicProfileGuard() {
        Fixture viewer = fixture("Vera", true);
        Fixture owner = fixture("Otto", true);
        UUID ownerAudioId = service.uploadPresentationAudio(owner.user, "audio/aac", AUDIO, 20).id();

        UserPublicProfileResponse publicProfile = service.getPublicProfile(viewer.user, owner.profile.id);
        assertNotNull(publicProfile.presentationAudio());
        assertEquals(ownerAudioId, publicProfile.presentationAudio().id());
        assertEquals(20, publicProfile.presentationAudio().durationSeconds());
        assertArrayEquals(AUDIO,
                service.getPublicPresentationAudioContent(viewer.user, owner.profile.id, ownerAudioId).bytes());

        UserBlock block = new UserBlock();
        block.id = UUIDv7Generator.generate();
        block.blocker = owner.user;
        block.blocked = viewer.user;
        block.createdAt = Instant.now();
        block.persist();

        assertThrows(NoSuchElementException.class,
                () -> service.getPublicPresentationAudioContent(viewer.user, owner.profile.id, ownerAudioId));
    }

    @Test
    @TestTransaction
    void publicAudioOfUnverifiedProfileIsNotFound() {
        Fixture viewer = fixture("Vera", true);
        Fixture unverified = fixture("Nico", false);
        UUID audioId = service.uploadPresentationAudio(unverified.user, "audio/aac", AUDIO, 20).id();

        assertThrows(NoSuchElementException.class,
                () -> service.getPublicPresentationAudioContent(viewer.user, unverified.profile.id, audioId));
    }

    @Test
    @TestTransaction
    void publicAudioIdFromAnotherProfileIsNotFound() {
        Fixture viewer = fixture("Vera", true);
        Fixture owner = fixture("Otto", true);
        Fixture other = fixture("Rita", true);
        service.uploadPresentationAudio(owner.user, "audio/aac", AUDIO, 20);
        UUID otherAudioId = service.uploadPresentationAudio(other.user, "audio/aac", AUDIO, 20).id();

        assertThrows(NoSuchElementException.class,
                () -> service.getPublicPresentationAudioContent(viewer.user, owner.profile.id, otherAudioId));
    }

    // ------------------------------------------------------------ fixtures

    private record Fixture(User user, UserProfile profile) {
    }

    private static Fixture fixture(String name, boolean verified) {
        User user = new User();
        user.id = UUIDv7Generator.generate();
        user.name = name;
        user.lastName = "Audio";
        user.email = "audio-" + UUID.randomUUID() + "@example.com";
        user.password = "hash";
        user.birthdate = LocalDate.now().minusYears(30);
        user.verified = verified;
        user.lastUpdatedAt = Instant.now();
        user.persist();

        UserProfile profile = new UserProfile();
        profile.id = UUIDv7Generator.generate();
        profile.user = user;
        profile.bio = "bio";
        profile.persist();
        user.profile = profile;
        return new Fixture(user, profile);
    }
}
