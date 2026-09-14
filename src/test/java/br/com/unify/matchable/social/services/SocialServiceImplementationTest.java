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
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityCategory;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.community.enums.CommunityPrivacy;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.post.entity.PostLike;
import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.social.dto.FollowActionResponse;
import br.com.unify.matchable.social.dto.FollowPageResponse;
import br.com.unify.matchable.social.dto.FollowStatsResponse;
import br.com.unify.matchable.social.dto.UserFeedPageResponse;
import br.com.unify.matchable.social.dto.UserPostCommentPageResponse;
import br.com.unify.matchable.social.dto.UserPostCommentResponse;
import br.com.unify.matchable.social.dto.UserPostLikeResponse;
import br.com.unify.matchable.social.dto.UserPostResponse;
import br.com.unify.matchable.social.entity.UserFollow;
import br.com.unify.matchable.social.enums.FeedSource;
import br.com.unify.matchable.user.entity.InterestType;
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
        assertEquals(PostOrigin.PERSONAL, response.origin());
        assertNull(response.community());
        assertEquals("Olá feed", response.body());
        assertNull(response.mediaUrl());
        assertNull(response.editedAt());
        assertEquals(0, response.likesCount());
        assertEquals(0, response.commentsCount());
        assertFalse(response.likedByCurrentUser());
        assertFalse(response.commentedByCurrentUser());
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
        assertTrue(service.listPostsByProfile(ana.user, ana.profile.id, 0, 10).posts().isEmpty());
        assertThrows(NoSuchElementException.class, () -> service.deletePost(ana.user, post.id()));
        assertThrows(NoSuchElementException.class, () -> service.getPostMedia(ana.user, post.id()));
    }

    @Test
    @TestTransaction
    void getFeed_naoIncluiPostsProprios_eSugerePostsDeQuemNaoSigo() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");

        service.createPost(ana.user, "post da ana", null);
        assertTrue(service.getFeed(ana.user, 0, 10).posts().isEmpty(), "só existem os meus posts: vazio (eles ficam no perfil)");

        UserPostResponse betoPost = service.createPost(beto.user, "post do beto", null);
        UserFeedPageResponse feed = service.getFeed(ana.user, 0, 10);

        assertEquals(List.of(betoPost.id()), feed.posts().stream().map(UserPostResponse::id).toList(),
                "sem seguir ninguém o feed não fica vazio: o post do Beto entra como sugestão");
        assertEquals(FeedSource.SUGGESTED_PROFILE, feed.posts().get(0).feedSource());
        assertFalse(feed.hasNext());
        assertEquals(1, service.listPostsByProfile(ana.user, ana.profile.id, 0, 10).posts().size());
        assertNull(service.listPostsByProfile(ana.user, ana.profile.id, 0, 10).posts().get(0).feedSource(),
                "feedSource só existe no feed da aba Início");
    }

    @Test
    @TestTransaction
    void getFeed_comunidadePublica_entraComoSugestao_ePrivadaSoQuandoSouMembro() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        Community publicCommunity = persistCommunity(beto.user, "Xadrez", CommunityPrivacy.PUBLIC);
        Community privateCommunity = persistCommunity(beto.user, "Segredos", CommunityPrivacy.PRIVATE);
        persistMembership(publicCommunity, beto.profile, CommunityMemberRole.ADMIN);
        persistMembership(privateCommunity, beto.profile, CommunityMemberRole.ADMIN);
        Post publicPost = persistCommunityPost(publicCommunity, beto.user, "post na comunidade pública");
        persistCommunityPost(privateCommunity, beto.user, "post na comunidade privada");

        UserFeedPageResponse asVisitor = service.getFeed(ana.user, 0, 10);
        assertEquals(List.of(publicPost.id), asVisitor.posts().stream().map(UserPostResponse::id).toList(),
                "não membro: a pública entra como sugestão, a privada nunca");
        UserPostResponse item = asVisitor.posts().get(0);
        assertEquals(FeedSource.SUGGESTED_COMMUNITY, item.feedSource());
        assertEquals(PostOrigin.COMMUNITY, item.origin());
        assertNotNull(item.community());
        assertEquals(publicCommunity.id, item.community().id());
        assertEquals("Xadrez", item.community().name());
        assertNull(item.community().iconUrl());
        assertEquals(beto.profile.id, item.author().userProfileId());

        persistMembership(publicCommunity, ana.profile, CommunityMemberRole.MEMBER);
        persistMembership(privateCommunity, ana.profile, CommunityMemberRole.MEMBER);
        UserFeedPageResponse asMember = service.getFeed(ana.user, 0, 10);
        assertEquals(2, asMember.posts().size(), "membro: pública e privada entram");
        assertTrue(asMember.posts().stream().allMatch(post -> post.feedSource() == FeedSource.MEMBER_COMMUNITY));

        publicCommunity.active = false;
        privateCommunity.active = false;
        assertTrue(service.getFeed(ana.user, 0, 10).posts().isEmpty(), "comunidade inativa some do feed");
    }

    @Test
    @TestTransaction
    void getFeed_misturaSeguidosEComunidades_comContadores() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        Fixture caio = fixture("Caio");
        service.follow(ana.user, beto.profile.id);
        Community community = persistCommunity(caio.user, "Trilhas");
        persistMembership(community, caio.profile, CommunityMemberRole.ADMIN);
        persistMembership(community, ana.profile, CommunityMemberRole.MEMBER);

        UserPostResponse betoPost = service.createPost(beto.user, "beto pessoal", null);
        Post caioCommunityPost = persistCommunityPost(community, caio.user, "caio na trilha");
        UserPostResponse caioPersonalPost = service.createPost(caio.user, "caio pessoal (não sigo)", null);

        service.likePost(ana.user, betoPost.id());
        service.likePost(beto.user, betoPost.id());
        service.createComment(ana.user, betoPost.id(), "legal!");

        UserFeedPageResponse feed = service.getFeed(ana.user, 0, 10);

        // Quem eu sigo (60) + engajamento vence a comunidade (50); o post pessoal
        // do Caio (não sigo) fecha a lista como sugestão.
        assertEquals(List.of(betoPost.id(), caioCommunityPost.id, caioPersonalPost.id()),
                feed.posts().stream().map(UserPostResponse::id).toList());
        assertEquals(
                List.of(FeedSource.FOLLOWING, FeedSource.MEMBER_COMMUNITY, FeedSource.SUGGESTED_PROFILE),
                feed.posts().stream().map(UserPostResponse::feedSource).toList()
        );
        UserPostResponse betoInFeed = feed.posts().get(0);
        assertEquals(2, betoInFeed.likesCount());
        assertEquals(1, betoInFeed.commentsCount());
        assertTrue(betoInFeed.likedByCurrentUser());
        assertTrue(betoInFeed.commentedByCurrentUser());
        UserPostResponse caioInFeed = feed.posts().get(1);
        assertEquals(0, caioInFeed.likesCount());
        assertFalse(caioInFeed.likedByCurrentUser());
        assertFalse(caioInFeed.commentedByCurrentUser());
    }

    @Test
    @TestTransaction
    void getFeed_sugerePerfis_porInteressesEmComum_eDepoisPorEngajamento() {
        Fixture ana = withInterests(fixture("Ana"), 1, 2);
        Fixture beto = withInterests(fixture("Beto"), 1, 2);
        Fixture caio = withInterests(fixture("Caio"), 1, 3);
        Fixture dani = withInterests(fixture("Dani"), 4);
        Fixture curioso = fixture("Curioso");

        UserPostResponse betoPost = service.createPost(beto.user, "dois interesses em comum", null);
        UserPostResponse caioPost = service.createPost(caio.user, "um interesse em comum", null);
        UserPostResponse daniPost = service.createPost(dani.user, "nenhum interesse em comum", null);

        UserFeedPageResponse byAffinity = service.getFeed(ana.user, 0, 10);
        assertEquals(List.of(betoPost.id(), caioPost.id(), daniPost.id()),
                byAffinity.posts().stream().map(UserPostResponse::id).toList());
        assertTrue(byAffinity.posts().stream().allMatch(post -> post.feedSource() == FeedSource.SUGGESTED_PROFILE));

        // 4 comentários (4 pontos cada) fazem o post sem afinidade passar o de 1 interesse (15 pontos).
        for (int i = 0; i < 4; i++) {
            service.createComment(curioso.user, daniPost.id(), "comentário " + i);
        }
        UserFeedPageResponse byEngagement = service.getFeed(ana.user, 0, 10);
        assertEquals(List.of(betoPost.id(), daniPost.id(), caioPost.id()),
                byEngagement.posts().stream().map(UserPostResponse::id).toList());
    }

    @Test
    @TestTransaction
    void getFeed_comunidadeSugerida_pontuaCategoriaEmComum_ePessoasQueSigo() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        Fixture caio = fixture("Caio");
        CommunityCategory games = CommunityCategory.findById(7);
        CommunityCategory music = CommunityCategory.findById(8);
        assertNotNull(games);
        assertNotNull(music);

        Community mine = persistCommunity(caio.user, "Minha de games", CommunityPrivacy.PUBLIC);
        mine.category = games;
        persistMembership(mine, ana.profile, CommunityMemberRole.MEMBER);

        Community sameCategory = persistCommunity(beto.user, "Outra de games", CommunityPrivacy.PUBLIC);
        sameCategory.category = games;
        Community otherCategory = persistCommunity(beto.user, "Música", CommunityPrivacy.PUBLIC);
        otherCategory.category = music;
        Community withFollowed = persistCommunity(beto.user, "Onde o Caio está", CommunityPrivacy.PUBLIC);
        withFollowed.category = music;
        persistMembership(withFollowed, caio.profile, CommunityMemberRole.MEMBER);
        service.follow(ana.user, caio.profile.id);

        Post sameCategoryPost = persistCommunityPost(sameCategory, beto.user, "games");
        Post otherCategoryPost = persistCommunityPost(otherCategory, beto.user, "música");
        Post withFollowedPost = persistCommunityPost(withFollowed, beto.user, "caio é membro aqui");

        UserFeedPageResponse feed = service.getFeed(ana.user, 0, 10);

        // categoria em comum (20) > pessoa que sigo é membro (10) > nada (0)
        assertEquals(List.of(sameCategoryPost.id, withFollowedPost.id, otherCategoryPost.id),
                feed.posts().stream().map(UserPostResponse::id).toList());
        assertTrue(feed.posts().stream().allMatch(post -> post.feedSource() == FeedSource.SUGGESTED_COMMUNITY));
    }

    @Test
    @TestTransaction
    void updatePost_soAutor_eMarcaEditedAt() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        UserPostResponse post = service.createPost(ana.user, "original", null);

        assertThrows(ForbiddenException.class, () -> service.updatePost(beto.user, post.id(), "invasão"));
        assertThrows(IllegalArgumentException.class, () -> service.updatePost(ana.user, post.id(), "  "));
        assertThrows(NoSuchElementException.class, () -> service.updatePost(ana.user, UUID.randomUUID(), "x"));

        UserPostResponse updated = service.updatePost(ana.user, post.id(), "  editado  ");

        assertEquals("editado", updated.body());
        assertNotNull(updated.editedAt());
        assertEquals("editado", Post.<Post>findById(post.id()).body);
    }

    @Test
    @TestTransaction
    void likePost_eIdempotente_eUnlikeRemove() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        UserPostResponse post = service.createPost(beto.user, "curta aqui", null);

        UserPostLikeResponse first = service.likePost(ana.user, post.id());
        UserPostLikeResponse second = service.likePost(ana.user, post.id());
        assertTrue(second.likedByCurrentUser());
        assertEquals(1, first.likesCount());
        assertEquals(1, second.likesCount());
        assertEquals(1, PostLike.countByPost(Post.findById(post.id())));

        UserPostLikeResponse removed = service.unlikePost(ana.user, post.id());
        assertFalse(removed.likedByCurrentUser());
        assertEquals(0, removed.likesCount());
        assertEquals(0, service.unlikePost(ana.user, post.id()).likesCount(), "unlike repetido não falha");

        assertThrows(NoSuchElementException.class, () -> service.likePost(ana.user, UUID.randomUUID()));
    }

    @Test
    @TestTransaction
    void comments_criaListaEExclui_autorDoPostPodeExcluirDeOutro() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        Fixture caio = fixture("Caio");
        UserPostResponse post = service.createPost(ana.user, "comentem", null);

        assertThrows(IllegalArgumentException.class, () -> service.createComment(beto.user, post.id(), " "));
        assertThrows(IllegalArgumentException.class, () -> service.createComment(beto.user, post.id(), "x".repeat(2001)));

        UserPostCommentResponse betoComment = service.createComment(beto.user, post.id(), " primeiro ");
        UserPostCommentResponse caioComment = service.createComment(caio.user, post.id(), "segundo");

        assertEquals("primeiro", betoComment.body());
        assertTrue(betoComment.commentedByCurrentUser());
        assertEquals(beto.profile.id, betoComment.author().userProfileId());

        UserPostCommentPageResponse page = service.getComments(ana.user, post.id(), 0, 20);
        assertEquals(2, page.totalElements());
        assertEquals(List.of("primeiro", "segundo"), page.comments().stream().map(UserPostCommentResponse::body).toList());
        assertFalse(page.comments().get(0).commentedByCurrentUser(), "visto pela Ana, o comentário do Beto não é dela");
        assertFalse(page.hasNext());

        assertThrows(ForbiddenException.class, () -> service.deleteComment(caio.user, post.id(), betoComment.id()));
        service.deleteComment(ana.user, post.id(), betoComment.id());
        service.deleteComment(caio.user, post.id(), caioComment.id());
        assertEquals(0, service.getComments(ana.user, post.id(), 0, 20).totalElements());
        assertThrows(NoSuchElementException.class, () -> service.deleteComment(ana.user, post.id(), betoComment.id()));
    }

    @Test
    @TestTransaction
    void getFeed_deveOrdenarSeguidosPorCreatedAtDesc_ePaginarComHasNext() {
        Fixture ana = fixture("Ana");
        Fixture beto = fixture("Beto");
        Fixture caio = fixture("Caio");
        service.follow(ana.user, beto.profile.id);

        service.createPost(beto.user, "beto 1", null);
        service.createPost(ana.user, "ana (próprio, fica no perfil)", null);
        service.createPost(caio.user, "caio (não seguido)", null);
        service.createPost(beto.user, "beto 2", null);
        service.createPost(beto.user, "beto 3", null);

        UserFeedPageResponse firstPage = service.getFeed(ana.user, 0, 2);
        assertEquals(List.of("beto 3", "beto 2"), firstPage.posts().stream().map(UserPostResponse::body).toList());
        assertTrue(firstPage.hasNext());

        // Quem eu sigo vem antes; o post do Caio (não sigo) fecha o feed como sugestão.
        UserFeedPageResponse secondPage = service.getFeed(ana.user, 1, 2);
        assertEquals(List.of("beto 1", "caio (não seguido)"), secondPage.posts().stream().map(UserPostResponse::body).toList());
        assertEquals(FeedSource.SUGGESTED_PROFILE, secondPage.posts().get(1).feedSource());
        assertFalse(secondPage.hasNext());

        UserFeedPageResponse betoPosts = service.listPostsByProfile(ana.user, beto.profile.id, 0, 10);
        assertEquals(List.of("beto 3", "beto 2", "beto 1"), betoPosts.posts().stream().map(UserPostResponse::body).toList());
    }

    // ------------------------------------------------------------ fixtures

    private record Fixture(User user, UserProfile profile) {
    }

    private static Community persistCommunity(User owner, String name) {
        return persistCommunity(owner, name, CommunityPrivacy.PUBLIC);
    }

    private static Community persistCommunity(User owner, String name, CommunityPrivacy privacy) {
        Community community = new Community();
        community.id = UUIDv7Generator.generate();
        community.name = name;
        community.owner = owner;
        community.active = true;
        community.privacy = privacy;
        community.persist();
        return community;
    }

    /** Interesses vêm do import.sql de teste (interest_types 1..6). */
    private static Fixture withInterests(Fixture fixture, Integer... interestTypeIds) {
        for (Integer id : interestTypeIds) {
            InterestType interest = InterestType.findById(id);
            assertNotNull(interest, "interest_type " + id + " precisa existir no import.sql");
            fixture.profile.interestTypes.add(interest);
        }
        fixture.profile.persist();
        return fixture;
    }

    private static void persistMembership(Community community, UserProfile profile, CommunityMemberRole role) {
        CommunityMembership membership = new CommunityMembership();
        membership.id = UUIDv7Generator.generate();
        membership.community = community;
        membership.userProfile = profile;
        membership.role = role;
        membership.joinedAt = Instant.now();
        membership.persist();
    }

    private static Post persistCommunityPost(Community community, User author, String body) {
        Post post = new Post();
        post.id = UUIDv7Generator.generate();
        post.origin = PostOrigin.COMMUNITY;
        post.community = community;
        post.author = author;
        post.body = body;
        post.createdAt = Instant.now();
        post.persist();
        return post;
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
