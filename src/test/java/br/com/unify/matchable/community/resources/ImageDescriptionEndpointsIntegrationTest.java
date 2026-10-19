package br.com.unify.matchable.community.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.post.entity.PostComment;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.http.ContentType;

/**
 * Real routing for the three image-description endpoints: each must win over the broader
 * /communities and /users resources, accept a body-less POST and reuse stored text without the provider.
 * Assumes no GEMINI_API_KEY in the test environment (the "not configured" branch answers 503).
 */
@QuarkusTest
class ImageDescriptionEndpointsIntegrationTest {
    private record Fixture(UUID userId, UUID communityId, String token) {}
    private final List<Fixture> fixtures = new ArrayList<>();

    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (Fixture f : fixtures) {
                PostComment.delete("post.id in (select p.id from Post p where p.author.id = ?1)", f.userId());
                Post.delete("author.id", f.userId());
                CommunityMembership.delete("community.id", f.communityId());
                Community.deleteById(f.communityId());
            }
        });
    }

    private Fixture fixture() {
        String email = "image-description-" + UUID.randomUUID() + "@example.com";
        UUID userId = UUID.randomUUID(), profileId = UUID.randomUUID(), communityId = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User(); user.id = userId; user.name = "Autora"; user.lastName = "Imagens";
            user.email = email; user.password = BcryptUtil.bcryptHash("Senha@123");
            user.birthdate = LocalDate.now().minusYears(30); user.verified = true; user.lastUpdatedAt = Instant.now(); user.persist();
            UserProfile profile = new UserProfile(); profile.id = profileId; profile.user = user; profile.persist();
            Community community = new Community(); community.id = communityId; community.name = "Comunidade de descrições";
            community.owner = user; community.persist();
            CommunityMembership member = new CommunityMembership(); member.id = UUID.randomUUID();
            member.community = community; member.userProfile = profile; member.role = CommunityMemberRole.ADMIN;
            member.joinedAt = Instant.now(); member.persist();
        });
        String token = given().contentType(ContentType.JSON).body(new SignInRequest(email, "Senha@123"))
                .post("/auth/signin").then().statusCode(200).extract().as(TokenResponse.class).accessToken();
        Fixture fixture = new Fixture(userId, communityId, token);
        fixtures.add(fixture);
        return fixture;
    }

    private byte[] image() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private MultiPartSpecBuilder text(String name, String value) {
        return new MultiPartSpecBuilder(value).controlName(name).mimeType("text/plain").charset("UTF-8");
    }

    private String communityPost(Fixture f, String description) throws Exception {
        var request = given().auth().oauth2(f.token()).multiPart(text("body", "Post com imagem").build())
                .multiPart("image", "foto.png", image(), "image/png");
        if (description != null) request.multiPart(text("imageDescription", description).build());
        return request.post("/communities/posts?communityId=" + f.communityId()).then().statusCode(201).extract().path("id");
    }

    @Test void communityPostReusesStoredDescription() throws Exception {
        Fixture f = fixture();
        String id = communityPost(f, "Bolo de chocolate em uma mesa.");
        given().auth().oauth2(f.token()).post("/communities/posts/" + id + "/image-description").then().statusCode(200)
            .header("Cache-Control", "no-store")
            .body("description", equalTo("Bolo de chocolate em uma mesa.")).body("source", equalTo("SAVED"));
        // The broader resource still owns its own routes.
        given().auth().oauth2(f.token()).get("/communities/posts/" + id).then().statusCode(200);
    }

    @Test void missingDescriptionWithoutProviderKeyAnswers503() throws Exception {
        Fixture f = fixture();
        String id = communityPost(f, null);
        given().auth().oauth2(f.token()).post("/communities/posts/" + id + "/image-description").then().statusCode(503)
            .body("error", equalTo("IMAGE_DESCRIPTION_UNAVAILABLE"));
        given().auth().oauth2(f.token()).post("/communities/posts/" + UUID.randomUUID() + "/image-description")
            .then().statusCode(404);
    }

    @Test void personalPostDescriptionIsReusedAndReturnedByFeedShape() throws Exception {
        Fixture f = fixture();
        String id = given().auth().oauth2(f.token()).multiPart(text("body", "Meu passeio").build())
                .multiPart("image", "foto.png", image(), "image/png")
                .post("/users/posts").then().statusCode(201).extract().path("id");
        QuarkusTransaction.requiringNew().run(() -> {
            Post post = Post.findById(UUID.fromString(id));
            post.imageDescription = "Trilha de terra entre árvores altas.";
        });
        given().auth().oauth2(f.token()).post("/users/posts/" + id + "/image-description").then().statusCode(200)
            .body("description", equalTo("Trilha de terra entre árvores altas.")).body("source", equalTo("SAVED"));
        given().auth().oauth2(f.token()).get("/users/posts/" + id).then().statusCode(200)
            .body("imageDescription", equalTo("Trilha de terra entre árvores altas."));
    }

    @Test void commentImageReusesAuthorDescription() throws Exception {
        Fixture f = fixture();
        String postId = communityPost(f, null);
        String commentId = given().auth().oauth2(f.token())
                .multiPart("image", "foto.png", image(), "image/png")
                .multiPart(text("imageDescription", "Gato preto no sofá.").build())
                .post("/communities/posts/" + postId + "/comments").then().statusCode(201).extract().path("id");
        given().auth().oauth2(f.token()).post("/communities/posts/" + postId + "/comments/" + commentId + "/image-description")
            .then().statusCode(200).body("description", equalTo("Gato preto no sofá.")).body("source", equalTo("SAVED"));
    }
}
