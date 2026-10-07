package br.com.unify.matchable.community.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import io.restassured.builder.MultiPartSpecBuilder;
import java.util.ArrayList;
import java.util.List;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.restassured.http.ContentType;
import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.community.enums.CommunityPrivacy;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;

/** Real HTTP, JWT, PostgreSQL persistence and image conversion. Run against an isolated test database. */
@QuarkusTest
class CommunityPostIntegrationTest {
    private record Fixture(UUID communityId, UUID profileId, String token) {}
    private final List<UUID> communities = new ArrayList<>();
    @AfterEach void cleanupPosts() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (UUID id : communities) {
                Post.delete("community.id", id);
                CommunityMembership.delete("community.id", id);
                Community.deleteById(id);
            }
        });
    }
    private Fixture fixture() {
        String email = "community-post-" + UUID.randomUUID() + "@example.com";
        UUID userId = UUID.randomUUID(), profileId = UUID.randomUUID(), communityId = UUID.randomUUID();
        communities.add(communityId);
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User(); user.id = userId; user.name = "Autor"; user.lastName = "Comunidade";
            user.email = email; user.password = BcryptUtil.bcryptHash("Senha@123");
            user.birthdate = LocalDate.now().minusYears(28); user.verified = true; user.lastUpdatedAt = Instant.now(); user.persist();
            UserProfile profile = new UserProfile(); profile.id = profileId; profile.user = user; profile.persist();
            Community community = new Community(); community.id = communityId; community.name = "Comunidade de teste";
            community.owner = user; community.persist();
            CommunityMembership member = new CommunityMembership(); member.id = UUID.randomUUID();
            member.community = community; member.userProfile = profile; member.role = CommunityMemberRole.ADMIN;
            member.joinedAt = Instant.now(); member.persist();
        });
        String token = given().contentType(ContentType.JSON).body(new SignInRequest(email, "Senha@123"))
                .post("/auth/signin").then().statusCode(200).extract().as(TokenResponse.class).accessToken();
        return new Fixture(communityId, profileId, token);
    }
    private byte[] image() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
    private String create(Fixture f, boolean withImage, String description) throws Exception {
        var request = given().auth().oauth2(f.token()).multiPart("body", "Texto independente");
        if (withImage) request.multiPart("image", "foto.png", image(), "image/png");
        if (description != null) request.multiPart(new MultiPartSpecBuilder(description).controlName("imageDescription").mimeType("text/plain").charset("UTF-8").build());
        return request.post("/communities/posts?communityId=" + f.communityId()).then().statusCode(201)
            .body("body", equalTo("Texto independente"))
            .body("imageDescription", description == null ? nullValue() : equalTo(description))
            .extract().path("id");
    }
    @Test void createWithoutImageAndLegacyNull() throws Exception {
        Fixture f = fixture(); String id = create(f, false, null);
        given().auth().oauth2(f.token()).get("/communities/posts/" + id).then().statusCode(200)
            .body("imageDescription", nullValue()).body("mediaData", nullValue());
        given().auth().oauth2(f.token()).contentType(ContentType.JSON).body("{\"body\":\"Editado\"}")
            .put("/communities/posts/" + id).then().statusCode(200).body("imageDescription", nullValue());
    }
    @Test void createWithImageWithoutDescription() throws Exception { create(fixture(), true, null); }
    @Test void descriptionPersistsAndReturnsInAllFeeds() throws Exception {
        Fixture f = fixture(); String id = create(f, true, "Uma árvore");
        QuarkusTransaction.requiringNew().run(() -> {
            Post post = Post.findById(UUID.fromString(id));
            assertEquals("Uma árvore", post.imageDescription); assertEquals("Texto independente", post.body);
        });
        given().auth().oauth2(f.token()).get("/communities/posts/" + id).then().statusCode(200).body("imageDescription", equalTo("Uma árvore"));
        given().auth().oauth2(f.token()).get("/communities/feed?communityId=" + f.communityId()).then().statusCode(200)
            .body("posts.content.find { it.id == '" + id + "' }.imageDescription", equalTo("Uma árvore"));
        given().auth().oauth2(f.token()).get("/communities/feed/me").then().statusCode(200)
            .body("content.find { it.post.id == '" + id + "' }.post.imageDescription", equalTo("Uma árvore"));
        given().auth().oauth2(f.token()).get("/users/feed").then().statusCode(200)
            .body("posts.find { it.id == '" + id + "' }.imageDescription", equalTo("Uma árvore"));
    }
    @Test void descriptionOnlyAndBodyOnlyEditsPreserveMedia() throws Exception {
        Fixture f = fixture(); String id = create(f, true, "Original"); String path = "/communities/posts/" + id;
        given().auth().oauth2(f.token()).contentType(ContentType.JSON).body("{\"imageDescription\":\"Nova\"}")
            .put(path).then().statusCode(200).body("body", equalTo("Texto independente"))
            .body("imageDescription", equalTo("Nova")).body("mediaData", notNullValue());
        given().auth().oauth2(f.token()).contentType(ContentType.JSON).body("{\"body\":\"Outro texto\"}")
            .put(path).then().statusCode(200).body("imageDescription", equalTo("Nova")).body("mediaData", notNullValue());
        given().auth().oauth2(f.token()).contentType(ContentType.JSON).body("{\"imageDescription\":null}")
            .put(path).then().statusCode(200).body("imageDescription", nullValue());
    }
    @Test void replacingAndRemovingImageClearPreviousDescription() throws Exception {
        Fixture f = fixture(); String id = create(f, true, "Antiga"); String path = "/communities/posts/" + id;
        given().auth().oauth2(f.token()).multiPart("body", "Texto independente")
            .multiPart("image", "nova.png", image(), "image/png").put(path + "/media")
            .then().statusCode(200).body("imageDescription", nullValue()).body("mediaData", notNullValue());
        given().auth().oauth2(f.token()).multiPart("body", "Texto independente")
            .multiPart("image", "nova.png", image(), "image/png").multiPart("imageDescription", "Nova imagem")
            .put(path + "/media").then().statusCode(200).body("imageDescription", equalTo("Nova imagem"));
        given().auth().oauth2(f.token()).multiPart("body", "Texto independente").multiPart("removeImage", "true")
            .put(path + "/media").then().statusCode(200).body("imageDescription", nullValue()).body("mediaData", nullValue());
    }
    @Test void invalidDescriptionsAndForeignEditsAreRejected() throws Exception {
        Fixture f = fixture();
        given().auth().oauth2(f.token()).multiPart("body", "Texto").multiPart("imageDescription", "Órfã")
            .post("/communities/posts?communityId=" + f.communityId()).then().statusCode(400);
        String id = create(f, true, "Original");
        given().auth().oauth2(f.token()).contentType(ContentType.JSON).body("{\"imageDescription\":\"" + "a".repeat(241) + "\"}")
            .put("/communities/posts/" + id).then().statusCode(400);
        Fixture other = fixture();
        given().auth().oauth2(other.token()).contentType(ContentType.JSON).body("{\"imageDescription\":\"Outra\"}")
            .put("/communities/posts/" + id).then().statusCode(409);
        QuarkusTransaction.requiringNew().run(() -> {
            CommunityMembership member = new CommunityMembership(); member.id = UUID.randomUUID();
            member.community = Community.findById(f.communityId()); member.userProfile = UserProfile.findById(other.profileId());
            member.role = CommunityMemberRole.MODERATOR; member.joinedAt = Instant.now(); member.persist();
        });
        given().auth().oauth2(other.token()).contentType(ContentType.JSON).body("{\"imageDescription\":\"Outra\"}")
            .put("/communities/posts/" + id).then().statusCode(403);
        QuarkusTransaction.requiringNew().run(() -> { CommunityMembership.delete("community.id = ?1 and userProfile.id = ?2", f.communityId(), other.profileId()); });
        given().get("/communities/posts/" + id).then().statusCode(401);
        QuarkusTransaction.requiringNew().run(() -> { Community c = Community.findById(f.communityId()); c.privacy = CommunityPrivacy.PRIVATE; });
        given().auth().oauth2(other.token()).get("/communities/posts/" + id).then().statusCode(404);
    }
}
