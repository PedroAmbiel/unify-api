package br.com.unify.matchable.admin;

import static io.restassured.RestAssured.given;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.community.enums.CommunityPrivacy;
import br.com.unify.matchable.moderation.entity.UserReport;
import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.enums.UserRole;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.http.ContentType;

/**
 * Contas e denúncias reais (commitadas) para os testes HTTP do backoffice:
 * cada teste cria as próprias, então não depende de ordem nem de limpeza.
 */
public final class AdminTestSupport {

    public static final String RAW_PASSWORD = "SenhaForte@123";

    /** Mesmo hash para todas as contas de teste: bcrypt é caro de propósito. */
    private static final String PASSWORD_HASH = BcryptUtil.bcryptHash(RAW_PASSWORD);

    private AdminTestSupport() {
    }

    public record TestAccount(UUID userId, UUID profileId, String email, TokenResponse tokens) {

        public String token() {
            return tokens.accessToken();
        }
    }

    public static TestAccount admin(String name) {
        return account(name, UserRole.ADMIN);
    }

    public static TestAccount member(String name) {
        return account(name, UserRole.USER);
    }

    public static TestAccount account(String name, UserRole role) {
        UUID userId = UUIDv7Generator.generate();
        UUID profileId = UUIDv7Generator.generate();
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";

        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.id = userId;
            user.name = name;
            user.lastName = "Teste";
            user.email = email;
            user.password = PASSWORD_HASH;
            user.birthdate = LocalDate.now().minusYears(30);
            user.verified = true;
            user.lastUpdatedAt = Instant.now();
            user.role = role;
            user.persist();

            UserProfile profile = new UserProfile();
            profile.id = profileId;
            profile.user = user;
            profile.bio = "Perfil de teste do backoffice";
            profile.persist();
        });

        return new TestAccount(userId, profileId, email, signIn(email));
    }

    public static TokenResponse signIn(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(new SignInRequest(email, RAW_PASSWORD))
        .when()
                .post("/auth/signin")
        .then()
                .statusCode(200)
                .extract().as(TokenResponse.class);
    }

    /**
     * Posts e comunidades criados pelos testes do backoffice ficam commitados e
     * apareceriam no feed/descoberta de outras classes de teste (que contam o
     * que é visível). Chamar no {@code @AfterEach} de quem cria conteúdo.
     */
    public static void retireContent() {
        QuarkusTransaction.requiringNew().run(() -> {
            var entityManager = Post.getEntityManager();
            entityManager.createNativeQuery("update posts set active = false where active = true "
                    + "and fk_user in (select id from users where email like 'admin-test-%')").executeUpdate();
            entityManager.createNativeQuery("update communities set active = false where active = true "
                    + "and fk_owner_user in (select id from users where email like 'admin-test-%')").executeUpdate();
        });
    }

    /** Publicação pessoal ativa (semana 07: moderação de publicações). */
    public static UUID personalPost(TestAccount author, String body) {
        UUID postId = UUIDv7Generator.generate();
        QuarkusTransaction.requiringNew().run(() -> {
            Post post = new Post();
            post.id = postId;
            post.origin = PostOrigin.PERSONAL;
            post.author = User.findById(author.userId());
            post.body = body;
            post.createdAt = Instant.now();
            post.persist();
        });
        return postId;
    }

    /** Comunidade pública nova, com o autor como membro, e um post dele nela. */
    public static UUID communityPost(TestAccount author, String body) {
        UUID postId = UUIDv7Generator.generate();
        QuarkusTransaction.requiringNew().run(() -> {
            User user = User.findById(author.userId());

            Community community = new Community();
            community.id = UUIDv7Generator.generate();
            community.name = "Comunidade " + UUID.randomUUID();
            community.owner = user;
            community.active = true;
            community.privacy = CommunityPrivacy.PUBLIC;
            community.persist();

            CommunityMembership membership = new CommunityMembership();
            membership.id = UUIDv7Generator.generate();
            membership.community = community;
            membership.userProfile = UserProfile.findById(author.profileId());
            membership.role = CommunityMemberRole.ADMIN;
            membership.joinedAt = Instant.now();
            membership.persist();

            Post post = new Post();
            post.id = postId;
            post.origin = PostOrigin.COMMUNITY;
            post.community = community;
            post.author = user;
            post.body = body;
            post.createdAt = Instant.now();
            post.persist();
        });
        return postId;
    }

    /** Denúncia ABERTA de publicação (o denunciado é o autor do post). */
    public static UUID reportPost(TestAccount reporter, UUID postId, ReportReason reason) {
        UUID reportId = UUIDv7Generator.generate();
        QuarkusTransaction.requiringNew().run(() -> {
            Post post = Post.findById(postId);
            UserReport report = new UserReport();
            report.id = reportId;
            report.reporter = User.findById(reporter.userId());
            report.reportedUser = post.author;
            report.reportedPost = post;
            report.reason = reason;
            report.description = "Denúncia de publicação criada pelo teste";
            report.status = UserReportStatus.OPEN;
            report.createdAt = Instant.now();
            report.persist();
        });
        return reportId;
    }

    public static UUID report(TestAccount reporter, TestAccount reported, ReportReason reason) {
        UUID reportId = UUIDv7Generator.generate();
        QuarkusTransaction.requiringNew().run(() -> {
            UserReport report = new UserReport();
            report.id = reportId;
            report.reporter = User.findById(reporter.userId());
            report.reportedUser = User.findById(reported.userId());
            report.reason = reason;
            report.description = "Denúncia criada pelo teste";
            report.status = UserReportStatus.OPEN;
            report.createdAt = Instant.now();
            report.persist();
        });
        return reportId;
    }
}
