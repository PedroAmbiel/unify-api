package br.com.unify.matchable.social.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.exceptions.ForbiddenException;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.social.dto.FollowActionResponse;
import br.com.unify.matchable.social.dto.FollowPageResponse;
import br.com.unify.matchable.social.dto.FollowStatsResponse;
import br.com.unify.matchable.social.dto.UserFeedPageResponse;
import br.com.unify.matchable.social.dto.UserPostResponse;
import br.com.unify.matchable.social.entity.UserFollow;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** Integração com banco real, transação revertida por teste (ver UserReportServiceImplementationTest). */
@QuarkusTest
class SocialServiceImplementationTest {

    @Inject
    SocialService service;

    // ------------------------------------------------------------- follow

    @Test
    @TestTransaction
    void follow_deveSerIdempotente_quandoJaSegue() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");

        FollowActionResponse first = service.follow(ana.user, beto.profile.id);
        FollowActionResponse second = service.follow(ana.user, beto.profile.id);

        assertTrue(first.following());
        assertTrue(second.following());
        assertEquals(1, second.followersCount());
        assertEquals(1, UserFollow.countFollowers(beto.profile));
        assertEquals(1, UserFollow.countFollowing(ana.profile));
    }

    @Test
    @TestTransaction
    void follow_deveFalhar_quandoSeguirAProprioPerfil() {
        Fixture ana = fixture("Ana");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.follow(ana.user, ana.profile.id)
        );

        assertEquals(SocialServiceImplementation.SELF_FOLLOW_FORBIDDEN_MESSAGE, exception.getMessage());
    }

    @Test
    @TestTransaction
    void follow_deveFalhar_semPerfilOuAlvoInexistente() {
        User semPerfil = persistUser("Sem", "Perfil");
        Fixture beto = fixture("Beto");

        assertThrows(IllegalStateException.class, () -> service.follow(semPerfil, beto.profile.id));
        assertThrows(NoSuchElementException.class, () -> service.follow(beto.user, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.follow(beto.user, null));
    }

    @Test
    @TestTransaction
    void unfollow_naoDeveFalhar_quandoNaoSeguia() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");

        FollowActionResponse response = assertDoesNotThrow(() -> service.unfollow(ana.user, beto.profile.id));

        assertFalse(response.following());
        assertEquals(0, response.followersCount());
    }

    @Test
    @TestTransaction
    void followStats_eListas_refletemFollowEUnfollow() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        Fixture caio = fixture("Caio");

        service.follow(ana.user, beto.profile.id);
        service.follow(caio.user, beto.profile.id);
        service.follow(beto.user, ana.profile.id);

        FollowStatsResponse betoStatsSeenByAna = service.getFollowStats(ana.user, beto.profile.id);
        assertEquals(2, betoStatsSeenByAna.followersCount());
        assertEquals(1, betoStatsSeenByAna.followingCount());
        assertTrue(betoStatsSeenByAna.followedByCurrentUser());

        FollowPageResponse betoFollowers = service.listFollowers(beto.user, 0, 20);
        assertEquals(2, betoFollowers.totalElements());
        assertTrue(betoFollowers.profiles().stream()
                .filter(profile -> profile.userProfileId().equals(ana.profile.id))
                .findFirst().orElseThrow().followedByCurrentUser(), "Beto segue Ana de volta");
        assertFalse(betoFollowers.profiles().stream()
                .filter(profile -> profile.userProfileId().equals(caio.profile.id))
                .findFirst().orElseThrow().followedByCurrentUser());

        FollowPageResponse anaFollowing = service.listFollowing(ana.user, 0, 20);
        assertEquals(List.of(beto.profile.id), anaFollowing.profiles().stream().map(p -> p.userProfileId()).toList());
        assertEquals("Beto Sobrenome", anaFollowing.profiles().get(0).name());
        assertEquals(beto.user.id, anaFollowing.profiles().get(0).userId());

        service.unfollow(ana.user, beto.profile.id);
        FollowStatsResponse afterUnfollow = service.getFollowStats(ana.user, beto.profile.id);
        assertEquals(1, afterUnfollow.followersCount());
        assertFalse(afterUnfollow.followedByCurrentUser());
        assertFalse(service.getFollowStats(beto.user, beto.profile.id).followedByCurrentUser(), "próprio perfil nunca é 'seguido por mim'");
    }

    // -------------------------------------------------------------- posts

    @Test
    @TestTransaction
    void createPost_deveFalhar_quandoBodyVazio() {
        Fixture ana = fixture("Ana");

        assertThrows(IllegalArgumentException.class, () -> service.createPost(ana.user, "   ", null));
        assertThrows(IllegalArgumentException.class, () -> service.createPost(ana.user, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.createPost(ana.user, "x".repeat(4001), null));
    }

    @Test
    @TestTransaction
    void createPost_devePersistirComoPersonal_semComunidade() {
        Fixture ana = fixture("Ana");

        UserPostResponse response = service.createPost(ana.user, "  Olá feed  ", null);

        assertNotNull(response.id());
        assertEquals("Olá feed", response.body());
        assertNull(response.mediaUrl());
        assertEquals(ana.profile.id, response.author().userProfileId());
        assertEquals(ana.user.id, response.author().userId());
        assertEquals("Ana Sobrenome", response.author().name());

        Post stored = Post.findById(response.id());
        assertEquals(PostOrigin.PERSONAL, stored.origin);
        assertNull(stored.community);
        assertTrue(stored.active);
        assertNull(Post.findByIdWithActiveCommunity(response.id()), "post pessoal é invisível às rotas de comunidade");
    }

    @Test
    @TestTransaction
    void deletePost_deveFalhar_quandoNaoForAutor_eSerSoftDelete() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        UserPostResponse post = service.createPost(ana.user, "meu post", null);

        assertThrows(ForbiddenException.class, () -> service.deletePost(beto.user, post.id()));

        service.deletePost(ana.user, post.id());

        Post stored = Post.findById(post.id());
        assertNotNull(stored, "soft delete: a linha continua na tabela");
        assertFalse(stored.active);
        assertTrue(service.getFeed(ana.user, 0, 10).posts().isEmpty());
        assertThrows(NoSuchElementException.class, () -> service.deletePost(ana.user, post.id()));
        assertThrows(NoSuchElementException.class, () -> service.getPostMedia(ana.user, post.id()));
    }

    @Test
    @TestTransaction
    void getFeed_deveIncluirPostsProprios_quandoNaoSeguirNinguem() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        service.createPost(beto.user, "post do beto", null);

        assertTrue(service.getFeed(ana.user, 0, 10).posts().isEmpty(), "sem seguir ninguém e sem posts: vazio");

        service.createPost(ana.user, "post da ana", null);
        UserFeedPageResponse feed = service.getFeed(ana.user, 0, 10);

        assertEquals(1, feed.posts().size());
        assertEquals("post da ana", feed.posts().get(0).body());
        assertFalse(feed.hasNext());
    }

    @Test
    @TestTransaction
    void getFeed_deveOrdenarPorCreatedAtDesc_ePaginarComHasNext() throws InterruptedException {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        Fixture caio = fixture("Caio");
        service.follow(ana.user, beto.profile.id);

        service.createPost(beto.user, "beto 1", null);
        service.createPost(ana.user, "ana 2", null);
        service.createPost(caio.user, "caio (não seguido)", null);
        service.createPost(beto.user, "beto 3", null);

        UserFeedPageResponse firstPage = service.getFeed(ana.user, 0, 2);
        assertEquals(List.of("beto 3", "ana 2"), firstPage.posts().stream().map(UserPostResponse::body).toList());
        assertTrue(firstPage.hasNext());

        UserFeedPageResponse secondPage = service.getFeed(ana.user, 1, 2);
        assertEquals(List.of("beto 1"), secondPage.posts().stream().map(UserPostResponse::body).toList());
        assertFalse(secondPage.hasNext());

        UserFeedPageResponse betoPosts = service.listPostsByProfile(ana.user, beto.profile.id, 0, 10);
        assertEquals(List.of("beto 3", "beto 1"), betoPosts.posts().stream().map(UserPostResponse::body).toList());
    }

    // ------------------------------------------------------------ fixtures

    private record Fixture(User user, UserProfile profile) {
    }

    private static Fixture fixture(String name) {
        User user = persistUser(name, "Sobrenome");
        UserProfile profile = new UserProfile();
        profile.id = UUIDv7Generator.generate();
        profile.user = user;
        profile.bio = "bio";
        profile.persist();
        return new Fixture(user, profile);
    }

    private static User persistUser(String name, String lastName) {
        User user = new User();
        user.id = UUIDv7Generator.generate();
        user.name = name;
        user.lastName = lastName;
        user.email = "social-" + UUID.randomUUID() + "@example.com";
        user.password = "hash";
        user.birthdate = LocalDate.now().minusYears(30);
        user.verified = true;
        user.lastUpdatedAt = Instant.now();
        user.persist();
        return user;
    }
}
