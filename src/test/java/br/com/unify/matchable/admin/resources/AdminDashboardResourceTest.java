package br.com.unify.matchable.admin.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.unify.matchable.admin.AdminTestSupport;
import br.com.unify.matchable.admin.AdminTestSupport.TestAccount;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.community.enums.CommunityPrivacy;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;

/** Semana 07 — dashboard administrativo: RBAC, formato, séries diárias e cache. */
@QuarkusTest
class AdminDashboardResourceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Inject
    @CacheName("admin-dashboard-summary")
    Cache summaryCache;

    @Inject
    @CacheName("admin-dashboard-timeseries")
    Cache timeSeriesCache;

    @Inject
    @CacheName("admin-dashboard-top-communities")
    Cache topCommunitiesCache;

    @BeforeEach
    void clearCaches() {
        clearAll();
    }

    @AfterEach
    void retireContent() {
        AdminTestSupport.retireContent();
    }

    @Test
    void onlyAdminsReadTheDashboard() {
        TestAccount member = AdminTestSupport.member("Comum");
        TestAccount admin = AdminTestSupport.admin("Gestora");

        for (String path : List.of(
                "/admin/dashboard/summary",
                "/admin/dashboard/timeseries?metric=new_users",
                "/admin/dashboard/top-communities")) {
            given().when().get(path).then().statusCode(401);
            given().auth().oauth2(member.token()).when().get(path).then().statusCode(403);
            given().auth().oauth2(admin.token()).when().get(path).then().statusCode(200);
        }
    }

    @Test
    void summaryBringsEveryCategoryAndStaysCachedUntilInvalidated() {
        TestAccount admin = AdminTestSupport.admin("Gestora");

        JsonPath first = summary(admin);
        for (String category : List.of("users", "matches", "chat", "communities", "posts", "interactions",
                "reports", "follows")) {
            assertTrue(first.getMap(category) != null, "categoria ausente: " + category);
        }
        assertTrue(first.getLong("users.total") >= 1);
        assertTrue(first.getLong("users.admins") >= 1);
        assertEquals(first.getLong("reports.open") + first.getLong("reports.underReview"),
                first.getLong("reports.pending"));

        AdminTestSupport.member("Nova conta");

        // dentro dos 60 s o cache devolve o mesmo cálculo
        JsonPath cached = summary(admin);
        assertEquals(first.getLong("generatedAtEpochMillis"), cached.getLong("generatedAtEpochMillis"));
        assertEquals(first.getLong("users.total"), cached.getLong("users.total"));

        clearAll();
        JsonPath fresh = summary(admin);
        assertEquals(first.getLong("users.total") + 1, fresh.getLong("users.total"));
        assertEquals(first.getLong("users.newLast7Days") + 1, fresh.getLong("users.newLast7Days"));
    }

    @Test
    void summaryCountsPostsRemovedByModeration() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount author = AdminTestSupport.member("Autora");
        TestAccount reporter = AdminTestSupport.member("Ana");
        UUID postId = AdminTestSupport.personalPost(author, "Post que será removido");
        AdminTestSupport.reportPost(reporter, postId, br.com.unify.matchable.moderation.enums.ReportReason.SCAM);

        JsonPath before = summary(admin);

        given().auth().oauth2(admin.token())
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"Golpe com link falso de premiação.\"}")
                .when().post("/admin/posts/" + postId + "/remove")
                .then().statusCode(200);
        clearAll();

        JsonPath after = summary(admin);
        assertEquals(before.getLong("posts.personalPosts") - 1, after.getLong("posts.personalPosts"));
        assertEquals(before.getLong("posts.removedByModeration") + 1, after.getLong("posts.removedByModeration"));
        assertEquals(before.getLong("reports.pendingPostReports") - 1, after.getLong("reports.pendingPostReports"));
        assertEquals(before.getLong("reports.resolved") + 1, after.getLong("reports.resolved"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"new_users", "new_mutual_matches", "messages_sent", "posts_created", "reports_opened"})
    void everyMetricReturnsOnePointPerDayEndingToday(String metric) {
        TestAccount admin = AdminTestSupport.admin("Gestora");

        JsonPath body = given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/timeseries?metric=" + metric + "&days=7")
                .then().statusCode(200)
                .body("metric", equalTo(metric))
                .body("days", equalTo(7))
                .body("points", hasSize(7))
                .extract().jsonPath();

        LocalDate today = LocalDate.now(SAO_PAULO);
        List<String> dates = body.getList("points.date");
        for (int index = 0; index < 7; index++) {
            assertEquals(today.minusDays(6L - index).toString(), dates.get(index), "datas em ordem, sem buracos");
        }
        if ("new_users".equals(metric)) {
            assertTrue(body.getLong("points[6].value") >= 1, "a conta de admin acabou de ser criada");
        }
    }

    @Test
    void timeSeriesDefaultsToThirtyDays() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/timeseries?metric=NEW_USERS")
                .then().statusCode(200)
                .body("metric", equalTo("new_users"))
                .body("points", hasSize(30));
    }

    @Test
    void mutualMatchCountsOnTheDayItBecameMutual() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount ana = AdminTestSupport.member("Ana");
        TestAccount bia = AdminTestSupport.member("Bia");
        TestAccount carla = AdminTestSupport.member("Carla");

        long before = mutualMatchesTwoDaysAgo(admin);

        LocalDate twoDaysAgo = LocalDate.now(SAO_PAULO).minusDays(2);
        Instant matchedAt = twoDaysAgo.atTime(LocalTime.NOON).atZone(SAO_PAULO).toInstant();
        QuarkusTransaction.requiringNew().run(() -> {
            // mútuo: criado 5 dias antes, confirmado há 2 dias
            UserPossibleMatch mutual = new UserPossibleMatch();
            mutual.id = UUIDv7Generator.generate();
            mutual.starterProfile = UserProfile.findById(ana.profileId());
            mutual.pendingProfile = UserProfile.findById(bia.profileId());
            mutual.createdAt = matchedAt.minusSeconds(5 * 86_400L);
            mutual.starterAccepted = true;
            mutual.pendingAccepted = true;
            mutual.matchedAt = matchedAt;
            mutual.persist();

            // só uma curtida, sem resposta: não conta
            UserPossibleMatch pending = new UserPossibleMatch();
            pending.id = UUIDv7Generator.generate();
            pending.starterProfile = UserProfile.findById(ana.profileId());
            pending.pendingProfile = UserProfile.findById(carla.profileId());
            pending.createdAt = matchedAt;
            pending.starterAccepted = true;
            pending.persist();
        });
        clearAll();

        assertEquals(before + 1, mutualMatchesTwoDaysAgo(admin));
    }

    @Test
    void invalidParametersAreBadRequests() {
        TestAccount admin = AdminTestSupport.admin("Gestora");

        given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/timeseries?metric=nao_existe&days=30")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_INVALID_ARGUMENT"))
                .body("message", containsString("new_users"))
                .body("message", containsString("reports_opened"));
        given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/timeseries")
                .then().statusCode(400);
        given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/timeseries?metric=new_users&days=0")
                .then().statusCode(400);
        given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/timeseries?metric=new_users&days=181")
                .then().statusCode(400)
                .body("message", containsString("180"));
        given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/top-communities?limit=0")
                .then().statusCode(400);
        given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/top-communities?limit=51")
                .then().statusCode(400);
    }

    @Test
    void topCommunitiesAreOrderedLimitedAndIgnoreBannedMembers() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        UUID biggest = communityWithMembers("Maior", 14, 1);
        UUID second = communityWithMembers("Segunda", 12, 0);
        clearAll();

        JsonPath body = given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/top-communities?limit=2")
                .then().statusCode(200)
                .body("byMembers", hasSize(2))
                .body("byPosts.size()", lessThanOrEqualTo(2))
                .body("byMembers[0].name", notNullValue())
                .extract().jsonPath();

        assertEquals(biggest.toString(), body.getString("byMembers[0].communityId"));
        assertEquals(13, body.getLong("byMembers[0].memberCount"), "a conta banida não conta como membro");
        assertEquals(second.toString(), body.getString("byMembers[1].communityId"));
        assertEquals(12, body.getLong("byMembers[1].memberCount"));

        List<Integer> postCounts = body.getList("byPosts.postCount");
        for (int index = 1; index < postCounts.size(); index++) {
            assertTrue(postCounts.get(index - 1) >= postCounts.get(index), "byPosts em ordem decrescente");
        }
    }

    private JsonPath summary(TestAccount admin) {
        return given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/summary")
                .then().statusCode(200)
                .body("generatedAtEpochMillis", notNullValue())
                .extract().jsonPath();
    }

    private static long mutualMatchesTwoDaysAgo(TestAccount admin) {
        return given().auth().oauth2(admin.token())
                .when().get("/admin/dashboard/timeseries?metric=new_mutual_matches&days=7")
                .then().statusCode(200)
                .extract().jsonPath().getLong("points[4].value");
    }

    /** Comunidade ativa com {@code members} contas (sem login), das quais {@code banned} banidas. */
    private static UUID communityWithMembers(String name, int members, int banned) {
        UUID communityId = UUIDv7Generator.generate();
        QuarkusTransaction.requiringNew().run(() -> {
            Community community = null;
            for (int index = 0; index < members; index++) {
                User user = new User();
                user.id = UUIDv7Generator.generate();
                user.name = name + index;
                user.lastName = "Membro";
                user.email = "admin-test-dashboard-" + UUID.randomUUID() + "@example.com";
                user.password = "x";
                user.birthdate = LocalDate.now().minusYears(25);
                user.verified = true;
                user.lastUpdatedAt = Instant.now();
                if (index < banned) {
                    user.banned = true;
                    user.bannedAt = Instant.now();
                    user.banReason = "Conta banida no teste do dashboard";
                }
                user.persist();

                UserProfile profile = new UserProfile();
                profile.id = UUIDv7Generator.generate();
                profile.user = user;
                profile.persist();

                if (community == null) {
                    community = new Community();
                    community.id = communityId;
                    community.name = name + " " + UUID.randomUUID();
                    community.owner = user;
                    community.active = true;
                    community.privacy = CommunityPrivacy.PUBLIC;
                    community.persist();
                }

                CommunityMembership membership = new CommunityMembership();
                membership.id = UUIDv7Generator.generate();
                membership.community = community;
                membership.userProfile = profile;
                membership.role = index == 0 ? CommunityMemberRole.ADMIN : CommunityMemberRole.MEMBER;
                membership.joinedAt = Instant.now();
                membership.persist();
            }
        });
        return communityId;
    }

    private void clearAll() {
        summaryCache.invalidateAll().await().indefinitely();
        timeSeriesCache.invalidateAll().await().indefinitely();
        topCommunitiesCache.invalidateAll().await().indefinitely();
    }
}
