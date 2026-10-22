package br.com.unify.matchable.admin.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.AdminTestSupport;
import br.com.unify.matchable.admin.AdminTestSupport.TestAccount;
import br.com.unify.matchable.admin.entity.AdminAuditLog;
import br.com.unify.matchable.admin.services.AdminAuditService;
import br.com.unify.matchable.user.entity.InterestType;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Semana 06 (A) — CRUD dos catálogos de seleção de perfil, de ponta a ponta.
 * Cada teste cria os próprios itens (descrição com sufixo único) e nunca
 * desativa itens semeados: o banco de teste é compartilhado pela suíte.
 */
@QuarkusTest
class AdminLookupResourceTest {

    private static final String INTERESTS = "/admin/lookups/interest-types";
    private static final String OPTIONS = "/users/me/profile/options";

    @Test
    void createdItemGetsSequenceIdAndShowsUpInProfileOptions() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount member = AdminTestSupport.member("Comum");
        String description = unique("Jogos de tabuleiro");

        int id = given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + description + "\"}")
        .when()
                .post(INTERESTS)
        .then()
                .statusCode(201)
                .body("description", equalTo(description))
                .body("active", equalTo(true))
                .body("usageCount", equalTo(0))
                .body("protectedItem", equalTo(false))
                .body("id", greaterThan(6)) // acima dos ids semeados (sequência V30)
                .extract().path("id");

        given().auth().oauth2(member.token())
                .when().get(OPTIONS)
                .then().statusCode(200)
                .body("interestTypes.id", hasItem(id));

        long audits = QuarkusTransaction.requiringNew().call(() -> AdminAuditLog.count(
                "adminUser.id = ?1 and action = ?2 and targetId = ?3",
                admin.userId(), AdminAuditService.ACTION_LOOKUP_CREATE, "interest-types:" + id));
        assertEquals(1, audits);
    }

    @Test
    void deactivatedItemLeavesOptionsButStaysValidInExistingProfiles() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount member = AdminTestSupport.member("Comum");
        String description = unique("Xadrez adaptado");
        int id = create(admin, description);
        addInterest(member, id);

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"active\":false}")
        .when()
                .put(INTERESTS + "/" + id + "/active")
        .then()
                .statusCode(200)
                .body("active", equalTo(false))
                .body("usageCount", equalTo(1));

        given().auth().oauth2(member.token())
                .when().get(OPTIONS)
                .then().statusCode(200)
                .body("interestTypes.id", not(hasItem(id)));

        given().auth().oauth2(member.token())
                .when().get("/users/me/profile")
                .then().statusCode(200)
                .body("interestTypes.description", hasItem(description));

        // Reativar devolve o item às opções.
        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"active\":true}")
                .when().put(INTERESTS + "/" + id + "/active")
                .then().statusCode(200).body("active", equalTo(true));
        given().auth().oauth2(member.token())
                .when().get(OPTIONS)
                .then().body("interestTypes.id", hasItem(id));
    }

    @Test
    void referencedItemCannotBeDeletedButOrphanCan() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount member = AdminTestSupport.member("Comum");
        int used = create(admin, unique("Item em uso"));
        int orphan = create(admin, unique("Item órfão"));
        addInterest(member, used);

        given().auth().oauth2(admin.token())
                .when().delete(INTERESTS + "/" + used)
                .then().statusCode(409)
                .body("error", equalTo("RESOURCE_CONFLICT"))
                .body("message", containsString("1 registro usa este item"))
                .body("message", containsString("Desative-o"));

        given().auth().oauth2(admin.token())
                .when().delete(INTERESTS + "/" + orphan)
                .then().statusCode(204);

        given().auth().oauth2(admin.token())
                .when().get(INTERESTS)
                .then().statusCode(200)
                .body("id", hasItem(used))
                .body("id", not(hasItem(orphan)));
    }

    @Test
    void undisclosedGenderIsProtected() {
        TestAccount admin = AdminTestSupport.admin("Gestora");

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"active\":false}")
                .when().put("/admin/lookups/genders/4/active")
                .then().statusCode(409)
                .body("message", containsString("regra fixa do algoritmo de match"));

        given().auth().oauth2(admin.token())
                .when().delete("/admin/lookups/genders/4")
                .then().statusCode(409);

        given().auth().oauth2(admin.token())
                .when().get("/admin/lookups/genders")
                .then().statusCode(200)
                .body("find { it.id == 4 }.protectedItem", equalTo(true))
                .body("find { it.id == 4 }.active", equalTo(true));
    }

    @Test
    void updateRenamesKeepingIdAndDuplicatesConflictIgnoringCase() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        String original = unique("Bocha");
        int id = create(admin, original);
        int other = create(admin, unique("Goalball"));

        String renamed = unique("Bocha paralímpica");
        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + renamed + "\"}")
                .when().put(INTERESTS + "/" + id)
                .then().statusCode(200)
                .body("id", equalTo(id))
                .body("description", equalTo(renamed));

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + renamed.toUpperCase() + "\"}")
                .when().put(INTERESTS + "/" + other)
                .then().statusCode(409);

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"   \"}")
                .when().post(INTERESTS)
                .then().statusCode(400);

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + "x".repeat(101) + "\"}")
                .when().post(INTERESTS)
                .then().statusCode(400);
    }

    @Test
    void disabilityRequiresAValidIonicIcon() {
        TestAccount admin = AdminTestSupport.admin("Gestora");

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + unique("Surdocegueira") + "\"}")
                .when().post("/admin/lookups/disabilities")
                .then().statusCode(400);

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + unique("Surdocegueira") + "\",\"ionicIcon\":\"Hand Left\"}")
                .when().post("/admin/lookups/disabilities")
                .then().statusCode(400);

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + unique("Surdocegueira") + "\",\"ionicIcon\":\"hand-left-outline\"}")
                .when().post("/admin/lookups/disabilities")
                .then().statusCode(201)
                .body("ionicIcon", equalTo("hand-left-outline"));
    }

    @Test
    void inactiveCommunityCategoryLeavesCategoryOptions() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount member = AdminTestSupport.member("Comum");
        String description = unique("Teatro acessível");

        int id = given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + description + "\",\"ionicIcon\":\"film-outline\"}")
                .when().post("/admin/lookups/community-categories")
                .then().statusCode(201)
                .extract().path("id");

        given().auth().oauth2(member.token())
                .when().get("/communities/categories")
                .then().statusCode(200).body("id", hasItem(id));

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"active\":false}")
                .when().put("/admin/lookups/community-categories/" + id + "/active")
                .then().statusCode(200);

        given().auth().oauth2(member.token())
                .when().get("/communities/categories")
                .then().statusCode(200).body("id", not(hasItem(id)));
    }

    // ------------------------------------------------------------------ apoio

    private static int create(TestAccount admin, String description) {
        return given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + description + "\"}")
                .when().post(INTERESTS)
                .then().statusCode(201)
                .extract().path("id");
    }

    private static void addInterest(TestAccount account, int interestTypeId) {
        QuarkusTransaction.requiringNew().run(() -> {
            UserProfile profile = UserProfile.findByUser(User.findById(account.userId()));
            profile.interestTypes.add(InterestType.findById(interestTypeId));
        });
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }
}
