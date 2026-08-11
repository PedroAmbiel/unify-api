package br.com.unify.matchable.user.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Diferente dos demais testes de resource deste pacote (unitários, com
 * serviço stub e sem HTTP real), este é um {@code @QuarkusTest} de ponta a
 * ponta: a checagem de "401 sem token" depende do filtro JWT/@RolesAllowed
 * real do Quarkus, que não é exercitado ao chamar o método do resource
 * diretamente. Usuário é persistido já verificado (via Panache) e
 * autenticado via POST /auth/signin para obter um token de acesso real.
 */
@QuarkusTest
class UserAccessibilitySettingsResourceTest {

    private static final String RAW_PASSWORD = "Senha@123";
    private static final String PATH = "/users/me/accessibility-settings";

    @Test
    void getWithoutTokenReturnsUnauthorized() {
        given()
                .contentType(ContentType.JSON)
        .when()
                .get(PATH)
        .then()
                .statusCode(401);
    }

    @Test
    void getWithTokenAndNoStoredPreferencesReturnsDefaults() {
        String token = signUpAndSignIn();

        given()
                .auth().oauth2(token)
        .when()
                .get(PATH)
        .then()
                .statusCode(200)
                .body("fontScale", equalTo("MEDIUM"))
                .body("fontScaleMultiplier", equalTo(1.0f))
                .body("highContrast", equalTo(false))
                .body("screenReaderOptimized", equalTo(false))
                .body("reduceMotion", equalTo(false));
    }

    @Test
    void putValidPayloadPersistsSettingsAndSubsequentGetReflectsIt() {
        String token = signUpAndSignIn();

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "fontScale": "LARGE",
                          "highContrast": true,
                          "screenReaderOptimized": true,
                          "reduceMotion": false
                        }
                        """)
        .when()
                .put(PATH)
        .then()
                .statusCode(200)
                .body("fontScale", equalTo("LARGE"))
                .body("fontScaleMultiplier", equalTo(1.15f))
                .body("highContrast", equalTo(true))
                .body("screenReaderOptimized", equalTo(true))
                .body("reduceMotion", equalTo(false));

        given()
                .auth().oauth2(token)
        .when()
                .get(PATH)
        .then()
                .statusCode(200)
                .body("fontScale", equalTo("LARGE"))
                .body("highContrast", equalTo(true))
                .body("screenReaderOptimized", equalTo(true));
    }

    @Test
    void putEmptyBodyAppliesDefaults() {
        String token = signUpAndSignIn();

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{}")
        .when()
                .put(PATH)
        .then()
                .statusCode(200)
                .body("fontScale", equalTo("MEDIUM"))
                .body("highContrast", equalTo(false))
                .body("screenReaderOptimized", equalTo(false))
                .body("reduceMotion", equalTo(false));
    }

    private String signUpAndSignIn() {
        String email = "a11y-" + UUID.randomUUID() + "@example.com";
        persistVerifiedUser(email);

        return given()
                .contentType(ContentType.JSON)
                .body(new SignInRequest(email, RAW_PASSWORD))
        .when()
                .post("/auth/signin")
        .then()
                .statusCode(200)
                .extract().as(TokenResponse.class)
                .accessToken();
    }

    private void persistVerifiedUser(String email) {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.id = UUID.randomUUID();
            user.name = "Teste";
            user.lastName = "Acessibilidade";
            user.email = email;
            user.password = BcryptUtil.bcryptHash(RAW_PASSWORD);
            user.birthdate = LocalDate.now().minusYears(25);
            user.verified = true;
            user.lastUpdatedAt = Instant.now();
            user.persist();
        });
    }
}
