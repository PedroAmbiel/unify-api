package br.com.unify.matchable.notification.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.notification.entity.UserPushToken;
import br.com.unify.matchable.notification.services.NotificationService;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * Ponta a ponta pelo HTTP real (filtro JWT + {@code @RolesAllowed} +
 * {@code GlobalExceptionMapper}), com usuários verificados persistidos e
 * login de verdade em {@code /auth/signin}.
 */
@QuarkusTest
class NotificationResourceTest {

    private static final String RAW_PASSWORD = "Senha@123";

    @Inject
    NotificationService notificationService;

    @Test
    void endpointsRequireAuthentication() {
        given().when().get("/notifications").then().statusCode(401);
        given().when().get("/notifications/unread-count").then().statusCode(401);
        given().when().put("/notifications/read-all").then().statusCode(401);
        given().when().put("/notifications/" + UUID.randomUUID() + "/read").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{}").when().post("/notifications/push-tokens").then().statusCode(401);
        given().when().delete("/notifications/push-tokens/" + UUID.randomUUID()).then().statusCode(401);
    }

    @Test
    void inboxListsCountsAndMarksAsRead() {
        Account ana = account("Ana");
        Account beto = account("Beto");
        UUID followId = QuarkusTransaction.requiringNew().call(() -> {
            notificationService.emitPostLiked(User.findById(ana.userId()), User.findById(beto.userId()), UUID.randomUUID(), null);
            return notificationService.emitNewFollower(
                    User.findById(ana.userId()), User.findById(beto.userId()), beto.profileId()).id;
        });

        given().auth().oauth2(ana.token())
                .when().get("/notifications/unread-count")
                .then().statusCode(200).body("unreadCount", equalTo(2));

        given().auth().oauth2(ana.token())
                .when().get("/notifications?page=0&size=20")
                .then()
                .statusCode(200)
                .body("content", hasSize(2))
                .body("totalElements", equalTo(2))
                .body("hasNext", equalTo(false))
                // Mais recente primeiro.
                .body("content[0].id", equalTo(followId.toString()))
                .body("content[0].type", equalTo("NEW_FOLLOWER"))
                .body("content[0].referenceId", equalTo(beto.profileId().toString()))
                .body("content[0].read", equalTo(false))
                .body("content[0].actor.name", equalTo("Beto Rest"))
                .body("content[0].actor.userProfileId", equalTo(beto.profileId().toString()));

        given().auth().oauth2(ana.token())
                .when().put("/notifications/" + followId + "/read")
                .then().statusCode(200).body("read", equalTo(true));

        given().auth().oauth2(ana.token())
                .when().get("/notifications/unread-count")
                .then().statusCode(200).body("unreadCount", equalTo(1));

        given().auth().oauth2(ana.token())
                .when().put("/notifications/read-all")
                .then().statusCode(200).body("updatedCount", equalTo(1));

        given().auth().oauth2(ana.token())
                .when().get("/notifications/unread-count")
                .then().statusCode(200).body("unreadCount", equalTo(0));
    }

    @Test
    void someoneElsesNotificationIsNotFound() {
        Account ana = account("Ana");
        Account beto = account("Beto");
        UUID forAna = QuarkusTransaction.requiringNew().call(() -> notificationService.emitNewFollower(
                User.findById(ana.userId()), User.findById(beto.userId()), beto.profileId()).id);

        given().auth().oauth2(beto.token())
                .when().put("/notifications/" + forAna + "/read")
                .then().statusCode(404).body("error", equalTo("RESOURCE_NOT_FOUND"));

        // Beto não enxerga nem conta o aviso da Ana.
        given().auth().oauth2(beto.token())
                .when().get("/notifications/unread-count")
                .then().statusCode(200).body("unreadCount", equalTo(0));
    }

    @Test
    void invalidPaginationIsRejected() {
        Account ana = account("Ana");

        given().auth().oauth2(ana.token())
                .when().get("/notifications?size=500")
                .then().statusCode(400);
    }

    @Test
    void pushTokenRegisterIsIdempotentAndRemovalUsesTheReturnedId() {
        Account ana = account("Ana");
        String token = "fcm-" + UUID.randomUUID();

        String id = given().auth().oauth2(ana.token())
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + token + "\",\"platform\":\"ANDROID\"}")
                .when().post("/notifications/push-tokens")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("platform", equalTo("ANDROID"))
                .extract().path("id");

        String again = given().auth().oauth2(ana.token())
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + token + "\",\"platform\":\"ANDROID\"}")
                .when().post("/notifications/push-tokens")
                .then().statusCode(200)
                .extract().path("id");

        assertEquals(id, again);
        assertEquals(1L, (long) QuarkusTransaction.requiringNew().call(() -> UserPushToken.count("token", token)));

        given().auth().oauth2(ana.token())
                .when().delete("/notifications/push-tokens/" + id)
                .then().statusCode(204);

        assertEquals(0L, (long) QuarkusTransaction.requiringNew().call(() -> UserPushToken.count("token", token)));

        given().auth().oauth2(ana.token())
                .when().delete("/notifications/push-tokens/" + id)
                .then().statusCode(404);
    }

    @Test
    void pushTokenWithoutTokenOrWithUnknownPlatformIsBadRequest() {
        Account ana = account("Ana");

        given().auth().oauth2(ana.token())
                .contentType(ContentType.JSON)
                .body("{\"token\":\"   \",\"platform\":\"ANDROID\"}")
                .when().post("/notifications/push-tokens")
                .then().statusCode(400);

        given().auth().oauth2(ana.token())
                .contentType(ContentType.JSON)
                .body("{\"token\":\"fcm-x\",\"platform\":\"SYMBIAN\"}")
                .when().post("/notifications/push-tokens")
                .then().statusCode(400);
    }

    // ------------------------------------------------------------ apoio

    private record Account(UUID userId, UUID profileId, String token) {
    }

    private Account account(String name) {
        String email = "notification-rest-" + UUID.randomUUID() + "@example.com";
        UUID[] ids = QuarkusTransaction.requiringNew().call(() -> {
            User user = new User();
            user.id = UUIDv7Generator.generate();
            user.name = name;
            user.lastName = "Rest";
            user.email = email;
            user.password = BcryptUtil.bcryptHash(RAW_PASSWORD);
            user.birthdate = LocalDate.now().minusYears(28);
            user.verified = true;
            user.lastUpdatedAt = Instant.now();
            user.persist();

            UserProfile profile = new UserProfile();
            profile.id = UUIDv7Generator.generate();
            profile.user = user;
            profile.bio = "bio";
            profile.persist();
            return new UUID[] { user.id, profile.id };
        });

        String token = given()
                .contentType(ContentType.JSON)
                .body(new SignInRequest(email, RAW_PASSWORD))
                .when().post("/auth/signin")
                .then().statusCode(200)
                .extract().as(TokenResponse.class)
                .accessToken();

        return new Account(ids[0], ids[1], token);
    }
}
