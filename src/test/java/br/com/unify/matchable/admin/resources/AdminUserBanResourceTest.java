package br.com.unify.matchable.admin.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.AdminTestSupport;
import br.com.unify.matchable.admin.AdminTestSupport.TestAccount;
import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.entity.ActiveConnection;
import br.com.unify.matchable.moderation.entity.UserReport;
import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Semana 06 (B) — banimento com efeito real nas quatro camadas (signin,
 * refresh, sessões revogadas, access token já emitido) e em cascata nas
 * denúncias pendentes.
 */
@QuarkusTest
class AdminUserBanResourceTest {

    private static final String REASON = "Assédio reiterado a outros membros em comentários de comunidade.";

    @Test
    void banCutsEveryAccessPathAndResolvesPendingReports() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount target = AdminTestSupport.member("Alvo");
        TestAccount ana = AdminTestSupport.member("Ana");
        TestAccount bia = AdminTestSupport.member("Bia");
        TestAccount viewer = AdminTestSupport.member("Visitante");

        // Bia tem duas pendentes (uma já em análise): só uma pode estar OPEN por vez.
        AdminTestSupport.report(ana, target, ReportReason.HARASSMENT);
        UUID underReview = AdminTestSupport.report(bia, target, ReportReason.FAKE_PROFILE);
        QuarkusTransaction.requiringNew().run(() -> {
            UserReport report = UserReport.findById(underReview);
            report.status = UserReportStatus.UNDER_REVIEW;
        });
        AdminTestSupport.report(bia, target, ReportReason.SCAM);

        given().auth().oauth2(viewer.token())
                .when().get("/users/me/profile/public?userProfileId=" + target.profileId())
                .then().statusCode(200);

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"" + REASON + "\"}")
        .when()
                .post("/admin/users/" + target.userId() + "/ban")
        .then()
                .statusCode(200)
                .body("banned", equalTo(true))
                .body("banReason", equalTo(REASON))
                .body("bannedAt", notNullValue())
                .body("activeSessions", equalTo(0))
                .body("pendingReportsReceived", equalTo(0))
                .body("reportsReceived", equalTo(3));

        // Camada 3: nenhuma sessão viva.
        long liveSessions = QuarkusTransaction.requiringNew().call(() ->
                ActiveConnection.count("user.id = ?1 and revoked = false", target.userId()));
        assertEquals(0, liveSessions);

        // Camada 4: o access token emitido ANTES do ban para de funcionar.
        given().auth().oauth2(target.token())
                .when().get("/users/me")
                .then().statusCode(403)
                .body("code", equalTo(4003))
                .body("error", equalTo("USER_DISABLED"))
                .body("message", containsString(REASON));

        // Camada 2: refresh capturado antes do ban é recusado.
        given().contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"" + target.tokens().refreshToken() + "\"}")
                .when().post("/auth/refresh")
                .then().statusCode(403)
                .body("error", equalTo("USER_DISABLED"));

        // Camada 1: senha certa -> 403 com motivo; senha errada continua 401 (não vaza o ban).
        given().contentType(ContentType.JSON)
                .body(new SignInRequest(target.email(), AdminTestSupport.RAW_PASSWORD))
                .when().post("/auth/signin")
                .then().statusCode(403)
                .body("error", equalTo("USER_DISABLED"))
                .body("message", containsString(REASON));
        given().contentType(ContentType.JSON)
                .body(new SignInRequest(target.email(), "SenhaErrada@123"))
                .when().post("/auth/signin")
                .then().statusCode(401)
                .body("error", equalTo("AUTH_INVALID_CREDENTIALS"));

        // Some para os outros usuários.
        given().auth().oauth2(viewer.token())
                .when().get("/users/me/profile/public?userProfileId=" + target.profileId())
                .then().statusCode(404);
        given().auth().oauth2(viewer.token())
                .when().get("/users/" + target.profileId() + "/posts")
                .then().statusCode(404);

        // Cascata: as 3 pendentes viram RESOLVED com nota e revisor; 1 aviso por denunciante.
        List<UserReport> reports = QuarkusTransaction.requiringNew().call(() -> {
            List<UserReport> list = UserReport.list("reportedUser.id", target.userId());
            list.forEach(report -> report.reviewedBy.id.toString());
            return list;
        });
        assertEquals(3, reports.size());
        reports.forEach(report -> {
            assertEquals(UserReportStatus.RESOLVED, report.status);
            assertTrue(report.adminNote.startsWith("Encerrada pelo banimento"));
            assertEquals(admin.userId(), report.reviewedBy.id);
        });
        assertEquals(1, reportUpdatesOf(ana));
        assertEquals(1, reportUpdatesOf(bia), "duas denúncias da mesma pessoa geram um aviso só");
    }

    @Test
    void banValidatesReasonAndProtectsAdminAccounts() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount otherAdmin = AdminTestSupport.admin("Outra");
        TestAccount target = AdminTestSupport.member("Alvo");

        ban(admin, target.userId(), "{\"reason\":\"curto\"}").statusCode(400);
        ban(admin, target.userId(), "{\"reason\":null}").statusCode(400);
        ban(admin, target.userId(), "{\"reason\":\"" + "x".repeat(501) + "\"}").statusCode(400);
        ban(admin, otherAdmin.userId(), "{\"reason\":\"" + REASON + "\"}").statusCode(409)
                .body("message", containsString("administrativas"));
        ban(admin, admin.userId(), "{\"reason\":\"" + REASON + "\"}").statusCode(409);
        ban(admin, UUID.randomUUID(), "{\"reason\":\"" + REASON + "\"}").statusCode(404);

        ban(admin, target.userId(), "{\"reason\":\"" + REASON + "\"}").statusCode(200);
        ban(admin, target.userId(), "{\"reason\":\"" + REASON + "\"}").statusCode(409);
    }

    @Test
    void unbanRestoresAccessWithANewLogin() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount target = AdminTestSupport.member("Alvo");

        ban(admin, target.userId(), "{\"reason\":\"" + REASON + "\"}").statusCode(200);

        given().auth().oauth2(admin.token())
                .when().post("/admin/users/" + target.userId() + "/unban")
                .then().statusCode(200)
                .body("banned", equalTo(false))
                .body("bannedAt", nullValue())
                .body("banReason", nullValue());

        given().auth().oauth2(admin.token())
                .when().post("/admin/users/" + target.userId() + "/unban")
                .then().statusCode(409);

        String freshToken = AdminTestSupport.signIn(target.email()).accessToken();
        given().auth().oauth2(freshToken).when().get("/users/me").then().statusCode(200).body("banned", equalTo(false));
    }

    @Test
    void searchFiltersByTermAndState() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        String marker = "Busca" + UUID.randomUUID().toString().substring(0, 8);
        TestAccount active = AdminTestSupport.member(marker);
        TestAccount banned = AdminTestSupport.member(marker);
        ban(admin, banned.userId(), "{\"reason\":\"" + REASON + "\"}").statusCode(200);

        given().auth().oauth2(admin.token())
                .when().get("/admin/users?query=" + marker.toLowerCase())
                .then().statusCode(200)
                .body("totalElements", equalTo(2))
                .body("content.id", hasItem(active.userId().toString()));

        given().auth().oauth2(admin.token())
                .when().get("/admin/users?filter=BANNED&query=" + marker)
                .then().statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].id", equalTo(banned.userId().toString()))
                .body("content.banned", everyItem(equalTo(true)));

        given().auth().oauth2(admin.token())
                .when().get("/admin/users?filter=ADMIN")
                .then().statusCode(200)
                .body("content.role", everyItem(equalTo("ADMIN")))
                .body("content.id", not(hasItem(active.userId().toString())));

        given().auth().oauth2(admin.token())
                .when().get("/admin/users?filter=QUALQUER")
                .then().statusCode(400);
    }

    @Test
    void databaseRejectsInconsistentBanState() {
        TestAccount target = AdminTestSupport.member("Alvo");

        assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew().run(() ->
                User.getEntityManager()
                        .createNativeQuery("update users set banned = true where id = :id")
                        .setParameter("id", target.userId())
                        .executeUpdate()));

        // Com os três campos juntos o banco aceita.
        QuarkusTransaction.requiringNew().run(() -> User.getEntityManager()
                .createNativeQuery("update users set banned = true, banned_at = :now, ban_reason = 'motivo' where id = :id")
                .setParameter("now", Instant.now())
                .setParameter("id", target.userId())
                .executeUpdate());
    }

    private static io.restassured.response.ValidatableResponse ban(TestAccount admin, UUID userId, String body) {
        return given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/admin/users/" + userId + "/ban")
                .then();
    }

    private static long reportUpdatesOf(TestAccount account) {
        return QuarkusTransaction.requiringNew().call(() -> Notification.count(
                "recipient.id = ?1 and type = ?2", account.userId(), NotificationType.REPORT_UPDATE));
    }
}
