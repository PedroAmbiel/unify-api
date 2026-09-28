package br.com.unify.matchable.user.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.entity.Gender;
import br.com.unify.matchable.user.entity.InterestType;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserCoordinates;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserPrivacySettings;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Semana 04 — privacidade e bloqueio de ponta a ponta (HTTP real + JWT real),
 * no mesmo formato do ChatResourceTest. Cada teste cria os próprios usuários,
 * então não depende de ordem nem de limpeza.
 */
@QuarkusTest
class UserPrivacyAndBlockResourceTest {

    private static final String RAW_PASSWORD = "SenhaForte@123";
    private static final String PRIVACY_PATH = "/users/me/privacy-settings";
    private static final String PUBLIC_PROFILE_PATH = "/users/me/profile/public";

    // ------------------------------------------------------------ privacidade

    @Test
    void privacySettingsReturnDefaultsWithoutCreatingARow() {
        TestUser ana = createUser("Ana", null);

        given()
                .auth().oauth2(ana.token())
        .when()
                .get(PRIVACY_PATH)
        .then()
                .statusCode(200)
                .body("discoverable", equalTo(true))
                .body("feedVisibility", equalTo("PUBLIC"))
                .body("showDistance", equalTo(true))
                .body("showAge", equalTo(true));

        assertNull(QuarkusTransaction.requiringNew().call(() -> UserPrivacySettings.findByUser(User.findById(ana.userId()))));
    }

    @Test
    void partialUpdateKeepsOtherFieldsAndInvalidVisibilityIsRejected() {
        TestUser ana = createUser("Ana", null);

        given()
                .auth().oauth2(ana.token())
                .contentType(ContentType.JSON)
                .body("{\"discoverable\":false}")
        .when()
                .put(PRIVACY_PATH)
        .then()
                .statusCode(200)
                .body("discoverable", equalTo(false))
                .body("feedVisibility", equalTo("PUBLIC"))
                .body("showDistance", equalTo(true))
                .body("showAge", equalTo(true));

        given()
                .auth().oauth2(ana.token())
                .contentType(ContentType.JSON)
                .body("{\"feedVisibility\":\"AMIGOS\"}")
        .when()
                .put(PRIVACY_PATH)
        .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_INVALID_FORMAT"));

        given()
                .auth().oauth2(ana.token())
        .when()
                .get(PRIVACY_PATH)
        .then()
                .statusCode(200)
                .body("discoverable", equalTo(false))
                .body("feedVisibility", equalTo("PUBLIC"));
    }

    // ---------------------------------------------------------- perfil público

    @Test
    void publicProfileHidesAgeAndDistanceWhenOwnerOptsOut() {
        TestUser viewer = createUser("Vera", new double[] { -23.5505, -46.6333 });
        TestUser owner = createUser("Otto", new double[] { -22.9068, -43.1729 });

        // Default: idade e distância calculadas (São Paulo → Rio ≈ 360 km).
        given()
                .auth().oauth2(viewer.token())
        .when()
                .get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then()
                .statusCode(200)
                .body("age", equalTo(30))
                .body("distanceKm", notNullValue())
                .body("presentationAudio", nullValue());

        given()
                .auth().oauth2(owner.token())
                .contentType(ContentType.JSON)
                .body("{\"showAge\":false,\"showDistance\":false}")
        .when()
                .put(PRIVACY_PATH)
        .then()
                .statusCode(200);

        given()
                .auth().oauth2(viewer.token())
        .when()
                .get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then()
                .statusCode(200)
                .body("age", nullValue())
                .body("distanceKm", nullValue());
    }

    @Test
    void distanceIsNullWhenViewerHasNoLocation() {
        TestUser viewer = createUser("Vera", null);
        TestUser owner = createUser("Otto", new double[] { -22.9068, -43.1729 });

        given()
                .auth().oauth2(viewer.token())
        .when()
                .get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then()
                .statusCode(200)
                .body("age", equalTo(30))
                .body("distanceKm", nullValue());
    }

    // ------------------------------------------- visibilidade por parte do perfil

    @Test
    void profileFieldVisibilityDependsOnFollowDirection() {
        TestUser viewer = createUser("Vera", null);
        TestUser owner = createUser("Otto", null);
        QuarkusTransaction.requiringNew().run(() -> {
            UserProfile profile = UserProfile.findById(owner.profileId());
            profile.gender = Gender.findById(2);
            profile.interestTypes.add(InterestType.findById(1));
        });

        given()
                .auth().oauth2(owner.token())
        .when()
                .get(PRIVACY_PATH)
        .then()
                .statusCode(200)
                .body("profileFieldVisibility.size()", equalTo(13))
                .body("profileFieldVisibility.GENDER", equalTo("PUBLIC"));

        given()
                .auth().oauth2(owner.token())
                .contentType(ContentType.JSON)
                .body("{\"profileFieldVisibility\":{\"GENDER\":\"HIDDEN\",\"INTEREST_TYPES\":\"FOLLOWERS_ONLY\","
                        + "\"BIO\":\"FOLLOWING_ONLY\"}}")
        .when()
                .put(PRIVACY_PATH)
        .then()
                .statusCode(200)
                .body("profileFieldVisibility.GENDER", equalTo("HIDDEN"))
                .body("profileFieldVisibility.INTEREST_TYPES", equalTo("FOLLOWERS_ONLY"))
                .body("profileFieldVisibility.BIO", equalTo("FOLLOWING_ONLY"))
                .body("profileFieldVisibility.PRONOUNS", equalTo("PUBLIC"))
                // Só mexeu nas partes: os outros campos de privacidade seguem no default.
                .body("discoverable", equalTo(true));

        // Estranho: nada das três partes.
        given().auth().oauth2(viewer.token())
        .when().get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then().statusCode(200)
                .body("gender", nullValue())
                .body("interestTypes", hasSize(0))
                .body("bio", nullValue())
                .body("hiddenFields", containsInAnyOrder("BIO", "GENDER", "INTEREST_TYPES"));

        // Vera segue Otto: vê o que é "só para seguidores".
        given().auth().oauth2(viewer.token())
        .when().post("/users/" + owner.profileId() + "/follow")
        .then().statusCode(200);
        given().auth().oauth2(viewer.token())
        .when().get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then().statusCode(200)
                .body("interestTypes", hasSize(1))
                .body("hiddenFields", containsInAnyOrder("BIO", "GENDER"));

        // Otto segue Vera de volta: vê também o que é "só para quem eu sigo"; HIDDEN continua.
        given().auth().oauth2(owner.token())
        .when().post("/users/" + viewer.profileId() + "/follow")
        .then().statusCode(200);
        given().auth().oauth2(viewer.token())
        .when().get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then().statusCode(200)
                .body("bio", equalTo("Perfil de teste de privacidade"))
                .body("gender", nullValue())
                .body("hiddenFields", containsInAnyOrder("GENDER"));

        // Voltar para PUBLIC apaga a restrição.
        given()
                .auth().oauth2(owner.token())
                .contentType(ContentType.JSON)
                .body("{\"profileFieldVisibility\":{\"GENDER\":\"PUBLIC\"}}")
        .when()
                .put(PRIVACY_PATH)
        .then()
                .statusCode(200);
        given().auth().oauth2(viewer.token())
        .when().get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then().statusCode(200)
                .body("gender.id", equalTo(2))
                .body("hiddenFields", hasSize(0));
    }

    @Test
    void invalidProfileFieldOrVisibilityIsRejected() {
        TestUser ana = createUser("Ana", null);

        given().auth().oauth2(ana.token()).contentType(ContentType.JSON)
                .body("{\"profileFieldVisibility\":{\"SIGNO\":\"HIDDEN\"}}")
        .when().put(PRIVACY_PATH)
        .then().statusCode(400);

        given().auth().oauth2(ana.token()).contentType(ContentType.JSON)
                .body("{\"profileFieldVisibility\":{\"GENDER\":\"AMIGOS\"}}")
        .when().put(PRIVACY_PATH)
        .then().statusCode(400);
    }

    @Test
    void hiddenGalleryAndAudioReturnNotFoundOnPublicEndpoints() {
        TestUser viewer = createUser("Vera", null);
        TestUser owner = createUser("Otto", null);

        given()
                .auth().oauth2(owner.token())
                .contentType(ContentType.JSON)
                .body("{\"profileFieldVisibility\":{\"GALLERY\":\"HIDDEN\",\"PRESENTATION_AUDIO\":\"HIDDEN\"}}")
        .when()
                .put(PRIVACY_PATH)
        .then()
                .statusCode(200);

        given().auth().oauth2(viewer.token())
        .when().get(PUBLIC_PROFILE_PATH + "/images?userProfileId=" + owner.profileId())
        .then().statusCode(200).body("galleryImageIds", hasSize(0));
        given().auth().oauth2(viewer.token())
        .when().get(PUBLIC_PROFILE_PATH + "/audio/" + UUID.randomUUID() + "?userProfileId=" + owner.profileId())
        .then().statusCode(404);
    }

    // ------------------------------------------------------ seguir com aprovação

    @Test
    void followingAnApprovalAccountCreatesARequestThatTheOwnerAccepts() {
        TestUser owner = createUser("Otto", null);
        TestUser viewer = createUser("Vera", null);
        TestUser stranger = createUser("Caio", null);
        requireApproval(owner, true);

        given().auth().oauth2(viewer.token())
        .when().post("/users/" + owner.profileId() + "/follow")
        .then().statusCode(200)
                .body("following", equalTo(false))
                .body("followRequested", equalTo(true))
                .body("followersCount", equalTo(0));
        // Idempotente: repetir não duplica o pedido.
        given().auth().oauth2(viewer.token())
        .when().post("/users/" + owner.profileId() + "/follow")
        .then().statusCode(200).body("followRequested", equalTo(true));

        given().auth().oauth2(viewer.token())
        .when().get("/users/" + owner.profileId() + "/follow-stats")
        .then().statusCode(200)
                .body("followedByCurrentUser", equalTo(false))
                .body("followRequestedByCurrentUser", equalTo(true))
                .body("followApprovalRequired", equalTo(true))
                .body("pendingFollowRequestsCount", nullValue());
        given().auth().oauth2(owner.token())
        .when().get("/users/" + owner.profileId() + "/follow-stats")
        .then().statusCode(200).body("pendingFollowRequestsCount", equalTo(1));

        given().auth().oauth2(viewer.token())
        .when().get("/users/follow-requests/sent")
        .then().statusCode(200).body("requests", hasSize(1))
                .body("requests[0].userProfileId", equalTo(owner.profileId().toString()));
        String requestId = given().auth().oauth2(owner.token())
        .when().get("/users/follow-requests")
        .then().statusCode(200).body("totalElements", equalTo(1))
                .body("requests[0].userProfileId", equalTo(viewer.profileId().toString()))
                .body("requests[0].name", equalTo("Vera Teste"))
                .extract().path("requests[0].id");

        // Terceiro não aceita nem apaga: o pedido "não existe" para ele. Quem pediu não aceita.
        given().auth().oauth2(stranger.token())
        .when().post("/users/follow-requests/" + requestId + "/accept")
        .then().statusCode(404);
        given().auth().oauth2(stranger.token())
        .when().delete("/users/follow-requests/" + requestId)
        .then().statusCode(404);
        given().auth().oauth2(viewer.token())
        .when().post("/users/follow-requests/" + requestId + "/accept")
        .then().statusCode(404);

        given().auth().oauth2(owner.token())
        .when().post("/users/follow-requests/" + requestId + "/accept")
        .then().statusCode(204);

        given().auth().oauth2(viewer.token())
        .when().get("/users/" + owner.profileId() + "/follow-stats")
        .then().statusCode(200)
                .body("followedByCurrentUser", equalTo(true))
                .body("followRequestedByCurrentUser", equalTo(false))
                .body("followersCount", equalTo(1));
        given().auth().oauth2(owner.token())
        .when().get("/users/follow-requests")
        .then().statusCode(200).body("requests", hasSize(0));
    }

    @Test
    void requesterCancelsAndOwnerDeclines() {
        TestUser owner = createUser("Otto", null);
        TestUser viewer = createUser("Vera", null);
        TestUser other = createUser("Rita", null);
        requireApproval(owner, true);

        // Quem pediu cancela pelo próprio "deixar de seguir".
        given().auth().oauth2(viewer.token()).when().post("/users/" + owner.profileId() + "/follow").then().statusCode(200);
        given().auth().oauth2(viewer.token())
        .when().delete("/users/" + owner.profileId() + "/follow")
        .then().statusCode(200).body("followRequested", equalTo(false));
        given().auth().oauth2(viewer.token())
        .when().get("/users/follow-requests/sent")
        .then().statusCode(200).body("requests", hasSize(0));

        // O dono recusa.
        given().auth().oauth2(other.token()).when().post("/users/" + owner.profileId() + "/follow").then().statusCode(200);
        String requestId = given().auth().oauth2(owner.token())
        .when().get("/users/follow-requests")
        .then().statusCode(200).extract().path("requests[0].id");
        given().auth().oauth2(owner.token())
        .when().delete("/users/follow-requests/" + requestId)
        .then().statusCode(204);
        given().auth().oauth2(other.token())
        .when().get("/users/" + owner.profileId() + "/follow-stats")
        .then().statusCode(200)
                .body("followedByCurrentUser", equalTo(false))
                .body("followRequestedByCurrentUser", equalTo(false));
    }

    @Test
    void turningApprovalOffAcceptsPendingAndBlockDropsRequests() {
        TestUser owner = createUser("Otto", null);
        TestUser viewer = createUser("Vera", null);
        TestUser blocked = createUser("Beto", null);
        requireApproval(owner, true);

        given().auth().oauth2(viewer.token()).when().post("/users/" + owner.profileId() + "/follow").then().statusCode(200);
        given().auth().oauth2(blocked.token()).when().post("/users/" + owner.profileId() + "/follow").then().statusCode(200);

        given().auth().oauth2(owner.token())
        .when().post("/users/" + blocked.profileId() + "/block")
        .then().statusCode(201);
        given().auth().oauth2(owner.token())
        .when().get("/users/follow-requests")
        .then().statusCode(200).body("requests", hasSize(1))
                .body("requests[0].userProfileId", equalTo(viewer.profileId().toString()));

        requireApproval(owner, false);

        given().auth().oauth2(viewer.token())
        .when().get("/users/" + owner.profileId() + "/follow-stats")
        .then().statusCode(200)
                .body("followedByCurrentUser", equalTo(true))
                .body("followApprovalRequired", equalTo(false));
        given().auth().oauth2(owner.token())
        .when().get("/users/follow-requests")
        .then().statusCode(200).body("requests", hasSize(0));

        // Conta aberta de novo: seguir é direto.
        TestUser newcomer = createUser("Nina", null);
        given().auth().oauth2(newcomer.token())
        .when().post("/users/" + owner.profileId() + "/follow")
        .then().statusCode(200).body("following", equalTo(true)).body("followRequested", equalTo(false));
    }

    @Test
    void privateAccountLocksProfileAndPostsUntilTheRequestIsAccepted() {
        TestUser owner = createUser("Otto", new double[] { -23.5505, -46.6333 });
        TestUser viewer = createUser("Vera", new double[] { -22.9068, -43.1729 });
        QuarkusTransaction.requiringNew().run(() -> {
            UserProfile profile = UserProfile.findById(owner.profileId());
            profile.gender = Gender.findById(2);
        });
        given().auth().oauth2(owner.token())
                .contentType(ContentType.MULTIPART)
                .multiPart("body", "post da conta privada")
        .when().post("/users/posts")
        .then().statusCode(201);
        requireApproval(owner, true);

        // Travado: só nome e id.
        given().auth().oauth2(viewer.token())
        .when().get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then().statusCode(200)
                .body("locked", equalTo(true))
                .body("name", equalTo("Otto"))
                .body("age", nullValue())
                .body("distanceKm", nullValue())
                .body("bio", nullValue())
                .body("gender", nullValue())
                .body("hiddenFields", hasSize(13));
        given().auth().oauth2(viewer.token())
        .when().get("/users/" + owner.profileId() + "/posts")
        .then().statusCode(200).body("visible", equalTo(false)).body("posts", hasSize(0));

        // Pedido pendente não destrava.
        given().auth().oauth2(viewer.token()).when().post("/users/" + owner.profileId() + "/follow").then().statusCode(200);
        given().auth().oauth2(viewer.token())
        .when().get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then().statusCode(200).body("locked", equalTo(true));

        String requestId = given().auth().oauth2(owner.token())
        .when().get("/users/follow-requests")
        .then().statusCode(200).extract().path("requests[0].id");
        given().auth().oauth2(owner.token())
        .when().post("/users/follow-requests/" + requestId + "/accept")
        .then().statusCode(204);

        given().auth().oauth2(viewer.token())
        .when().get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then().statusCode(200)
                .body("locked", equalTo(false))
                .body("gender.id", equalTo(2))
                .body("age", equalTo(30))
                .body("distanceKm", notNullValue())
                .body("hiddenFields", hasSize(0));
        given().auth().oauth2(viewer.token())
        .when().get("/users/" + owner.profileId() + "/posts")
        .then().statusCode(200).body("visible", equalTo(true)).body("posts", hasSize(1));
    }

    @Test
    void mutualMatchStaysLockedButSeesMatchDataThatIsNotHidden() {
        TestUser owner = createUser("Otto", null);
        TestUser match = createUser("Mia", null);
        QuarkusTransaction.requiringNew().run(() -> {
            UserProfile profile = UserProfile.findById(owner.profileId());
            profile.gender = Gender.findById(2);
            profile.interestTypes.add(InterestType.findById(1));
        });
        requireApproval(owner, true);
        given()
                .auth().oauth2(owner.token())
                .contentType(ContentType.JSON)
                .body("{\"profileFieldVisibility\":{\"GENDER\":\"HIDDEN\"}}")
        .when()
                .put(PRIVACY_PATH)
        .then()
                .statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> {
            UserPossibleMatch mutual = new UserPossibleMatch();
            mutual.id = UUIDv7Generator.generate();
            mutual.starterProfile = UserProfile.findById(owner.profileId());
            mutual.pendingProfile = UserProfile.findById(match.profileId());
            mutual.createdAt = Instant.now();
            mutual.starterAccepted = true;
            mutual.pendingAccepted = true;
            mutual.persist();
        });

        // Continua travado: sem bio/galeria/áudio; dados de match não escondidos aparecem.
        given().auth().oauth2(match.token())
        .when().get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then().statusCode(200)
                .body("locked", equalTo(true))
                .body("userId", equalTo(owner.userId().toString()))
                .body("bio", nullValue())
                .body("age", equalTo(30))
                .body("interestTypes", hasSize(1))
                .body("gender", nullValue())
                .body("hiddenFields", containsInAnyOrder("BIO", "GALLERY", "PRESENTATION_AUDIO", "GENDER"));

        // Visitante comum continua sem nada.
        TestUser stranger = createUser("Caio", null);
        given().auth().oauth2(stranger.token())
        .when().get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then().statusCode(200)
                .body("locked", equalTo(true))
                .body("interestTypes", hasSize(0))
                .body("age", nullValue())
                .body("hiddenFields", hasSize(13));
        given().auth().oauth2(match.token())
        .when().get("/users/" + owner.profileId() + "/posts")
        .then().statusCode(200).body("visible", equalTo(false));
    }

    private void requireApproval(TestUser user, boolean required) {
        given()
                .auth().oauth2(user.token())
                .contentType(ContentType.JSON)
                .body("{\"followApprovalRequired\":" + required + "}")
        .when()
                .put(PRIVACY_PATH)
        .then()
                .statusCode(200)
                .body("followApprovalRequired", equalTo(required));
    }

    // ---------------------------------------------------------------- bloqueio

    @Test
    void blockHidesProfileBothWaysAndUnblockRestoresIt() {
        TestUser ana = createUser("Ana", null);
        TestUser beto = createUser("Beto", null);

        given().auth().oauth2(ana.token())
        .when().post("/users/" + beto.profileId() + "/follow")
        .then().statusCode(200);
        given().auth().oauth2(beto.token())
        .when().post("/users/" + ana.profileId() + "/follow")
        .then().statusCode(200);

        given()
                .auth().oauth2(ana.token())
        .when()
                .post("/users/" + beto.profileId() + "/block")
        .then()
                .statusCode(201)
                .body("userProfileId", equalTo(beto.profileId().toString()))
                .body("name", equalTo("Beto Teste"))
                .body("blockedAt", notNullValue());

        // 404 genérico nos dois sentidos (não revela o bloqueio).
        assertPublicProfileStatus(ana, beto, 404);
        assertPublicProfileStatus(beto, ana, 404);
        given().auth().oauth2(beto.token())
        .when().get("/users/" + ana.profileId() + "/posts")
        .then().statusCode(404);

        // Follows dos dois sentidos foram removidos e não podem ser recriados.
        given().auth().oauth2(ana.token())
        .when().get("/users/following")
        .then().statusCode(200).body("totalElements", equalTo(0));
        given().auth().oauth2(beto.token())
        .when().post("/users/" + ana.profileId() + "/follow")
        .then().statusCode(409);

        // Lista só quem EU bloqueei.
        given().auth().oauth2(ana.token())
        .when().get("/users/me/blocks")
        .then().statusCode(200).body("$", hasSize(1)).body("[0].userProfileId", equalTo(beto.profileId().toString()));
        given().auth().oauth2(beto.token())
        .when().get("/users/me/blocks")
        .then().statusCode(200).body("$", hasSize(0));

        given().auth().oauth2(ana.token())
        .when().delete("/users/" + beto.profileId() + "/block")
        .then().statusCode(204);

        assertPublicProfileStatus(ana, beto, 200);
        assertPublicProfileStatus(beto, ana, 200);

        given().auth().oauth2(ana.token())
        .when().delete("/users/" + beto.profileId() + "/block")
        .then().statusCode(404);
    }

    @Test
    void blockTwiceConflictsAndSelfBlockIsRejected() {
        TestUser ana = createUser("Ana", null);
        TestUser beto = createUser("Beto", null);

        given().auth().oauth2(ana.token())
        .when().post("/users/" + beto.profileId() + "/block")
        .then().statusCode(201);
        given().auth().oauth2(ana.token())
        .when().post("/users/" + beto.profileId() + "/block")
        .then().statusCode(409).body("error", equalTo("RESOURCE_CONFLICT"));

        given().auth().oauth2(ana.token())
        .when().post("/users/" + ana.profileId() + "/block")
        .then().statusCode(400);

        given().auth().oauth2(ana.token())
        .when().post("/users/" + UUID.randomUUID() + "/block")
        .then().statusCode(404);
    }

    // ------------------------------------------------------------------ apoio

    private void assertPublicProfileStatus(TestUser viewer, TestUser owner, int expectedStatus) {
        int status = given()
                .auth().oauth2(viewer.token())
        .when()
                .get(PUBLIC_PROFILE_PATH + "?userProfileId=" + owner.profileId())
        .then()
                .extract().statusCode();
        assertEquals(expectedStatus, status, viewer.name() + " vendo " + owner.name());
    }

    /** {@code coordinates} = {latitude, longitude} ou nulo (sem localização ativa). */
    private TestUser createUser(String name, double[] coordinates) {
        UUID userId = UUIDv7Generator.generate();
        UUID profileId = UUIDv7Generator.generate();
        String email = "privacy-" + UUID.randomUUID() + "@example.com";

        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.id = userId;
            user.name = name;
            user.lastName = "Teste";
            user.email = email;
            user.password = BcryptUtil.bcryptHash(RAW_PASSWORD);
            user.birthdate = LocalDate.now().minusYears(30);
            user.verified = true;
            user.lastUpdatedAt = Instant.now();
            user.persist();

            UserProfile profile = new UserProfile();
            profile.id = profileId;
            profile.user = user;
            profile.bio = "Perfil de teste de privacidade";
            profile.persist();

            if (coordinates != null) {
                UserCoordinates coordinate = new UserCoordinates();
                coordinate.id = UUIDv7Generator.generate();
                coordinate.userProfile = profile;
                coordinate.latitude = BigDecimal.valueOf(coordinates[0]);
                coordinate.longitude = BigDecimal.valueOf(coordinates[1]);
                coordinate.active = true;
                coordinate.persist();
            }
        });

        String token = given()
                .contentType(ContentType.JSON)
                .body(new SignInRequest(email, RAW_PASSWORD))
        .when()
                .post("/auth/signin")
        .then()
                .statusCode(200)
                .extract().as(TokenResponse.class)
                .accessToken();

        return new TestUser(name, userId, profileId, token);
    }

    private record TestUser(String name, UUID userId, UUID profileId, String token) {
    }
}
