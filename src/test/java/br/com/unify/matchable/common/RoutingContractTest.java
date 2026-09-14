package br.com.unify.matchable.common;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Prova que as rotas criticas do frontend resolvem para um recurso JAX-RS
 * (401 sem token) em vez de cair em 404 por causa de @Path sombreado ou
 * digitado errado. Ver br.com.unify.matchable.user.resources.UserResource,
 * removido por ser inalcancavel: @Path("/users") + @Path("/me") era
 * sombreado por UserProfileResource @Path("/users/me").
 *
 * Semana 03: SocialResource volta a usar @Path("/users"); as rotas pessoais
 * sao /users/feed|posts|following|followers (nunca /users/me/...). Este teste
 * e a unica cobertura real — /q/openapi lista rotas anotadas mesmo quando o
 * roteador devolve 404 em runtime.
 */
@QuarkusTest
class RoutingContractTest {

    private static final String SAMPLE_UUID = "0191a1e0-6f2e-7c31-9e3d-8b6a6c9b6e21";

    @ParameterizedTest
    @ValueSource(strings = {
            "/users/me/profile",
            "/users/me/matches/mutual",
            "/users/me/accessibility-settings",
            "/chats",
            "/communities",
            // semana 03 — social + moderation
            "/users/feed",
            "/users/following",
            "/users/followers",
            "/users/reports/reasons",
            "/users/" + SAMPLE_UUID + "/follow-stats",
            "/users/" + SAMPLE_UUID + "/posts",
            "/users/posts/" + SAMPLE_UUID + "/media"
    })
    void protectedGetRouteRespondsUnauthorizedNotNotFound(String path) {
        given()
                .when().get(path)
                .then()
                .statusCode(equalTo(401));
    }

    /**
     * A negociacao de @Consumes acontece no roteamento, antes do @RolesAllowed:
     * POST sem o Content-Type esperado devolve 415 mesmo sem token. Por isso
     * cada rota e chamada com o tipo que ela consome — 415 aqui seria falso
     * negativo, 404 continua sendo a falha que este teste caca.
     */
    @Test
    void protectedPostRoutesRespondUnauthorizedNotNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/users/reports")
                .then()
                .statusCode(equalTo(401));

        given()
                .multiPart("body", "texto")
                .when().post("/users/posts")
                .then()
                .statusCode(equalTo(401));

        given()
                .when().post("/users/" + SAMPLE_UUID + "/follow")
                .then()
                .statusCode(equalTo(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/users/" + SAMPLE_UUID + "/follow",
            "/users/posts/" + SAMPLE_UUID
    })
    void protectedDeleteRouteRespondsUnauthorizedNotNotFound(String path) {
        given()
                .when().delete(path)
                .then()
                .statusCode(equalTo(401));
    }
}
