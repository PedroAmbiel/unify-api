package br.com.unify.matchable.notification.services;

import static br.com.unify.matchable.notification.NotificationFixtures.notificationsOf;
import static br.com.unify.matchable.notification.NotificationFixtures.person;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.chat.dto.ConversationResponse;
import br.com.unify.matchable.chat.services.ChatService;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.community.enums.CommunityPrivacy;
import br.com.unify.matchable.community.services.CommunityService;
import br.com.unify.matchable.notification.NotificationFixtures.Person;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.social.dto.UserPostResponse;
import br.com.unify.matchable.social.services.SocialService;
import br.com.unify.matchable.user.dto.MatchDecisionRequest;
import br.com.unify.matchable.user.dto.MatchDecisionResponse;
import br.com.unify.matchable.user.entity.UserPrivacySettings;
import br.com.unify.matchable.user.services.UserMatchService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Pontos de gancho (plano 05, seção 4.8) pelos serviços reais: cada evento de
 * domínio gera a notificação certa para a pessoa certa, e só ela.
 */
@QuarkusTest
class NotificationHooksIntegrationTest {

    @Inject
    UserMatchService userMatchService;

    @Inject
    ChatService chatService;

    @Inject
    SocialService socialService;

    @Inject
    CommunityService communityService;

    @Test
    @TestTransaction
    void mutualMatchNotifiesBothPeopleOnce() {
        Person ana = person("Ana");
        Person beto = person("Beto");

        userMatchService.registerDecision(ana.user(), new MatchDecisionRequest(beto.profile().id, true));
        assertTrue(notificationsOf(beto.user()).isEmpty(), "curtida sem reciprocidade não avisa (privacidade do match)");

        MatchDecisionResponse mutual = userMatchService.registerDecision(
                beto.user(), new MatchDecisionRequest(ana.profile().id, true));

        List<Notification> forAna = notificationsOf(ana.user(), NotificationType.NEW_MATCH);
        List<Notification> forBeto = notificationsOf(beto.user(), NotificationType.NEW_MATCH);
        assertEquals(1, forAna.size());
        assertEquals(1, forBeto.size());
        assertEquals(mutual.id(), forAna.get(0).referenceId);
        assertEquals(beto.profile().id, forAna.get(0).contextId, "contexto = perfil da outra pessoa");
        assertEquals(ana.profile().id, forBeto.get(0).contextId);
        assertEquals("Beto Aviso também curtiu você. Vocês deram match!", forAna.get(0).body);
    }

    @Test
    @TestTransaction
    void declinedInviteDoesNotNotify() {
        Person ana = person("Ana");
        Person beto = person("Beto");

        userMatchService.registerDecision(ana.user(), new MatchDecisionRequest(beto.profile().id, true));
        userMatchService.registerDecision(beto.user(), new MatchDecisionRequest(ana.profile().id, false));

        assertTrue(notificationsOf(ana.user()).isEmpty());
        assertTrue(notificationsOf(beto.user()).isEmpty());
    }

    @Test
    @TestTransaction
    void chatMessagesGroupAndOpeningTheConversationClearsTheBell() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        userMatchService.registerDecision(ana.user(), new MatchDecisionRequest(beto.profile().id, true));
        MatchDecisionResponse mutual = userMatchService.registerDecision(
                beto.user(), new MatchDecisionRequest(ana.profile().id, true));
        ConversationResponse conversation = chatService.openConversation(ana.user(), mutual.id());

        chatService.sendTextMessage(ana.user(), conversation.conversationId(), "Oi, Beto!", null);
        chatService.sendTextMessage(ana.user(), conversation.conversationId(), "Viu meu convite?", null);

        List<Notification> forBeto = notificationsOf(beto.user(), NotificationType.NEW_MESSAGE);
        assertEquals(1, forBeto.size());
        assertEquals(conversation.conversationId(), forBeto.get(0).referenceId);
        assertEquals(ana.profile().id, forBeto.get(0).contextId);
        assertEquals(2, forBeto.get(0).eventCount);
        assertTrue(notificationsOf(ana.user(), NotificationType.NEW_MESSAGE).isEmpty(), "quem envia não é avisado");

        chatService.markConversationAsRead(beto.user(), conversation.conversationId());
        // UPDATE em lote não atualiza a entidade já carregada na sessão: confere no banco.
        assertEquals(1L, Notification.count("id = ?1 and readAt is not null", forBeto.get(0).id));
    }

    @Test
    @TestTransaction
    void followNotifiesOnceEvenWithRepeatedTaps() {
        Person ana = person("Ana");
        Person beto = person("Beto");

        socialService.follow(beto.user(), ana.profile().id);
        socialService.unfollow(beto.user(), ana.profile().id);
        socialService.follow(beto.user(), ana.profile().id);

        List<Notification> forAna = notificationsOf(ana.user(), NotificationType.NEW_FOLLOWER);
        assertEquals(1, forAna.size());
        assertEquals(beto.profile().id, forAna.get(0).referenceId);
    }

    @Test
    @TestTransaction
    void privateAccountGetsARequestAndTheRequesterLearnsItWasAccepted() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        requireFollowApproval(ana);

        socialService.follow(beto.user(), ana.profile().id);

        List<Notification> requests = notificationsOf(ana.user(), NotificationType.FOLLOW_REQUEST);
        assertEquals(1, requests.size());
        assertEquals(beto.profile().id, requests.get(0).referenceId);
        assertTrue(notificationsOf(ana.user(), NotificationType.NEW_FOLLOWER).isEmpty(), "pedido não é seguidor");

        UUID requestId = socialService.listFollowRequests(ana.user(), 0, 20).requests().get(0).id();
        socialService.acceptFollowRequest(ana.user(), requestId);

        List<Notification> accepted = notificationsOf(beto.user(), NotificationType.FOLLOW_REQUEST_ACCEPTED);
        assertEquals(1, accepted.size());
        assertEquals(ana.profile().id, accepted.get(0).referenceId);
        assertEquals(1L, Notification.count("id = ?1 and readAt is not null", requests.get(0).id),
                "pedido respondido sai do sino");
    }

    @Test
    @TestTransaction
    void likesAndCommentsOnPersonalPostsNotifyTheAuthorButNeverForOwnActions() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        UserPostResponse post = socialService.createPost(ana.user(), "Minha primeira publicação", null);

        socialService.likePost(ana.user(), post.id());
        socialService.createComment(ana.user(), post.id(), "Comentando no meu post");
        assertTrue(notificationsOf(ana.user()).isEmpty(), "ações na própria publicação não avisam");

        socialService.likePost(beto.user(), post.id());
        socialService.unlikePost(beto.user(), post.id());
        socialService.likePost(beto.user(), post.id());
        socialService.createComment(beto.user(), post.id(), "Parabéns pela publicação!");

        List<Notification> likes = notificationsOf(ana.user(), NotificationType.POST_LIKED);
        assertEquals(1, likes.size(), "curtir/descurtir/curtir não repete");
        assertEquals(post.id(), likes.get(0).referenceId);
        assertNull(likes.get(0).contextId, "post pessoal: sem comunidade");

        List<Notification> comments = notificationsOf(ana.user(), NotificationType.POST_COMMENTED);
        assertEquals(1, comments.size());
        assertEquals("Beto Aviso comentou na sua publicação: Parabéns pela publicação!", comments.get(0).body);
    }

    @Test
    @TestTransaction
    void communityPostNotificationsCarryTheCommunityAsContextAndPostOpensByIdForMembers() {
        Person ana = person("Ana");
        Person beto = person("Beto");
        Person caio = person("Caio");
        Community community = community(ana, CommunityPrivacy.PRIVATE);
        membership(community, ana, CommunityMemberRole.ADMIN);
        membership(community, beto, CommunityMemberRole.MEMBER);
        Post post = communityPost(community, ana, "Encontro sábado no parque");

        communityService.likePost(beto.user(), post.id);
        communityService.createComment(beto.user(), post.id, "Vou sim!");

        Notification like = notificationsOf(ana.user(), NotificationType.POST_LIKED).get(0);
        assertEquals(post.id, like.referenceId);
        assertEquals(community.id, like.contextId);
        assertEquals(1, notificationsOf(ana.user(), NotificationType.POST_COMMENTED).size());

        // Deep link: GET /users/posts/{id} devolve o post de comunidade no formato do feed.
        UserPostResponse opened = socialService.getPost(ana.user(), post.id);
        assertEquals(PostOrigin.COMMUNITY, opened.origin());
        assertEquals(community.id, opened.community().id());
        assertEquals(1L, opened.likesCount());

        // Comunidade privada: quem não é membro recebe 404, igual a post inexistente.
        assertThrows(NoSuchElementException.class, () -> socialService.getPost(caio.user(), post.id));
    }

    @Test
    @TestTransaction
    void personalPostOpensByIdForTheAuthor() {
        Person ana = person("Ana");
        UserPostResponse post = socialService.createPost(ana.user(), "Texto do post", null);

        UserPostResponse opened = socialService.getPost(ana.user(), post.id());

        assertEquals(PostOrigin.PERSONAL, opened.origin());
        assertEquals("Texto do post", opened.body());
        assertNull(opened.community());
    }

    // ------------------------------------------------------------ fixtures

    private static void requireFollowApproval(Person person) {
        UserPrivacySettings settings = new UserPrivacySettings();
        settings.id = UUIDv7Generator.generate();
        settings.user = person.user();
        settings.followApprovalRequired = true;
        settings.updatedAt = Instant.now();
        settings.persistAndFlush();
    }

    private static Community community(Person owner, CommunityPrivacy privacy) {
        Community community = new Community();
        community.id = UUIDv7Generator.generate();
        community.name = "Comunidade " + UUID.randomUUID();
        community.owner = owner.user();
        community.active = true;
        community.privacy = privacy;
        community.persist();
        return community;
    }

    private static void membership(Community community, Person person, CommunityMemberRole role) {
        CommunityMembership membership = new CommunityMembership();
        membership.id = UUIDv7Generator.generate();
        membership.community = community;
        membership.userProfile = person.profile();
        membership.role = role;
        membership.joinedAt = Instant.now();
        membership.persist();
    }

    private static Post communityPost(Community community, Person author, String body) {
        Post post = new Post();
        post.id = UUIDv7Generator.generate();
        post.origin = PostOrigin.COMMUNITY;
        post.community = community;
        post.author = author.user();
        post.body = body;
        post.createdAt = Instant.now();
        post.persist();
        return post;
    }
}
