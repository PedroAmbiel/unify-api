package br.com.unify.matchable.user.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.AdminTestSupport;
import br.com.unify.matchable.admin.AdminTestSupport.TestAccount;
import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.auth.entity.ActiveConnection;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * "Minha conta" do backoffice: dados da conta e troca de senha com a senha
 * atual (HTTP real + JWT real).
 */
@QuarkusTest
class UserAccountResourceTest {

    private static final String NEW_PASSWORD = "NovaSenha@456";

    @Test
    void updatesNameAndNormalizesCellphone() {
        TestAccount account = AdminTestSupport.admin("Gestora");

        given()
                .auth().oauth2(account.token())
                .contentType(ContentType.JSON)
                .body(Map.of("name", "  Maria   Clara ", "lastName", "Souza", "cellphone", "+55 (19) 99876-5432"))
        .when()
                .put("/users/me/account")
        .then()
                .statusCode(200)
                .body("name", equalTo("Maria Clara"))
                .body("lastName", equalTo("Souza"))
                .body("cellphone", equalTo("5519998765432"))
                .body("role", equalTo("ADMIN"));

        given()
                .auth().oauth2(account.token())
        .when()
                .get("/users/me")
        .then()
                .statusCode(200)
                .body("name", equalTo("Maria Clara"))
                .body("cellphone", equalTo("5519998765432"));
    }

    @Test
    void blankCellphoneClearsItAndInvalidInputIsRejected() {
        TestAccount account = AdminTestSupport.member("Ana");

        given()
                .auth().oauth2(account.token())
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Ana", "lastName", "Teste", "cellphone", ""))
        .when()
                .put("/users/me/account")
        .then()
                .statusCode(200)
                .body("cellphone", nullValue());

        given()
                .auth().oauth2(account.token())
                .contentType(ContentType.JSON)
                .body(Map.of("name", " ", "lastName", "Teste"))
        .when()
                .put("/users/me/account")
        .then()
                .statusCode(400)
                .body("message", containsString("Informe o nome"));

        given()
                .auth().oauth2(account.token())
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Ana", "lastName", "Teste", "cellphone", "1234"))
        .when()
                .put("/users/me/account")
        .then()
                .statusCode(400)
                .body("message", containsString("Celular"));
    }

    @Test
    void cellphoneOfAnotherAccountConflicts() {
        TestAccount owner = AdminTestSupport.member("Dona");
        TestAccount other = AdminTestSupport.member("Outra");
        String phone = "55119" + String.valueOf(System.nanoTime()).substring(0, 8);

        given()
                .auth().oauth2(owner.token())
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Dona", "lastName", "Teste", "cellphone", phone))
        .when()
                .put("/users/me/account")
        .then()
                .statusCode(200);

        given()
                .auth().oauth2(other.token())
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Outra", "lastName", "Teste", "cellphone", phone))
        .when()
                .put("/users/me/account")
        .then()
                .statusCode(409);
    }

    @Test
    void changePasswordRevokesOtherSessionsAndKeepsCallerSignedIn() {
        TestAccount account = AdminTestSupport.member("Bia");
        // Segunda sessão da mesma conta (ex.: o app no celular).
        TokenResponse otherDevice = AdminTestSupport.signIn(account.email());

        TokenResponse fresh = given()
                .auth().oauth2(account.token())
                .contentType(ContentType.JSON)
                .body(Map.of("currentPassword", AdminTestSupport.RAW_PASSWORD, "newPassword", NEW_PASSWORD))
        .when()
                .put("/users/me/account/password")
        .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .extract().as(TokenResponse.class);

        String hash = QuarkusTransaction.requiringNew().call(() -> User.<User>findById(account.userId()).password);
        assertTrue(BcryptUtil.matches(NEW_PASSWORD, hash));

        // Só a sessão nova continua ativa: a do outro aparelho não renova mais.
        long active = QuarkusTransaction.requiringNew().call(() ->
                ActiveConnection.count("user.id = ?1 and revoked = false", account.userId()));
        assertEquals(1, active);
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("refreshToken", otherDevice.refreshToken()))
        .when()
                .post("/auth/refresh")
        .then()
                .statusCode(401);
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("refreshToken", fresh.refreshToken()))
        .when()
                .post("/auth/refresh")
        .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body(new SignInRequest(account.email(), NEW_PASSWORD))
        .when()
                .post("/auth/signin")
        .then()
                .statusCode(200);
    }

    @Test
    void wrongCurrentPasswordIs400NotUnauthorized() {
        TestAccount account = AdminTestSupport.member("Caio");

        given()
                .auth().oauth2(account.token())
                .contentType(ContentType.JSON)
                .body(Map.of("currentPassword", "Errada@123", "newPassword", NEW_PASSWORD))
        .when()
                .put("/users/me/account/password")
        .then()
                .statusCode(400)
                .body("error", equalTo("USER_CURRENT_PASSWORD_INVALID"))
                .body("code", equalTo(4008));

        long active = QuarkusTransaction.requiringNew().call(() ->
                ActiveConnection.count("user.id = ?1 and revoked = false", account.userId()));
        assertEquals(1, active);
    }

    @Test
    void weakOrRepeatedNewPasswordIsRejected() {
        TestAccount account = AdminTestSupport.member("Duda");

        given()
                .auth().oauth2(account.token())
                .contentType(ContentType.JSON)
                .body(Map.of("currentPassword", AdminTestSupport.RAW_PASSWORD, "newPassword", "fraca123"))
        .when()
                .put("/users/me/account/password")
        .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_INVALID_FORMAT"));

        given()
                .auth().oauth2(account.token())
                .contentType(ContentType.JSON)
                .body(Map.of("currentPassword", AdminTestSupport.RAW_PASSWORD, "newPassword", AdminTestSupport.RAW_PASSWORD))
        .when()
                .put("/users/me/account/password")
        .then()
                .statusCode(400)
                .body("message", containsString("diferente da atual"));
    }
}
