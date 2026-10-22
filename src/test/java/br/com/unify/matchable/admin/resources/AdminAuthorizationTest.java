package br.com.unify.matchable.admin.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.unify.matchable.admin.AdminTestSupport;
import br.com.unify.matchable.admin.AdminTestSupport.TestAccount;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Semana 06 — a área administrativa é protegida NO SERVIDOR: esconder o menu
 * no cliente nunca é controle de acesso. Usuário comum leva 403 em qualquer
 * /admin/*, sem token é 401, e o admin continua usando o app normal.
 */
@QuarkusTest
class AdminAuthorizationTest {

    private static final String SAMPLE_UUID = "0191a1e0-6f2e-7c31-9e3d-8b6a6c9b6e21";
    private static final String SCORING_WEIGHTS_BODY = "{\"expectedRevision\":0,\"weights\":{\"MATCH_DISTANCE\":4}}";

    private static final List<String> ADMIN_GET_ROUTES = List.of(
            "/admin/lookups",
            "/admin/lookups/genders",
            "/admin/reports",
            "/admin/reports/" + SAMPLE_UUID,
            "/admin/users",
            "/admin/users/" + SAMPLE_UUID,
            "/admin/scoring-weights",
            "/admin/scoring-weights/match");

    @Test
    void requestsWithoutTokenGetUnauthorized() {
        ADMIN_GET_ROUTES.forEach(route -> given().when().get(route).then().statusCode(401));

        json(given()).body("{\"description\":\"Sem token\"}").when().post("/admin/lookups/interest-types").then().statusCode(401);
        json(given()).body("{\"reason\":\"tentativa sem token\"}").when().post("/admin/users/" + SAMPLE_UUID + "/ban").then().statusCode(401);
        json(given()).body(SCORING_WEIGHTS_BODY).when().put("/admin/scoring-weights/match").then().statusCode(401);
    }

    @Test
    void regularUserGetsForbiddenOnEveryAdminRoute() {
        TestAccount member = AdminTestSupport.member("Comum");

        ADMIN_GET_ROUTES.forEach(route ->
                given().auth().oauth2(member.token()).when().get(route).then().statusCode(403));

        json(given().auth().oauth2(member.token()))
                .body("{\"description\":\"Item indevido\"}")
                .when().post("/admin/lookups/interest-types").then().statusCode(403);
        json(given().auth().oauth2(member.token()))
                .body("{\"description\":\"Renomeado\"}")
                .when().put("/admin/lookups/genders/1").then().statusCode(403);
        json(given().auth().oauth2(member.token()))
                .body("{\"active\":false}")
                .when().put("/admin/lookups/genders/1/active").then().statusCode(403);
        given().auth().oauth2(member.token())
                .when().delete("/admin/lookups/genders/1").then().statusCode(403);
        json(given().auth().oauth2(member.token()))
                .body("{\"reason\":\"tentativa indevida de banimento\"}")
                .when().post("/admin/users/" + member.userId() + "/ban").then().statusCode(403);
        given().auth().oauth2(member.token())
                .when().post("/admin/users/" + member.userId() + "/unban").then().statusCode(403);
        json(given().auth().oauth2(member.token()))
                .body("{\"status\":\"DISMISSED\",\"adminNote\":\"tentativa indevida\"}")
                .when().put("/admin/reports/" + SAMPLE_UUID + "/status").then().statusCode(403);
        json(given().auth().oauth2(member.token()))
                .body(SCORING_WEIGHTS_BODY)
                .when().put("/admin/scoring-weights/match").then().statusCode(403);

        // O 403 de autorização não derruba a sessão do usuário comum.
        given().auth().oauth2(member.token())
                .when().get("/users/me")
                .then().statusCode(200)
                .body("role", equalTo("USER"))
                .body("banned", equalTo(false));
    }

    @Test
    void adminTokenCarriesBothGroupsAndAdminKeepsUsingTheApp() throws Exception {
        TestAccount admin = AdminTestSupport.admin("Gestora");

        Map<String, Object> claims = decodePayload(admin.token());
        assertEquals("ADMIN", claims.get("role"));
        assertTrue(((List<?>) claims.get("groups")).containsAll(List.of("user", "admin")));

        ADMIN_GET_ROUTES.stream()
                .filter(route -> !route.endsWith(SAMPLE_UUID))
                .forEach(route -> given().auth().oauth2(admin.token()).when().get(route).then().statusCode(200));

        given().auth().oauth2(admin.token())
                .when().get("/users/me")
                .then().statusCode(200)
                .body("id", equalTo(admin.userId().toString()))
                .body("role", equalTo("ADMIN"));
        given().auth().oauth2(admin.token()).when().get("/users/me/profile").then().statusCode(200);
        given().auth().oauth2(admin.token()).when().get("/communities").then().statusCode(200);
    }

    @Test
    void regularUserTokenHasOnlyUserGroup() throws Exception {
        TestAccount member = AdminTestSupport.member("Comum");

        Map<String, Object> claims = decodePayload(member.token());
        assertEquals("USER", claims.get("role"));
        assertEquals(List.of("user"), claims.get("groups"));
    }

    @Test
    void lookupCatalogListsTheTwelveCatalogs() {
        TestAccount admin = AdminTestSupport.admin("Gestora");

        given().auth().oauth2(admin.token())
                .when().get("/admin/lookups")
                .then().statusCode(200)
                .body("$", hasSize(12))
                .body("code", containsInAnyOrder(
                        "genders", "pronouns", "disabilities", "accessibility-needs", "autonomy-levels",
                        "communication-forms", "lifestyle-types", "love-languages", "energy-levels",
                        "interest-types", "connection-types", "community-categories"));

        given().auth().oauth2(admin.token())
                .when().get("/admin/lookups/" + UUID.randomUUID())
                .then().statusCode(404)
                .body("error", equalTo("RESOURCE_NOT_FOUND"));
    }

    private static RequestSpecification json(RequestSpecification specification) {
        return specification.contentType(ContentType.JSON);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decodePayload(String token) throws Exception {
        String payload = token.split("\\.")[1];
        byte[] json = Base64.getUrlDecoder().decode(payload);
        return new ObjectMapper().readValue(new String(json, StandardCharsets.UTF_8), Map.class);
    }
}
