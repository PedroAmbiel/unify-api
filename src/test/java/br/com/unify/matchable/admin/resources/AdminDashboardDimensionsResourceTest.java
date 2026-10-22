package br.com.unify.matchable.admin.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.AdminTestSupport;
import br.com.unify.matchable.admin.AdminTestSupport.TestAccount;
import br.com.unify.matchable.chat.entity.ChatMessage;
import br.com.unify.matchable.chat.entity.Conversation;
import br.com.unify.matchable.chat.enums.ChatMessageType;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.community.entity.CommunityCategory;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.user.entity.Disability;
import br.com.unify.matchable.user.entity.UserActivityDay;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;

/**
 * Semana 07 (ajuste) — painéis por dimensão: crescimento, matches,
 * publicações e comunidades. Cada teste lê o painel, cria o próprio dado e
 * confere a diferença (a base é compartilhada com as outras classes).
 */
@QuarkusTest
class AdminDashboardDimensionsResourceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Inject
    @CacheName("admin-dashboard-growth")
    Cache growthCache;

    @Inject
    @CacheName("admin-dashboard-matches")
    Cache matchesCache;

    @Inject
    @CacheName("admin-dashboard-posts")
    Cache postsCache;

    @Inject
    @CacheName("admin-dashboard-communities")
    Cache communitiesCache;

    @BeforeEach
    void clearCaches() {
        clearAll();
    }

    @AfterEach
    void retireContent() {
        AdminTestSupport.retireContent();
    }

    @Test
    void onlyAdminsReadTheDimensions() {
        TestAccount member = AdminTestSupport.member("Comum");
        TestAccount admin = AdminTestSupport.admin("Gestora");
        for (String path : List.of("/admin/dashboard/growth", "/admin/dashboard/matches",
                "/admin/dashboard/posts", "/admin/dashboard/communities")) {
            given().when().get(path).then().statusCode(401);
            given().auth().oauth2(member.token()).when().get(path).then().statusCode(403);
            given().auth().oauth2(admin.token()).when().get(path).then().statusCode(200)
                    .body("range.days", equalTo(30))
                    .body("range.granularity", equalTo("DAY"));
        }
    }

    @Test
    void invalidFiltersAreBadRequests() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        for (String query : List.of(
                "/admin/dashboard/growth?granularity=HOUR",
                "/admin/dashboard/growth?days=0",
                "/admin/dashboard/growth?inactivityDays=181",
                "/admin/dashboard/growth?genderId=-1",
                "/admin/dashboard/matches?connectionTypeId=0",
                "/admin/dashboard/posts?origin=STORY",
                "/admin/dashboard/posts?origin=PERSONAL&categoryId=1",
                "/admin/dashboard/communities?privacy=SECRET")) {
            given().auth().oauth2(admin.token()).when().get(query).then().statusCode(400)
                    .body("error", equalTo("VALIDATION_INVALID_ARGUMENT"));
        }
    }

    @Test
    void seriesFollowTheChosenGranularity() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        LocalDate today = LocalDate.now(SAO_PAULO);

        JsonPath daily = get(admin, "/admin/dashboard/growth?days=7&granularity=DAY");
        List<String> dailyBuckets = daily.getList("series[0].points.bucket");
        assertEquals(7, dailyBuckets.size());
        assertEquals(today.toString(), dailyBuckets.get(6));
        assertEquals(today.minusDays(6).toString(), daily.getString("range.start"));
        assertEquals(today.minusDays(13).toString(), daily.getString("range.previousStart"));

        JsonPath weekly = get(admin, "/admin/dashboard/posts?days=90&granularity=WEEK");
        List<String> weeklyBuckets = weekly.getList("series[0].points.bucket");
        assertTrue(weeklyBuckets.size() >= 13 && weeklyBuckets.size() <= 14, weeklyBuckets.toString());
        weeklyBuckets.forEach(bucket -> assertEquals(DayOfWeek.MONDAY, LocalDate.parse(bucket).getDayOfWeek(), bucket));

        given().auth().oauth2(admin.token()).when().get("/admin/dashboard/communities?days=180")
                .then().statusCode(200).body("range.granularity", equalTo("WEEK"));
    }

    @Test
    void growthCountsSignupsAndUsageAndFiltersByDisability() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        JsonPath before = get(admin, "/admin/dashboard/growth?days=7");
        assertEquals(List.of("new_users", "active_users", "total_users", "inactive_users", "activity_rate",
                "usage_frequency", "retention_7d"), before.getList("kpis.key"));
        assertEquals(List.of("active", "inactive", "unverified", "banned"), before.getList("accountStatus.key"));
        assertEquals(8, before.getList("cohorts").size());
        assertEquals(6, before.getList("byAgeGroup").size());

        TestAccount ana = AdminTestSupport.member("Ana");
        // primeira requisição autenticada do dia marca o uso
        given().auth().oauth2(ana.token()).when().get("/users/me").then().statusCode(200);
        assertEquals(1L, (long) QuarkusTransaction.requiringNew().call(() ->
                UserActivityDay.count("userId = ?1 and activityDate = ?2", ana.userId(), LocalDate.now(SAO_PAULO))));
        given().auth().oauth2(ana.token()).when().get("/users/me").then().statusCode(200);
        assertEquals(1L, (long) QuarkusTransaction.requiringNew().call(() -> UserActivityDay.count("userId", ana.userId())),
                "um registro por dia, por mais requisições que a pessoa faça");

        clearAll();
        JsonPath after = get(admin, "/admin/dashboard/growth?days=7");
        assertEquals(kpi(before, "new_users") + 1, kpi(after, "new_users"));
        assertEquals(kpi(before, "active_users") + 1, kpi(after, "active_users"));
        assertEquals(kpi(before, "total_users") + 1, kpi(after, "total_users"));
        assertEquals(last(before, 0) + 1, last(after, 0), "série de cadastros ganha 1 hoje");
        assertEquals(last(before, 2) + 1, last(after, 2), "total acumulado termina no total de contas");

        // filtro por deficiência declarada: só quem declarou entra
        int disabilityId = QuarkusTransaction.requiringNew().call(() -> Disability.<Disability>findAll().firstResult().id);
        String filtered = "/admin/dashboard/growth?days=7&disabilityId=" + disabilityId;
        JsonPath filteredBefore = get(admin, filtered);
        QuarkusTransaction.requiringNew().run(() -> {
            UserProfile profile = UserProfile.findById(ana.profileId());
            profile.disabilities.add(Disability.findById(disabilityId));
        });
        clearAll();
        JsonPath filteredAfter = get(admin, filtered);
        assertEquals(kpi(filteredBefore, "total_users") + 1, kpi(filteredAfter, "total_users"));
        assertTrue(kpi(filteredAfter, "total_users") <= kpi(after, "total_users"));
    }

    @Test
    void matchesFollowTheFunnelFromLikeToConversation() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount ana = AdminTestSupport.member("Ana");
        TestAccount bia = AdminTestSupport.member("Bia");
        TestAccount carla = AdminTestSupport.member("Carla");
        JsonPath before = get(admin, "/admin/dashboard/matches?days=7");
        assertEquals(List.of("likes", "mutual", "conversation_started", "both_replied"), before.getList("funnel.key"));

        Instant now = Instant.now();
        QuarkusTransaction.requiringNew().run(() -> {
            UserProfile anaProfile = UserProfile.findById(ana.profileId());
            UserProfile biaProfile = UserProfile.findById(bia.profileId());
            UserPossibleMatch mutual = new UserPossibleMatch();
            mutual.id = UUIDv7Generator.generate();
            mutual.starterProfile = anaProfile;
            mutual.pendingProfile = biaProfile;
            mutual.createdAt = now.minusSeconds(3 * 3600);
            mutual.starterAccepted = true;
            mutual.pendingAccepted = true;
            mutual.matchedAt = now.minusSeconds(3600);
            mutual.persist();

            Conversation conversation = new Conversation();
            conversation.id = UUIDv7Generator.generate();
            conversation.possibleMatch = mutual;
            conversation.participantOne = anaProfile;
            conversation.participantTwo = biaProfile;
            conversation.createdAt = now;
            conversation.persist();
            message(conversation, anaProfile, now.minusSeconds(1800));
            message(conversation, biaProfile, now.minusSeconds(600));

            UserPossibleMatch waiting = new UserPossibleMatch();
            waiting.id = UUIDv7Generator.generate();
            waiting.starterProfile = anaProfile;
            waiting.pendingProfile = UserProfile.findById(carla.profileId());
            waiting.createdAt = now;
            waiting.starterAccepted = true;
            waiting.persist();
        });
        clearAll();

        JsonPath after = get(admin, "/admin/dashboard/matches?days=7");
        assertEquals(kpi(before, "likes") + 2, kpi(after, "likes"));
        assertEquals(kpi(before, "mutual_matches") + 1, kpi(after, "mutual_matches"));
        assertEquals(kpi(before, "pending_now") + 1, kpi(after, "pending_now"));
        assertEquals(kpi(before, "conversations_replied") + 1, kpi(after, "conversations_replied"));
        assertEquals(slice(before, "outcomes", "pending") + 1, slice(after, "outcomes", "pending"));
        assertEquals(slice(before, "timeToMatch", "1_6h") + 1, slice(after, "timeToMatch", "1_6h"));
        List<Integer> funnel = after.getList("funnel.value");
        for (int index = 1; index < funnel.size(); index++) {
            assertTrue(funnel.get(index) <= funnel.get(index - 1), "funil nunca cresce: " + funnel);
        }
        assertEquals(sum(before, 3) + 1, sum(after, 3), "conversa com resposta entra na série pela data da resposta");

        given().auth().oauth2(admin.token()).when().get("/admin/dashboard/matches?days=7&connectionTypeId=1")
                .then().statusCode(200).body("funnel", hasSize(4));
    }

    @Test
    void postsSplitByOriginAndCategory() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount author = AdminTestSupport.member("Autora");
        int categoryId = QuarkusTransaction.requiringNew().call(() ->
                CommunityCategory.<CommunityCategory>findAll().firstResult().id);
        JsonPath all = get(admin, "/admin/dashboard/posts?days=7");
        JsonPath communityOnly = get(admin, "/admin/dashboard/posts?days=7&origin=COMMUNITY");
        JsonPath categoryBefore = get(admin, "/admin/dashboard/posts?days=7&categoryId=" + categoryId);

        AdminTestSupport.personalPost(author, "Post pessoal para o painel");
        UUID communityPostId = AdminTestSupport.communityPost(author, "Post de comunidade para o painel");
        QuarkusTransaction.requiringNew().run(() ->
                Post.<Post>findById(communityPostId).community.category = CommunityCategory.findById(categoryId));
        clearAll();

        JsonPath allAfter = get(admin, "/admin/dashboard/posts?days=7");
        assertEquals(kpi(all, "posts_created") + 2, kpi(allAfter, "posts_created"));
        assertEquals(last(all, 0) + 1, last(allAfter, 0), "série de posts pessoais");
        assertEquals(last(all, 1) + 1, last(allAfter, 1), "série de posts de comunidade");
        assertEquals(slice(all, "status", "live") + 2, slice(allAfter, "status", "live"));
        ZonedDateTime now = ZonedDateTime.now(SAO_PAULO);
        assertTrue(allAfter.getList("heatmap", Map.class).stream().anyMatch(cell ->
                ((Number) cell.get("weekday")).intValue() == now.getDayOfWeek().getValue()
                        && Math.abs(((Number) cell.get("hour")).intValue() - now.getHour()) <= 1),
                "mapa de calor marca o dia e a hora da publicação");

        JsonPath communityAfter = get(admin, "/admin/dashboard/posts?days=7&origin=COMMUNITY");
        assertEquals(kpi(communityOnly, "posts_created") + 1, kpi(communityAfter, "posts_created"));

        JsonPath categoryAfter = get(admin, "/admin/dashboard/posts?days=7&categoryId=" + categoryId);
        assertEquals(kpi(categoryBefore, "posts_created") + 1, kpi(categoryAfter, "posts_created"));
        given().auth().oauth2(admin.token()).when().get("/admin/dashboard/posts?days=7&origin=PERSONAL")
                .then().statusCode(200).body("byCategory", hasSize(0));
    }

    @Test
    void communitiesCountCreationMembersAndPosts() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        TestAccount owner = AdminTestSupport.member("Dona");
        JsonPath before = get(admin, "/admin/dashboard/communities?days=7");
        JsonPath privateBefore = get(admin, "/admin/dashboard/communities?days=7&privacy=PRIVATE");
        assertEquals(List.of("1", "2_5", "6_10", "11_25", "26_plus"), before.getList("sizeDistribution.key"));

        UUID postId = AdminTestSupport.communityPost(owner, "Primeiro post da comunidade nova");
        UUID communityId = QuarkusTransaction.requiringNew().call(() -> Post.<Post>findById(postId).community.id);
        clearAll();

        JsonPath after = get(admin, "/admin/dashboard/communities?days=7");
        assertEquals(kpi(before, "communities_created") + 1, kpi(after, "communities_created"));
        assertEquals(kpi(before, "new_members") + 1, kpi(after, "new_members"));
        assertEquals(kpi(before, "community_posts") + 1, kpi(after, "community_posts"));
        assertEquals(kpi(before, "total_communities") + 1, kpi(after, "total_communities"));
        assertEquals(slice(before, "sizeDistribution", "1") + 1, slice(after, "sizeDistribution", "1"));
        assertTrue(after.getList("scatter.id").contains(communityId.toString()));
        assertTrue(after.getList("ranking").size() <= 10);

        JsonPath privateAfter = get(admin, "/admin/dashboard/communities?days=7&privacy=PRIVATE");
        assertEquals(kpi(privateBefore, "communities_created"), kpi(privateAfter, "communities_created"),
                "comunidade pública não entra no filtro de privadas");
    }

    @Test
    void responsesCarryEveryKpiKey() {
        TestAccount admin = AdminTestSupport.admin("Gestora");
        given().auth().oauth2(admin.token()).when().get("/admin/dashboard/matches").then().statusCode(200)
                .body("kpis.key", hasItems("likes", "mutual_matches", "declines", "match_rate", "pending_now",
                        "conversations_replied", "conversation_rate", "median_hours_to_match"))
                .body("series.key", hasItems("likes", "mutual_matches", "declines", "conversations_replied"));
        given().auth().oauth2(admin.token()).when().get("/admin/dashboard/posts").then().statusCode(200)
                .body("kpis.key", hasItems("posts_created", "post_likes", "comments", "engagement_per_post", "image_share",
                        "image_description_share", "removed_by_moderation", "post_reports"))
                .body("kpis.find { it.key == 'image_share' }.unit", equalTo("PERCENT"));
        given().auth().oauth2(admin.token()).when().get("/admin/dashboard/communities").then().statusCode(200)
                .body("kpis.key", hasItems("communities_created", "new_members", "active_communities",
                        "inactive_communities", "community_posts", "pending_join_requests", "average_members",
                        "total_communities"))
                .body("series.key", hasItems("new_members", "communities_created", "community_posts"));
        given().auth().oauth2(admin.token()).when().get("/admin/dashboard/growth?inactivityDays=7").then().statusCode(200)
                .body("inactivityDays", equalTo(7))
                .body("series.key", hasItems("new_users", "active_users", "total_users"));
        given().auth().oauth2(admin.token()).when().get("/admin/dashboard/posts?origin=PERSONAL&categoryId=1")
                .then().statusCode(400).body("message", containsString("pessoais"));
    }

    // -------------------------------------------------------------- apoio

    private static void message(Conversation conversation, UserProfile sender, Instant at) {
        ChatMessage message = new ChatMessage();
        message.id = UUIDv7Generator.generate();
        message.conversation = conversation;
        message.sender = sender;
        message.type = ChatMessageType.TEXT;
        message.body = "Oi!";
        message.createdAt = at;
        message.updatedAt = at;
        message.persist();
    }

    private static JsonPath get(TestAccount admin, String path) {
        return given().auth().oauth2(admin.token()).when().get(path).then().statusCode(200).extract().jsonPath();
    }

    private static long kpi(JsonPath body, String key) {
        Number value = body.get("kpis.find { it.key == '" + key + "' }.current");
        return value == null ? 0 : Math.round(value.doubleValue());
    }

    private static long slice(JsonPath body, String list, String key) {
        Number value = body.get(list + ".find { it.key == '" + key + "' }.value");
        return value == null ? 0 : value.longValue();
    }

    private static long sum(JsonPath body, int index) {
        List<Number> values = body.getList("series[" + index + "].points.value");
        return values.stream().mapToLong(value -> Math.round(value.doubleValue())).sum();
    }

    /** Último ponto (hoje) da série {@code index}. */
    private static long last(JsonPath body, int index) {
        List<Number> values = body.getList("series[" + index + "].points.value");
        return Math.round(values.get(values.size() - 1).doubleValue());
    }

    private void clearAll() {
        for (Cache cache : List.of(growthCache, matchesCache, postsCache, communitiesCache)) {
            cache.invalidateAll().await().indefinitely();
        }
    }
}
