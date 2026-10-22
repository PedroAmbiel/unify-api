package br.com.unify.matchable.admin.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.AdminTestSupport;
import br.com.unify.matchable.admin.AdminTestSupport.TestAccount;
import br.com.unify.matchable.admin.entity.AdminAuditLog;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.moderation.entity.UserReport;
import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.post.entity.PostComment;
import br.com.unify.matchable.post.entity.PostLike;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/**
 * Semana 07 — moderação de publicações denunciadas (comunidade e pessoais):
 * fila agrupada por post, remoção definitiva e "manter no ar".
 */
@QuarkusTest
class AdminPostModerationResourceTest {

    private static final String REASON = "Discurso de ódio contra pessoas com deficiência.";
    private static final String NOTE = "Conteúdo revisado: não viola as diretrizes.";

    @AfterEach
    void retireContent() {
        AdminTestSupport.retireContent();
    }

    @Test
    void queueShowsEachReportedPostOnceWithItsCounts() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount author = AdminTestSupport.member("Autora");
        TestAccount ana = AdminTestSupport.member("Ana");
        TestAccount bia = AdminTestSupport.member("Bia");
        UUID postId = AdminTestSupport.personalPost(author, "Texto denunciado duas vezes");
        AdminTestSupport.reportPost(ana, postId, ReportReason.HATE_SPEECH);
        UUID secondReport = AdminTestSupport.reportPost(bia, postId, ReportReason.INAPPROPRIATE_CONTENT);
        updateReportStatus(admin, secondReport, "{\"status\":\"UNDER_REVIEW\"}");

        String item = "content.find { it.post.id == '" + postId + "' }";
        given().auth().oauth2(admin.token())
                .when().get("/admin/posts/reported?size=100")
                .then().statusCode(200)
                .body("content.findAll { it.post.id == '" + postId + "' }", hasSize(1))
                .body(item + ".reportsTotal", equalTo(2))
                .body(item + ".reportsPending", equalTo(2))
                .body(item + ".lastReportedAt", notNullValue())
                .body(item + ".author.id", equalTo(author.userId().toString()))
                .body(item + ".post.origin", equalTo("PERSONAL"))
                .body(item + ".post.moderatedAt", nullValue());

        given().auth().oauth2(admin.token())
                .when().get("/admin/posts/" + postId)
                .then().statusCode(200)
                .body("post.body", equalTo("Texto denunciado duas vezes"))
                .body("author.id", equalTo(author.userId().toString()))
                .body("author.pendingReportsReceived", equalTo(2))
                .body("reports", hasSize(2))
                .body("reports[0].id", equalTo(secondReport.toString()));

        given().auth().oauth2(admin.token())
                .when().get("/admin/posts/reported?filter=QUALQUER")
                .then().statusCode(400);
        given().auth().oauth2(admin.token())
                .when().get("/admin/posts/" + UUID.randomUUID())
                .then().statusCode(404);
        given().auth().oauth2(admin.token())
                .when().get("/admin/posts/" + postId + "/media")
                .then().statusCode(404);
    }

    @Test
    void removingIsDefinitiveClosesTheReportsAndWarnsEveryone() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount author = AdminTestSupport.member("Autora");
        TestAccount ana = AdminTestSupport.member("Ana");
        TestAccount bia = AdminTestSupport.member("Bia");
        UUID postId = AdminTestSupport.personalPost(author, "Publicação ofensiva\n\ncom quebra de linha");
        AdminTestSupport.reportPost(ana, postId, ReportReason.HATE_SPEECH);
        AdminTestSupport.reportPost(bia, postId, ReportReason.HARASSMENT);

        given().auth().oauth2(ana.token())
                .when().get("/users/posts/" + postId)
                .then().statusCode(200);

        remove(admin, postId, "{\"reason\":\"curto\"}").statusCode(400)
                .body("message", containsString("motivo"));
        remove(admin, postId, "{}").statusCode(400);

        remove(admin, postId, "{\"reason\":\"" + REASON + "\"}").statusCode(200)
                .body("post.active", equalTo(false))
                .body("post.moderatedAt", notNullValue())
                .body("post.moderatedByName", equalTo("Gestora Teste"))
                .body("post.moderationReason", equalTo(REASON))
                .body("reports", hasSize(2))
                .body("reports.status", everyItem(equalTo("RESOLVED")))
                .body("reports.adminNote", everyItem(startsWith("Publicação removida pela moderação")));

        // some para todos, inclusive para o autor, e não há como mexer nela de novo
        given().auth().oauth2(ana.token())
                .when().get("/users/posts/" + postId)
                .then().statusCode(404);
        given().auth().oauth2(author.token())
                .when().get("/users/posts/" + postId)
                .then().statusCode(404);
        given().auth().oauth2(author.token())
                .when().delete("/users/posts/" + postId)
                .then().statusCode(404);
        Post stored = QuarkusTransaction.requiringNew().call(() -> Post.<Post>findById(postId));
        assertFalse(stored.active);
        assertNotNull(stored.moderatedAt);

        remove(admin, postId, "{\"reason\":\"" + REASON + "\"}").statusCode(409);
        keep(admin, postId, "{\"adminNote\":\"" + NOTE + "\"}").statusCode(409);

        // quem denunciou recebe "resolvida"; a autora recebe o aviso com o começo do texto
        assertEquals(1, notificationsOf(ana, NotificationType.REPORT_UPDATE));
        assertEquals(1, notificationsOf(bia, NotificationType.REPORT_UPDATE));
        List<Notification> authorWarnings = QuarkusTransaction.requiringNew().call(() -> Notification.<Notification>list(
                "recipient.id = ?1 and type = ?2", author.userId(), NotificationType.POST_REMOVED));
        assertEquals(1, authorWarnings.size());
        assertEquals(postId, authorWarnings.get(0).referenceId);
        assertTrue(authorWarnings.get(0).body.contains("\"Publicação ofensiva com quebra de linha\""),
                authorWarnings.get(0).body);
        assertFalse(authorWarnings.get(0).body.contains(REASON), "o motivo interno não vai para o autor");

        long audits = QuarkusTransaction.requiringNew().call(() -> AdminAuditLog.count(
                "action = ?1 and targetId = ?2", "POST_REMOVE", postId.toString()));
        assertEquals(1, audits);

        // a fila de pendentes não mostra mais o post; a de removidas mostra
        given().auth().oauth2(admin.token())
                .when().get("/admin/posts/reported?size=100")
                .then().statusCode(200)
                .body("content.post.id", not(hasItem(postId.toString())));
        given().auth().oauth2(admin.token())
                .when().get("/admin/posts/reported?filter=REMOVED&size=100")
                .then().statusCode(200)
                .body("content.post.id", hasItem(postId.toString()));

        // post removido não volta para a fila por uma denúncia nova
        TestAccount carla = AdminTestSupport.member("Carla");
        given().auth().oauth2(carla.token())
                .contentType(ContentType.JSON)
                .body("{\"reportedUserId\":\"" + author.userId() + "\",\"reportedPostId\":\"" + postId
                        + "\",\"reason\":\"HATE_SPEECH\"}")
                .when().post("/users/reports")
                .then().statusCode(404);
    }

    @Test
    void removingACommunityPostHidesItFromTheCommunity() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount author = AdminTestSupport.member("Autora");
        TestAccount ana = AdminTestSupport.member("Ana");
        UUID postId = AdminTestSupport.communityPost(author, "Post de comunidade denunciado");
        UUID reportId = AdminTestSupport.reportPost(ana, postId, ReportReason.SCAM);

        given().auth().oauth2(ana.token())
                .when().get("/communities/posts/" + postId)
                .then().statusCode(200);

        remove(admin, postId, "{\"reason\":\"" + REASON + "\"}").statusCode(200)
                .body("post.origin", equalTo("COMMUNITY"))
                .body("post.communityName", startsWith("Comunidade "));

        given().auth().oauth2(ana.token())
                .when().get("/communities/posts/" + postId)
                .then().statusCode(404);

        // a denúncia individual também mostra o post como removido pela moderação
        given().auth().oauth2(admin.token())
                .when().get("/admin/reports/" + reportId)
                .then().statusCode(200)
                .body("report.status", equalTo("RESOLVED"))
                .body("report.reportedPost.moderatedAt", notNullValue());

        UUID communityId = QuarkusTransaction.requiringNew().call(() -> Post.<Post>findById(postId).community.id);
        Notification warning = QuarkusTransaction.requiringNew().call(() -> Notification.<Notification>find(
                "recipient.id = ?1 and type = ?2", author.userId(), NotificationType.POST_REMOVED).firstResult());
        assertEquals(communityId, warning.contextId);
    }

    @Test
    void keepingDismissesThePendingReportsAndLeavesThePostOnline() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount author = AdminTestSupport.member("Autora");
        TestAccount ana = AdminTestSupport.member("Ana");
        UUID postId = AdminTestSupport.personalPost(author, "Publicação que vai ficar");
        AdminTestSupport.reportPost(ana, postId, ReportReason.OTHER);

        keep(admin, postId, "{\"adminNote\":\"curta\"}").statusCode(400);

        keep(admin, postId, "{\"adminNote\":\"" + NOTE + "\"}").statusCode(200)
                .body("post.active", equalTo(true))
                .body("post.moderatedAt", nullValue())
                .body("reports[0].status", equalTo("DISMISSED"))
                .body("reports[0].adminNote", startsWith("Publicação mantida pela moderação"));

        given().auth().oauth2(ana.token())
                .when().get("/users/posts/" + postId)
                .then().statusCode(200);
        assertEquals(1, notificationsOf(ana, NotificationType.REPORT_UPDATE));
        assertEquals(0, notificationsOf(author, NotificationType.POST_REMOVED));

        keep(admin, postId, "{\"adminNote\":\"" + NOTE + "\"}").statusCode(409)
                .body("message", containsString("pendentes"));
    }

    @Test
    void removingAPostAlreadyDeletedByTheAuthorDoesNotWarnTheAuthor() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount author = AdminTestSupport.member("Autora");
        TestAccount ana = AdminTestSupport.member("Ana");
        UUID postId = AdminTestSupport.personalPost(author, "Apagada antes da moderação");
        UUID reportId = AdminTestSupport.reportPost(ana, postId, ReportReason.HARASSMENT);
        given().auth().oauth2(author.token())
                .when().delete("/users/posts/" + postId)
                .then().statusCode(204);

        remove(admin, postId, "{\"reason\":\"" + REASON + "\"}").statusCode(200);

        assertEquals(0, notificationsOf(author, NotificationType.POST_REMOVED));
        UserReport report = QuarkusTransaction.requiringNew().call(() -> UserReport.<UserReport>findById(reportId));
        assertEquals(UserReportStatus.RESOLVED, report.status);
    }

    /**
     * Post de comunidade apagado no app virou soft delete (antes a linha era
     * apagada e a denúncia perdia o post): some do app, mas curtidas,
     * comentários e o vínculo da denúncia continuam, e a moderação decide
     * sobre ele igual ao post pessoal.
     */
    @Test
    void communityPostDeletedInTheAppStaysLinkedToItsReports() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount author = AdminTestSupport.member("Autora");
        TestAccount ana = AdminTestSupport.member("Ana");
        UUID postId = AdminTestSupport.communityPost(author, "Post de comunidade apagado pela autora");
        UUID reportId = AdminTestSupport.reportPost(ana, postId, ReportReason.HATE_SPEECH);
        QuarkusTransaction.requiringNew().run(() -> {
            Post post = Post.findById(postId);
            PostLike like = new PostLike();
            like.id = UUIDv7Generator.generate();
            like.post = post;
            like.user = User.findById(ana.userId());
            like.createdAt = Instant.now();
            like.persist();

            PostComment comment = new PostComment();
            comment.id = UUIDv7Generator.generate();
            comment.post = post;
            comment.author = User.findById(ana.userId());
            comment.body = "Comentário que precisa sobreviver como evidência";
            comment.createdAt = Instant.now();
            comment.persist();
        });

        given().auth().oauth2(author.token())
                .when().delete("/communities/posts/" + postId)
                .then().statusCode(204);

        // some do app para todos, inclusive para a autora
        given().auth().oauth2(ana.token())
                .when().get("/communities/posts/" + postId)
                .then().statusCode(404);
        given().auth().oauth2(author.token())
                .when().delete("/communities/posts/" + postId)
                .then().statusCode(404);

        // a linha continua, com curtidas e comentários
        Post stored = QuarkusTransaction.requiringNew().call(() -> Post.<Post>findById(postId));
        assertNotNull(stored, "post de comunidade não é mais apagado fisicamente");
        assertFalse(stored.active);
        assertNull(stored.moderatedAt);
        assertEquals(1L, (long) QuarkusTransaction.requiringNew().call(() -> PostLike.count("post.id", postId)));
        assertEquals(1L, (long) QuarkusTransaction.requiringNew().call(() -> PostComment.count("post.id", postId)));

        // a denúncia continua apontando para o post, e a moderação ainda decide
        given().auth().oauth2(admin.token())
                .when().get("/admin/reports/" + reportId)
                .then().statusCode(200)
                .body("report.reportedPost.id", equalTo(postId.toString()))
                .body("report.reportedPost.active", equalTo(false));
        given().auth().oauth2(admin.token())
                .when().get("/admin/posts/reported?size=100")
                .then().statusCode(200)
                .body("content.post.id", hasItem(postId.toString()));

        remove(admin, postId, "{\"reason\":\"" + REASON + "\"}").statusCode(200)
                .body("post.moderatedAt", notNullValue())
                .body("reports[0].status", equalTo("RESOLVED"));
        assertEquals(0, notificationsOf(author, NotificationType.POST_REMOVED), "post já apagado: autora não é avisada");
        assertEquals(1, notificationsOf(ana, NotificationType.REPORT_UPDATE));
    }

    @Test
    void onlyAdminsModeratePosts() {
        TestAccount member = AdminTestSupport.member("Comum");
        TestAccount author = AdminTestSupport.member("Autora");
        UUID postId = AdminTestSupport.personalPost(author, "Post");

        given().when().get("/admin/posts/reported").then().statusCode(401);
        given().auth().oauth2(member.token())
                .when().get("/admin/posts/reported")
                .then().statusCode(403);
        remove(member, postId, "{\"reason\":\"" + REASON + "\"}").statusCode(403);

        Post stored = QuarkusTransaction.requiringNew().call(() -> Post.<Post>findById(postId));
        assertTrue(stored.active);
        assertNull(stored.moderatedAt);
    }

    private static ValidatableResponse remove(TestAccount admin, UUID postId, String body) {
        return given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/admin/posts/" + postId + "/remove")
                .then();
    }

    private static ValidatableResponse keep(TestAccount admin, UUID postId, String body) {
        return given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/admin/posts/" + postId + "/keep")
                .then();
    }

    private static void updateReportStatus(TestAccount admin, UUID reportId, String body) {
        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body(body)
                .when().put("/admin/reports/" + reportId + "/status")
                .then().statusCode(200);
    }

    private static long notificationsOf(TestAccount account, NotificationType type) {
        return QuarkusTransaction.requiringNew().call(() -> Notification.count(
                "recipient.id = ?1 and type = ?2", account.userId(), type));
    }
}
