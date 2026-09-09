package br.com.unify.matchable.common;

import io.quarkus.test.junit.QuarkusTest;
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
 */
@QuarkusTest
class RoutingContractTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/users/me/profile",
            "/users/me/matches/mutual",
            "/users/me/accessibility-settings",
            "/chats",
            "/communities"
    })
    void protectedRouteRespondsUnauthorizedNotNotFound(String path) {
        given()
                .when().get(path)
                .then()
                .statusCode(equalTo(401));
    }
}
