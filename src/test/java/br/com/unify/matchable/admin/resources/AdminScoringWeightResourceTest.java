package br.com.unify.matchable.admin.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.AdminTestSupport;
import br.com.unify.matchable.admin.AdminTestSupport.TestAccount;
import br.com.unify.matchable.admin.entity.AdminAuditLog;
import br.com.unify.matchable.admin.services.AdminAuditService;
import br.com.unify.matchable.scoring.ScoringWeightTestSupport;
import br.com.unify.matchable.scoring.entity.ScoringWeight;
import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.enums.ScoringGroup;
import br.com.unify.matchable.scoring.services.ScoringWeightRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Pesos de pontuação pelo backoffice, de ponta a ponta: o PUT grava no banco,
 * a memória da API (ScoringWeightRegistry) troca na hora e um novo boot
 * (reload) enxerga o mesmo valor. Pesos são globais: cada teste volta ao
 * padrão no fim.
 */
@QuarkusTest
class AdminScoringWeightResourceTest {

    private static final String WEIGHTS = "/admin/scoring-weights";
    private static final String MATCH_GROUP = "find { it.code == 'match' }";

    @Inject
    ScoringWeightRegistry registry;

    @AfterEach
    void restoreDefaults() {
        ScoringWeightTestSupport.restoreDefaults(registry);
    }

    @Test
    void listsTheThreeGroupsWithDefaultsLimitsAndCatalogs() {
        TestAccount admin = AdminTestSupport.admin("Gestora");

        JsonPath json = given().auth().oauth2(admin.token())
                .when().get(WEIGHTS)
                .then().statusCode(200)
                .body("code", contains("match", "feed", "community-discovery"))
                .extract().jsonPath();

        assertEquals(100d, json.getDouble(MATCH_GROUP + ".requiredPercentTotal"));
        assertEquals(13, json.getList(MATCH_GROUP + ".dimensions").size());
        assertNull(json.get(MATCH_GROUP + ".updatedAt"));

        String communication = MATCH_GROUP + ".dimensions.find { it.code == 'MATCH_COMMUNICATION' }";
        assertEquals(22d, json.getDouble(communication + ".weight"));
        assertEquals(22d, json.getDouble(communication + ".defaultWeight"));
        assertEquals(100d, json.getDouble(communication + ".maxWeight"));
        assertEquals("PERCENT", json.getString(communication + ".unit"));
        assertEquals("Compatibilidade", json.getString(communication + ".section"));
        assertEquals("communication-forms", json.getString(communication + ".lookupCode"));
        assertEquals("Formas de comunicação", json.getString(communication + ".lookupLabel"));

        String genderPenalty = MATCH_GROUP + ".dimensions.find { it.code == 'MATCH_PENALTY_GENDER_NOT_RECIPROCAL' }";
        assertEquals("POINTS", json.getString(genderPenalty + ".unit"));
        assertEquals("genders", json.getString(genderPenalty + ".lookupCode"));

        assertNull(json.get("find { it.code == 'feed' }.requiredPercentTotal"));
        String halfLife = "find { it.code == 'feed' }.dimensions.find { it.code == 'FEED_RECENCY_HALF_LIFE_HOURS' }";
        assertEquals("HOURS", json.getString(halfLife + ".unit"));
        assertEquals(1d, json.getDouble(halfLife + ".minWeight"));

        given().auth().oauth2(admin.token())
                .when().get(WEIGHTS + "/community-discovery")
                .then().statusCode(200)
                .body("code", equalTo("community-discovery"))
                .body("dimensions.code", contains(
                        "COMMUNITY_DISCOVERY_MEMBER", "COMMUNITY_DISCOVERY_POST", "COMMUNITY_DISCOVERY_LIKE"));

        given().auth().oauth2(admin.token())
                .when().get(WEIGHTS + "/ranking")
                .then().statusCode(404)
                .body("error", equalTo("RESOURCE_NOT_FOUND"));
    }

    @Test
    void savingAppliesInMemoryRightAwayPersistsAndAudits() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        long revision = revision(admin, "match");

        JsonPath saved = put(admin, "match", revision, Map.of("MATCH_COMMUNICATION", 25, "MATCH_INTERESTS", 15))
                .then().statusCode(200)
                .body("updatedByName", equalTo("Gestora Teste"))
                .extract().jsonPath();

        assertEquals(revision + 1, saved.getLong("revision"));
        assertNotNull(saved.getString("updatedAt"));
        assertEquals(25d, saved.getDouble("dimensions.find { it.code == 'MATCH_COMMUNICATION' }.weight"));

        // Memória: vale para a próxima busca, sem reiniciar a API.
        assertEquals(25d, registry.current().get(ScoringDimension.MATCH_COMMUNICATION));
        assertEquals(15d, registry.current().get(ScoringDimension.MATCH_INTERESTS));
        assertEquals(100d, registry.current().percentTotal(ScoringGroup.MATCH), 0.0001d);
        assertEquals(revision + 1, registry.current().revision(ScoringGroup.MATCH));

        // Banco: um boot novo (reload) enxerga os mesmos valores.
        BigDecimal stored = QuarkusTransaction.requiringNew().call(
                () -> ScoringWeight.<ScoringWeight>findById("MATCH_COMMUNICATION").weight);
        assertEquals(0, new BigDecimal("25").compareTo(stored));
        registry.reload();
        assertEquals(25d, registry.current().get(ScoringDimension.MATCH_COMMUNICATION));

        long audits = QuarkusTransaction.requiringNew().call(() -> AdminAuditLog.count(
                "adminUser.id = ?1 and action = ?2 and targetId = ?3 and details like ?4",
                admin.userId(), AdminAuditService.ACTION_SCORING_WEIGHTS_UPDATE, "MATCH",
                "%Formas de comunicação: 22 -> 25%"));
        assertEquals(1, audits);
    }

    @Test
    void percentSlicesMustSumOneHundredAndNothingChangesWhenRejected() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        long revision = revision(admin, "match");

        put(admin, "match", revision, Map.of("MATCH_COMMUNICATION", 30))
                .then().statusCode(400)
                .body("message", containsString("precisam somar 100%"))
                .body("message", containsString("108%"));

        assertEquals(22d, registry.current().get(ScoringDimension.MATCH_COMMUNICATION));
        assertEquals(revision, revision(admin, "match"));

        // Penalidade não entra na soma das fatias.
        put(admin, "match", revision, Map.of("MATCH_PENALTY_GENDER_NOT_RECIPROCAL", 12.5))
                .then().statusCode(200)
                .body("dimensions.find { it.code == 'MATCH_PENALTY_GENDER_NOT_RECIPROCAL' }.weight", equalTo(12.5f));
        assertEquals(12.5d, registry.current().get(ScoringDimension.MATCH_PENALTY_GENDER_NOT_RECIPROCAL));
    }

    @Test
    void staleRevisionIsRejectedSoNobodyOverwritesAnotherAdmin() {
        TestAccount first = AdminTestSupport.admin("Primeira");
        TestAccount second = AdminTestSupport.admin("Segunda");
        long revision = revision(first, "feed");

        put(first, "feed", revision, Map.of("FEED_LIKE", 3)).then().statusCode(200);

        put(second, "feed", revision, Map.of("FEED_COMMENT", 8))
                .then().statusCode(409)
                .body("error", equalTo("RESOURCE_CONFLICT"))
                .body("message", containsString("outra pessoa"));

        assertEquals(3d, registry.current().get(ScoringDimension.FEED_LIKE));
        assertEquals(4d, registry.current().get(ScoringDimension.FEED_COMMENT));
    }

    @Test
    void sameValuesKeepTheRevisionAndWriteNoAudit() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        long revision = revision(admin, "community-discovery");

        put(admin, "community-discovery", revision, Map.of("COMMUNITY_DISCOVERY_MEMBER", 3))
                .then().statusCode(200)
                .body("revision", equalTo((int) revision));

        long audits = QuarkusTransaction.requiringNew().call(() -> AdminAuditLog.count(
                "adminUser.id = ?1 and action = ?2", admin.userId(), AdminAuditService.ACTION_SCORING_WEIGHTS_UPDATE));
        assertEquals(0, audits);
    }

    @Test
    void invalidRequestsAreRejectedWithTheReason() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        long match = revision(admin, "match");
        long feed = revision(admin, "feed");

        put(admin, "match", match, Map.of("MATCH_NOPE", 1))
                .then().statusCode(400).body("message", containsString("não existe"));
        put(admin, "match", match, Map.of("FEED_LIKE", 1))
                .then().statusCode(400).body("message", containsString("não pertence ao grupo Encontros"));
        put(admin, "match", match, Map.of("MATCH_PENALTY_AGE_NOT_RECIPROCAL", 101))
                .then().statusCode(400).body("message", containsString("entre 0 e 100"));
        put(admin, "match", match, Map.of("MATCH_PENALTY_AGE_NOT_RECIPROCAL", -1))
                .then().statusCode(400).body("message", containsString("entre 0 e 100"));
        put(admin, "match", match, Map.of("MATCH_PENALTY_AGE_NOT_RECIPROCAL", 1.255))
                .then().statusCode(400).body("message", containsString("2 casas decimais"));
        put(admin, "feed", feed, Map.of("FEED_RECENCY_HALF_LIFE_HOURS", 0))
                .then().statusCode(400).body("message", containsString("entre 1 e 720 horas"));
        put(admin, "feed", feed, Map.of())
                .then().statusCode(400).body("message", containsString("ao menos um peso"));

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body(Map.of("weights", Map.of("FEED_LIKE", 3)))
                .when().put(WEIGHTS + "/feed")
                .then().statusCode(400)
                .body("message", containsString("expectedRevision"));

        assertEquals(match, revision(admin, "match"));
        assertEquals(feed, revision(admin, "feed"));
    }

    private static Response put(
            TestAccount admin, String group, long expectedRevision, Map<String, ? extends Number> weights) {
        return given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body(Map.of("expectedRevision", expectedRevision, "weights", weights))
                .when().put(WEIGHTS + "/" + group);
    }

    private static long revision(TestAccount admin, String group) {
        return given().auth().oauth2(admin.token())
                .when().get(WEIGHTS + "/" + group)
                .then().statusCode(200)
                .extract().jsonPath().getLong("revision");
    }
}
