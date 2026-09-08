package br.com.unify.matchable.chat.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Teste de integração de ponta a ponta do chat: usuários reais persistidos via
 * Panache, autenticação real por {@code POST /auth/signin} e chamadas HTTP com
 * RestAssured. O filtro JWT/{@code @RolesAllowed} só é exercitado desta forma —
 * chamar o método do resource direto (padrão de {@code CommunityResourceTest})
 * não cobriria os 401 nem a matriz de autorização real.
 */
@QuarkusTest
class ChatResourceTest {

    private static final String RAW_PASSWORD = "Senha@123";
    private static final String CHATS_PATH = "/chats";

    // ---------------------------------------------------------------- 1

    @Test
    void openConversationWithMutualMatchIsIdempotent() {
        TestUser alice = createUser("Alice", "Prado");
        TestUser bob = createUser("Bob", "Ramos");
        UUID matchId = createMatch(alice, bob, true);

        String firstConversationId = given()
                .auth().oauth2(alice.token())
        .when()
                .post(CHATS_PATH + "?matchId=" + matchId)
        .then()
                .statusCode(200)
                .body("matchId", equalTo(matchId.toString()))
                .body("otherUserId", equalTo(bob.userId().toString()))
                .body("otherUserProfileId", equalTo(bob.profileId().toString()))
                .body("otherUserName", equalTo("Bob Ramos"))
                .body("otherUserAge", notNullValue())
                .body("createdAt", notNullValue())
                .body("lastMessageAt", nullValue())
                .extract().path("conversationId");

        assertNotNull(firstConversationId);

        // Segunda chamada (inclusive pelo outro participante) devolve a MESMA conversa.
        String secondConversationId = given()
                .auth().oauth2(bob.token())
        .when()
                .post(CHATS_PATH + "?matchId=" + matchId)
        .then()
                .statusCode(200)
                .body("otherUserId", equalTo(alice.userId().toString()))
                .extract().path("conversationId");

        assertEquals(firstConversationId, secondConversationId);
    }

    // ---------------------------------------------------------------- 2

    @Test
    void openConversationWithNonMutualMatchReturnsConflict() {
        TestUser alice = createUser("Alice", "Naomi");
        TestUser bob = createUser("Bob", "Naomi");
        UUID matchId = createMatch(alice, bob, false);

        given()
                .auth().oauth2(alice.token())
        .when()
                .post(CHATS_PATH + "?matchId=" + matchId)
        .then()
                .statusCode(409)
                .body("error", equalTo("RESOURCE_CONFLICT"));
    }

    // ---------------------------------------------------------------- 3

    @Test
    void openConversationOfThirdPartyMatchReturnsForbidden() {
        TestUser alice = createUser("Alice", "Terceira");
        TestUser bob = createUser("Bob", "Terceiro");
        TestUser carol = createUser("Carol", "Intrusa");
        UUID matchId = createMatch(alice, bob, true);

        given()
                .auth().oauth2(carol.token())
        .when()
                .post(CHATS_PATH + "?matchId=" + matchId)
        .then()
                .statusCode(403)
                .body("error", equalTo("AUTH_FORBIDDEN"));
    }

    @Test
    void openConversationWithoutMatchIdReturnsBadRequest() {
        TestUser alice = createUser("Alice", "SemMatch");

        given()
                .auth().oauth2(alice.token())
        .when()
                .post(CHATS_PATH)
        .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_INVALID_FORMAT"));
    }

    @Test
    void openConversationWithUnknownMatchReturnsNotFound() {
        TestUser alice = createUser("Alice", "Fantasma");

        given()
                .auth().oauth2(alice.token())
        .when()
                .post(CHATS_PATH + "?matchId=" + UUID.randomUUID())
        .then()
                .statusCode(404)
                .body("error", equalTo("RESOURCE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- 4

    @Test
    void getMessagesFromOutsiderReturnsForbidden() {
        TestUser alice = createUser("Alice", "Dentro");
        TestUser bob = createUser("Bob", "Dentro");
        TestUser carol = createUser("Carol", "Fora");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        given()
                .auth().oauth2(carol.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(403)
                .body("error", equalTo("AUTH_FORBIDDEN"));

        given()
                .auth().oauth2(carol.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"invasao\"}")
        .when()
                .post(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(403);

        given()
                .auth().oauth2(carol.token())
        .when()
                .put(CHATS_PATH + "/" + conversationId + "/read")
        .then()
                .statusCode(403);
    }

    // ---------------------------------------------------------------- 5

    @Test
    void getMessageMediaFromOutsiderReturnsNotFoundInsteadOfForbidden() {
        TestUser alice = createUser("Alice", "Midia");
        TestUser bob = createUser("Bob", "Midia");
        TestUser carol = createUser("Carol", "Curiosa");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        byte[] jpeg = buildJpeg(48, 48);

        String mediaUrl = given()
                .auth().oauth2(alice.token())
                .multiPart("type", "IMAGE")
                .multiPart("file", "foto.jpg", jpeg, "image/jpeg")
        .when()
                .post(CHATS_PATH + "/" + conversationId + "/messages/media")
        .then()
                .statusCode(201)
                .body("type", equalTo("IMAGE"))
                .body("mediaContentType", equalTo("image/jpeg"))
                .body("mediaUrl", notNullValue())
                .extract().path("mediaUrl");

        // Participante lê normalmente.
        given()
                .auth().oauth2(bob.token())
        .when()
                .get(mediaUrl)
        .then()
                .statusCode(200)
                .header("Cache-Control", "private, max-age=31536000, immutable");

        // Quem não participa recebe 404 (e não 403) para não confirmar a existência.
        given()
                .auth().oauth2(carol.token())
        .when()
                .get(mediaUrl)
        .then()
                .statusCode(404)
                .body("error", equalTo("RESOURCE_NOT_FOUND"));
    }

    @Test
    void videoUploadIsRejectedWithBadRequest() {
        TestUser alice = createUser("Alice", "Video");
        TestUser bob = createUser("Bob", "Video");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        given()
                .auth().oauth2(alice.token())
                .multiPart("type", "VIDEO")
                .multiPart("file", "clipe.mp4", new byte[] { 1, 2, 3, 4 }, "video/mp4")
        .when()
                .post(CHATS_PATH + "/" + conversationId + "/messages/media")
        .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_INVALID_FORMAT"));
    }

    // ---------------------------------------------------------------- 6

    @Test
    void markAsReadOnlyAffectsMessagesFromTheOtherParticipant() {
        TestUser alice = createUser("Alice", "Leitura");
        TestUser bob = createUser("Bob", "Leitura");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        sendText(alice, conversationId, "mensagem da alice");
        sendText(bob, conversationId, "mensagem do bob 1");
        sendText(bob, conversationId, "mensagem do bob 2");

        given()
                .auth().oauth2(alice.token())
        .when()
                .put(CHATS_PATH + "/" + conversationId + "/read")
        .then()
                .statusCode(200)
                .body("conversationId", equalTo(conversationId))
                .body("markedCount", equalTo(2))
                .body("readAt", notNullValue());

        // A própria mensagem da Alice continua sem readAt; as do Bob foram marcadas.
        given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(200)
                .body("unreadCount", equalTo(0))
                .body("totalElements", equalTo(3))
                .body("messages.find { it.body == 'mensagem da alice' }.readAt", nullValue())
                .body("messages.find { it.body == 'mensagem do bob 1' }.readAt", notNullValue())
                .body("messages.find { it.body == 'mensagem do bob 2' }.readAt", notNullValue());
    }

    // ---------------------------------------------------------------- 7

    @Test
    void getMessagesWithSinceReturnsOnlyNewerMessagesInAscendingOrder() throws InterruptedException {
        TestUser alice = createUser("Alice", "Polling");
        TestUser bob = createUser("Bob", "Polling");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        sendText(alice, conversationId, "antiga");
        Thread.sleep(25);

        String serverTime = given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(200)
                .extract().path("serverTime");

        Thread.sleep(25);
        sendText(bob, conversationId, "nova 1");
        Thread.sleep(10);
        sendText(bob, conversationId, "nova 2");

        List<String> bodies = given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages?since=" + serverTime)
        .then()
                .statusCode(200)
                .body("unreadCount", equalTo(2))
                .body("serverTime", notNullValue())
                .extract().path("messages.body");

        assertEquals(List.of("nova 1", "nova 2"), bodies);
    }

    // ---------------------------------------------------------------- 8

    @Test
    void listConversationsIsSortedByLastMessageAndCarriesUnreadCount() throws InterruptedException {
        TestUser alice = createUser("Alice", "Lista");
        TestUser bob = createUser("Bob", "Lista");
        TestUser carol = createUser("Carol", "Lista");

        String conversationWithBob = openConversation(alice, createMatch(alice, bob, true));
        String conversationWithCarol = openConversation(alice, createMatch(alice, carol, true));

        sendText(bob, conversationWithBob, "oi da parte do bob");
        Thread.sleep(15);
        sendText(carol, conversationWithCarol, "oi da parte da carol");
        Thread.sleep(15);
        sendText(alice, conversationWithBob, "respondendo o bob");

        given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH)
        .then()
                .statusCode(200)
                .body("serverTime", notNullValue())
                .body("totalUnread", equalTo(2))
                .body("conversations.size()", equalTo(2))
                // A conversa com o Bob teve a última mensagem, então vem primeiro.
                .body("conversations[0].conversationId", equalTo(conversationWithBob))
                .body("conversations[0].unreadCount", equalTo(1))
                .body("conversations[0].otherUserName", equalTo("Bob Lista"))
                .body("conversations[0].lastMessage.preview", equalTo("respondendo o bob"))
                .body("conversations[0].lastMessage.fromMe", equalTo(true))
                .body("conversations[1].conversationId", equalTo(conversationWithCarol))
                .body("conversations[1].unreadCount", equalTo(1))
                .body("conversations[1].lastMessage.fromMe", equalTo(false));
    }

    // ---------------------------------------------------------------- 9

    @Test
    void everyEndpointRequiresAuthentication() {
        UUID anyId = UUID.randomUUID();

        given().when().get(CHATS_PATH).then().statusCode(401);
        given().when().post(CHATS_PATH + "?matchId=" + anyId).then().statusCode(401);
        given().when().get(CHATS_PATH + "/" + anyId + "/messages").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"body\":\"oi\"}")
                .when().post(CHATS_PATH + "/" + anyId + "/messages").then().statusCode(401);
        given().multiPart("type", "IMAGE").multiPart("file", "a.jpg", new byte[] { 1 }, "image/jpeg")
                .when().post(CHATS_PATH + "/" + anyId + "/messages/media").then().statusCode(401);
        given().when().get(CHATS_PATH + "/messages/" + anyId + "/media").then().statusCode(401);
        given().when().put(CHATS_PATH + "/" + anyId + "/read").then().statusCode(401);
    }

    // ---------------------------------------------------------------- 10

    @Test
    void malformedSinceReturnsBadRequest() {
        TestUser alice = createUser("Alice", "Since");
        TestUser bob = createUser("Bob", "Since");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages?since=ontem-a-tarde")
        .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_INVALID_FORMAT"));
    }

    @Test
    void emptyTextMessageIsRejected() {
        TestUser alice = createUser("Alice", "Vazia");
        TestUser bob = createUser("Bob", "Vazia");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        given()
                .auth().oauth2(alice.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"   \"}")
        .when()
                .post(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_INVALID_FORMAT"));
    }

    // ---------------------------------------------------------------- 12

    @Test
    void deliveryStatusProgressesFromSentToDeliveredToRead() {
        TestUser alice = createUser("Alice", "Entrega");
        TestUser bob = createUser("Bob", "Entrega");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        String messageId = given()
                .auth().oauth2(alice.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"oi bob\"}")
        .when()
                .post(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(201)
                .body("deliveredAt", nullValue())
                .body("readAt", nullValue())
                .body("editedAt", nullValue())
                .body("deletedAt", nullValue())
                .extract().path("id");

        // A própria remetente buscar a conversa NÃO entrega a mensagem.
        given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(200)
                .body("messages.find { it.id == '" + messageId + "' }.deliveredAt", nullValue());

        // Bob buscou a lista de conversas: entregue, mas ainda não vista.
        given()
                .auth().oauth2(bob.token())
        .when()
                .get(CHATS_PATH)
        .then()
                .statusCode(200);

        given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(200)
                .body("messages.find { it.id == '" + messageId + "' }.deliveredAt", notNullValue())
                .body("messages.find { it.id == '" + messageId + "' }.readAt", nullValue());

        // Bob abriu a conversa: vista.
        given()
                .auth().oauth2(bob.token())
        .when()
                .put(CHATS_PATH + "/" + conversationId + "/read")
        .then()
                .statusCode(200)
                .body("markedCount", equalTo(1));

        given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(200)
                .body("messages.find { it.id == '" + messageId + "' }.deliveredAt", notNullValue())
                .body("messages.find { it.id == '" + messageId + "' }.readAt", notNullValue());
    }

    // ---------------------------------------------------------------- 13

    @Test
    void statusChangesReachTheSenderThroughPolling() throws InterruptedException {
        TestUser alice = createUser("Alice", "Cursor");
        TestUser bob = createUser("Bob", "Cursor");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        sendText(alice, conversationId, "status por polling");
        Thread.sleep(25);

        String serverTime = given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(200)
                .extract().path("serverTime");

        // Nada mudou: o polling não devolve a mensagem de novo.
        given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages?since=" + serverTime)
        .then()
                .statusCode(200)
                .body("messages.size()", equalTo(0));

        Thread.sleep(25);

        given()
                .auth().oauth2(bob.token())
        .when()
                .put(CHATS_PATH + "/" + conversationId + "/read")
        .then()
                .statusCode(200);

        // A leitura moveu o cursor: a MESMA mensagem volta, agora com readAt.
        given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages?since=" + serverTime)
        .then()
                .statusCode(200)
                .body("messages.size()", equalTo(1))
                .body("messages[0].body", equalTo("status por polling"))
                .body("messages[0].fromMe", equalTo(true))
                .body("messages[0].readAt", notNullValue());
    }

    // ---------------------------------------------------------------- 14

    @Test
    void editOwnTextMessageMarksItEditedAndReachesTheOtherParticipant() throws InterruptedException {
        TestUser alice = createUser("Alice", "Edicao");
        TestUser bob = createUser("Bob", "Edicao");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        String messageId = given()
                .auth().oauth2(alice.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"texto original\"}")
        .when()
                .post(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(201)
                .extract().path("id");

        Thread.sleep(25);

        String bobServerTime = given()
                .auth().oauth2(bob.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(200)
                .body("messages[0].body", equalTo("texto original"))
                .extract().path("serverTime");

        Thread.sleep(25);

        given()
                .auth().oauth2(alice.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"  texto corrigido  \"}")
        .when()
                .put(CHATS_PATH + "/" + conversationId + "/messages/" + messageId)
        .then()
                .statusCode(200)
                .body("id", equalTo(messageId))
                .body("body", equalTo("texto corrigido"))
                .body("editedAt", notNullValue())
                .body("deletedAt", nullValue());

        // O polling do Bob recebe a versão editada.
        given()
                .auth().oauth2(bob.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages?since=" + bobServerTime)
        .then()
                .statusCode(200)
                .body("messages.size()", equalTo(1))
                .body("messages[0].id", equalTo(messageId))
                .body("messages[0].body", equalTo("texto corrigido"))
                .body("messages[0].editedAt", notNullValue());

        // Texto vazio é rejeitado.
        given()
                .auth().oauth2(alice.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"   \"}")
        .when()
                .put(CHATS_PATH + "/" + conversationId + "/messages/" + messageId)
        .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_INVALID_FORMAT"));
    }

    // ---------------------------------------------------------------- 15

    @Test
    void editOrDeleteByTheOtherParticipantReturnsForbidden() {
        TestUser alice = createUser("Alice", "Dona");
        TestUser bob = createUser("Bob", "Intruso");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        String messageId = given()
                .auth().oauth2(alice.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"minha mensagem\"}")
        .when()
                .post(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .auth().oauth2(bob.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"tentativa\"}")
        .when()
                .put(CHATS_PATH + "/" + conversationId + "/messages/" + messageId)
        .then()
                .statusCode(403)
                .body("error", equalTo("AUTH_FORBIDDEN"));

        given()
                .auth().oauth2(bob.token())
        .when()
                .delete(CHATS_PATH + "/" + conversationId + "/messages/" + messageId)
        .then()
                .statusCode(403)
                .body("error", equalTo("AUTH_FORBIDDEN"));

        // Mensagem inexistente na conversa: 404.
        given()
                .auth().oauth2(alice.token())
        .when()
                .delete(CHATS_PATH + "/" + conversationId + "/messages/" + UUID.randomUUID())
        .then()
                .statusCode(404)
                .body("error", equalTo("RESOURCE_NOT_FOUND"));

        // Nada mudou.
        given()
                .auth().oauth2(alice.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(200)
                .body("messages[0].body", equalTo("minha mensagem"))
                .body("messages[0].editedAt", nullValue())
                .body("messages[0].deletedAt", nullValue());
    }

    // ---------------------------------------------------------------- 16

    @Test
    void deleteOwnMessageKeepsItInHistoryWithoutContent() {
        TestUser alice = createUser("Alice", "Apaga");
        TestUser bob = createUser("Bob", "Apaga");
        String conversationId = openConversation(alice, createMatch(alice, bob, true));

        sendText(alice, conversationId, "fica");
        String messageId = given()
                .auth().oauth2(alice.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"some\"}")
        .when()
                .post(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .auth().oauth2(alice.token())
        .when()
                .delete(CHATS_PATH + "/" + conversationId + "/messages/" + messageId)
        .then()
                .statusCode(200)
                .body("id", equalTo(messageId))
                .body("type", equalTo("TEXT"))
                .body("body", nullValue())
                .body("mediaUrl", nullValue())
                .body("deletedAt", notNullValue());

        // Idempotente.
        given()
                .auth().oauth2(alice.token())
        .when()
                .delete(CHATS_PATH + "/" + conversationId + "/messages/" + messageId)
        .then()
                .statusCode(200)
                .body("deletedAt", notNullValue());

        // Editar depois de apagar: conflito.
        given()
                .auth().oauth2(alice.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"volta\"}")
        .when()
                .put(CHATS_PATH + "/" + conversationId + "/messages/" + messageId)
        .then()
                .statusCode(409)
                .body("error", equalTo("RESOURCE_CONFLICT"));

        // O histórico do Bob mantém a linha, sem conteúdo, e ela não conta como não lida.
        given()
                .auth().oauth2(bob.token())
        .when()
                .get(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(200)
                .body("totalElements", equalTo(2))
                .body("unreadCount", equalTo(1))
                .body("messages.find { it.id == '" + messageId + "' }.body", nullValue())
                .body("messages.find { it.id == '" + messageId + "' }.deletedAt", notNullValue())
                .body("messages.find { it.body == 'fica' }.deletedAt", nullValue());

        // A prévia da lista de conversas reflete a exclusão.
        given()
                .auth().oauth2(bob.token())
        .when()
                .get(CHATS_PATH)
        .then()
                .statusCode(200)
                .body("conversations[0].lastMessage.preview", equalTo("Mensagem apagada"))
                .body("conversations[0].unreadCount", equalTo(1));
    }

    // ---- apoio ----------------------------------------------------------

    private void sendText(TestUser sender, String conversationId, String body) {
        given()
                .auth().oauth2(sender.token())
                .contentType(ContentType.JSON)
                .body("{\"body\":\"" + body + "\"}")
        .when()
                .post(CHATS_PATH + "/" + conversationId + "/messages")
        .then()
                .statusCode(201)
                .body("type", equalTo("TEXT"))
                .body("body", equalTo(body))
                .body("mediaUrl", nullValue());
    }

    private String openConversation(TestUser user, UUID matchId) {
        return given()
                .auth().oauth2(user.token())
        .when()
                .post(CHATS_PATH + "?matchId=" + matchId)
        .then()
                .statusCode(200)
                .extract().path("conversationId");
    }

    private UUID createMatch(TestUser starter, TestUser pending, boolean mutual) {
        UUID matchId = UUIDv7Generator.generate();

        QuarkusTransaction.requiringNew().run(() -> {
            UserPossibleMatch match = new UserPossibleMatch();
            match.id = matchId;
            match.starterProfile = UserProfile.findById(starter.profileId());
            match.pendingProfile = UserProfile.findById(pending.profileId());
            match.createdAt = Instant.now();
            match.starterAccepted = true;
            match.pendingAccepted = mutual ? Boolean.TRUE : null;
            match.persist();
        });

        return matchId;
    }

    private TestUser createUser(String name, String lastName) {
        UUID userId = UUIDv7Generator.generate();
        UUID profileId = UUIDv7Generator.generate();
        String email = "chat-" + UUID.randomUUID() + "@example.com";

        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.id = userId;
            user.name = name;
            user.lastName = lastName;
            user.email = email;
            user.password = BcryptUtil.bcryptHash(RAW_PASSWORD);
            user.birthdate = LocalDate.now().minusYears(28);
            user.verified = true;
            user.lastUpdatedAt = Instant.now();
            user.persist();

            UserProfile profile = new UserProfile();
            profile.id = profileId;
            profile.user = user;
            profile.bio = "Perfil de teste do chat";
            profile.persist();
        });

        String token = given()
                .contentType(ContentType.JSON)
                .body(new SignInRequest(email, RAW_PASSWORD))
        .when()
                .post("/auth/signin")
        .then()
                .statusCode(200)
                .extract().as(TokenResponse.class)
                .accessToken();

        return new TestUser(userId, profileId, token);
    }

    private byte[] buildJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.MAGENTA);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLUE);
        graphics.fillOval(4, 4, width - 8, height - 8);
        graphics.dispose();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record TestUser(UUID userId, UUID profileId, String token) {
    }
}
