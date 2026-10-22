package br.com.unify.matchable.admin.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.AdminTestSupport;
import br.com.unify.matchable.admin.AdminTestSupport.TestAccount;
import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.enums.NotificationType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/** Semana 06 (B) — fila de moderação: filtros, histórico e máquina de estados. */
@QuarkusTest
class AdminReportResourceTest {

    private static final String NOTE = "Conteúdo revisado: viola as diretrizes da comunidade.";

    @Test
    void queueFiltersByStatusAndReportedUser() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount target = AdminTestSupport.member("Alvo");
        TestAccount ana = AdminTestSupport.member("Ana");
        TestAccount bia = AdminTestSupport.member("Bia");
        UUID first = AdminTestSupport.report(ana, target, ReportReason.HARASSMENT);
        AdminTestSupport.report(bia, target, ReportReason.SCAM);
        updateStatus(admin, first, "{\"status\":\"UNDER_REVIEW\"}").statusCode(200);

        given().auth().oauth2(admin.token())
                .when().get("/admin/reports?status=OPEN&reportedUserId=" + target.userId())
                .then().statusCode(200)
                .body("content", hasSize(1))
                .body("content.status", everyItem(equalTo("OPEN")))
                .body("totalElements", equalTo(1))
                .body("totalPages", equalTo(1))
                .body("hasNext", equalTo(false));

        given().auth().oauth2(admin.token())
                .when().get("/admin/reports?status=PENDING&reportedUserId=" + target.userId())
                .then().statusCode(200)
                .body("totalElements", equalTo(2));

        given().auth().oauth2(admin.token())
                .when().get("/admin/reports?status=ALL&size=1&reportedUserId=" + target.userId())
                .then().statusCode(200)
                .body("content", hasSize(1))
                .body("totalPages", equalTo(2))
                .body("hasNext", equalTo(true));

        given().auth().oauth2(admin.token())
                .when().get("/admin/reports?status=ABERTA")
                .then().statusCode(400);
    }

    @Test
    void detailBringsReportedUserAndHistory() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount target = AdminTestSupport.member("Alvo");
        TestAccount ana = AdminTestSupport.member("Ana");
        TestAccount bia = AdminTestSupport.member("Bia");
        AdminTestSupport.report(ana, target, ReportReason.HARASSMENT);
        UUID latest = AdminTestSupport.report(bia, target, ReportReason.SCAM);

        given().auth().oauth2(admin.token())
                .when().get("/admin/reports/" + latest)
                .then().statusCode(200)
                .body("report.id", equalTo(latest.toString()))
                .body("report.reasonLabel", equalTo("Golpe ou fraude"))
                .body("report.reporter.id", equalTo(bia.userId().toString()))
                .body("report.reportedPost", nullValue())
                .body("reportedUser.id", equalTo(target.userId().toString()))
                .body("reportedUser.userProfileId", equalTo(target.profileId().toString()))
                .body("reportedUser.pendingReportsReceived", equalTo(2))
                .body("reportedUserHistory", hasSize(2))
                .body("reportedUserHistory[0].id", equalTo(latest.toString()));

        given().auth().oauth2(admin.token())
                .when().get("/admin/reports/" + latest + "/post-media")
                .then().statusCode(404);
        given().auth().oauth2(admin.token())
                .when().get("/admin/reports/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void statusTransitionsAreValidatedAndClosingNotifiesTheReporter() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount target = AdminTestSupport.member("Alvo");
        TestAccount reporter = AdminTestSupport.member("Ana");
        UUID reportId = AdminTestSupport.report(reporter, target, ReportReason.HARASSMENT);

        updateStatus(admin, reportId, "{\"status\":\"UNDER_REVIEW\",\"adminNote\":null}").statusCode(200)
                .body("status", equalTo("UNDER_REVIEW"))
                .body("reviewedByName", equalTo("Gestora Teste"))
                .body("resolvedAt", nullValue());
        assertEquals(0, reportUpdatesOf(reporter), "em análise não avisa ninguém");

        updateStatus(admin, reportId, "{\"status\":\"UNDER_REVIEW\"}").statusCode(409);
        updateStatus(admin, reportId, "{\"status\":\"OPEN\"}").statusCode(409);
        updateStatus(admin, reportId, "{\"status\":\"RESOLVED\"}").statusCode(400)
                .body("message", containsString("nota"));
        updateStatus(admin, reportId, "{\"status\":\"RESOLVED\",\"adminNote\":\"curta\"}").statusCode(400);
        updateStatus(admin, reportId, "{\"status\":\"QUALQUER\",\"adminNote\":\"" + NOTE + "\"}").statusCode(400);

        updateStatus(admin, reportId, "{\"status\":\"RESOLVED\",\"adminNote\":\"" + NOTE + "\"}").statusCode(200)
                .body("status", equalTo("RESOLVED"))
                .body("adminNote", equalTo(NOTE))
                .body("resolvedAt", notNullValue());
        assertEquals(1, reportUpdatesOf(reporter));

        updateStatus(admin, reportId, "{\"status\":\"UNDER_REVIEW\"}").statusCode(409)
                .body("message", containsString("encerrada"));
    }

    @Test
    void dismissingDirectlyFromOpenIsAllowed() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount target = AdminTestSupport.member("Alvo");
        TestAccount reporter = AdminTestSupport.member("Ana");
        UUID reportId = AdminTestSupport.report(reporter, target, ReportReason.OTHER);

        updateStatus(admin, reportId, "{\"status\":\"DISMISSED\",\"adminNote\":\"Sem violação das diretrizes.\"}")
                .statusCode(200)
                .body("status", equalTo("DISMISSED"));
        assertEquals(1, reportUpdatesOf(reporter));
    }

    @Test
    void reportUnderReviewStillBlocksADuplicateFromTheSameReporter() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount target = AdminTestSupport.member("Alvo");
        TestAccount reporter = AdminTestSupport.member("Ana");
        UUID reportId = AdminTestSupport.report(reporter, target, ReportReason.HARASSMENT);
        updateStatus(admin, reportId, "{\"status\":\"UNDER_REVIEW\"}").statusCode(200);

        given().auth().oauth2(reporter.token())
                .contentType(ContentType.JSON)
                .body("{\"reportedUserId\":\"" + target.userId() + "\",\"reason\":\"HARASSMENT\"}")
                .when().post("/users/reports")
                .then().statusCode(409);
    }

    private static ValidatableResponse updateStatus(TestAccount admin, UUID reportId, String body) {
        return given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body(body)
                .when().put("/admin/reports/" + reportId + "/status")
                .then();
    }

    private static long reportUpdatesOf(TestAccount account) {
        return QuarkusTransaction.requiringNew().call(() -> Notification.count(
                "recipient.id = ?1 and type = ?2", account.userId(), NotificationType.REPORT_UPDATE));
    }
}
