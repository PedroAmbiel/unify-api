package br.com.unify.matchable.admin.services;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.unify.matchable.admin.dto.dashboard.CommunityDashboardResponse;
import br.com.unify.matchable.admin.dto.dashboard.CommunityPoint;
import br.com.unify.matchable.admin.dto.dashboard.CommunityRankingRow;
import br.com.unify.matchable.admin.dto.dashboard.DashboardKpi;
import br.com.unify.matchable.admin.dto.dashboard.DashboardSlice;
import br.com.unify.matchable.admin.enums.DashboardGranularity;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Dimensão comunidades. Criação usa {@code communities.created_at} (V36);
 * "novos membros" usa {@code joined_at} das participações que continuam
 * (sair da comunidade apaga a linha). Distribuições, dispersão e ranking só
 * consideram comunidades ativas. Filtros: categoria ({@code 0} = todas) e
 * privacidade (ALL, PUBLIC, PRIVATE).
 */
@ApplicationScoped
public class CommunityDashboardQueries extends DashboardSqlSupport {

    static final List<String> SIZE_BUCKETS = List.of("1", "2_5", "6_10", "11_25", "26_plus");
    static final int RANKING_SIZE = 10;
    static final int SCATTER_LIMIT = 200;

    @Transactional
    @CacheResult(cacheName = "admin-dashboard-communities")
    public CommunityDashboardResponse communities(int days, DashboardGranularity granularity, int categoryId, String privacy) {
        DashboardWindow window = DashboardWindow.of(days, granularity, Instant.now());
        Sql filter = communityFilter(categoryId, privacy)
                .param("curStart", window.startInstant())
                .param("prevStart", window.previousStartInstant());

        Object[] created = row("select count(*) filter (where c.created_at >= :curStart), "
                + "count(*) filter (where c.created_at < :curStart) "
                + "from communities c where c.created_at >= :prevStart" + filter.where(), filter);
        Object[] members = row("select count(*) filter (where m.joined_at >= :curStart), "
                + "count(*) filter (where m.joined_at < :curStart) "
                + "from community_memberships m join communities c on c.id = m.fk_community "
                + "where m.joined_at >= :prevStart" + filter.where(), filter);
        Object[] posting = row("select count(distinct p.fk_community) filter (where p.created_at >= :curStart), "
                + "count(distinct p.fk_community) filter (where p.created_at < :curStart), "
                + "count(*) filter (where p.created_at >= :curStart), "
                + "count(*) filter (where p.created_at < :curStart) "
                + "from posts p join communities c on c.id = p.fk_community "
                + "where p.origin = 'COMMUNITY' and c.active and p.created_at >= :prevStart" + filter.where(), filter);
        Object[] pendingRequests = row("select count(*) from community_join_requests r "
                + "join communities c on c.id = r.fk_community where c.active" + filter.where(), filter);

        // Uma linha por comunidade ativa: base da situação atual, distribuições, dispersão e ranking.
        List<Object[]> communities = rows("select c.id, c.name, cc.description, c.privacy, "
                + "(select count(*) from community_memberships m where m.fk_community = c.id) as members, "
                + "(select count(*) from community_memberships m where m.fk_community = c.id and m.joined_at >= :curStart), "
                + "(select count(*) from posts p where p.fk_community = c.id and p.created_at >= :curStart), "
                + "(select count(*) from post_likes l join posts p on p.id = l.fk_post "
                + "where p.fk_community = c.id and l.created_at >= :curStart) "
                + "+ (select count(*) from post_comments cm join posts p on p.id = cm.fk_post "
                + "where p.fk_community = c.id and cm.active and cm.created_at >= :curStart), "
                + "c.fk_category "
                + "from communities c left join community_categories cc on cc.id = c.fk_category "
                + "where c.active" + filter.where() + " order by c.name", filter);

        long total = communities.size();
        long withoutPosts = communities.stream().filter(row -> count(row[6]) == 0).count();
        long totalMembers = communities.stream().mapToLong(row -> count(row[4])).sum();

        List<DashboardKpi> kpis = List.of(
                countKpi("communities_created", count(at(created, 0)), count(at(created, 1))),
                countKpi("new_members", count(at(members, 0)), count(at(members, 1))),
                countKpi("active_communities", count(at(posting, 0)), count(at(posting, 1))),
                snapshotKpi("inactive_communities", withoutPosts),
                countKpi("community_posts", count(at(posting, 2)), count(at(posting, 3))),
                snapshotKpi("pending_join_requests", count(at(pendingRequests, 0))),
                new DashboardKpi("average_members", average(totalMembers, total), null, DashboardKpi.AVERAGE),
                snapshotKpi("total_communities", total));

        var newMembers = window.series("new_members", byBucket(rows("select " + window.bucketOfInstant("m.joined_at")
                + ", count(*) from community_memberships m join communities c on c.id = m.fk_community "
                + "where m.joined_at >= :curStart" + filter.where() + " group by 1", filter)));
        var createdSeries = window.series("communities_created", byBucket(rows("select "
                + window.bucketOfInstant("c.created_at") + ", count(*) from communities c "
                + "where c.created_at >= :curStart" + filter.where() + " group by 1", filter)));
        var postsSeries = window.series("community_posts", byBucket(rows("select "
                + window.bucketOfInstant("p.created_at") + ", count(*) from posts p join communities c on c.id = p.fk_community "
                + "where p.origin = 'COMMUNITY' and c.active and p.created_at >= :curStart" + filter.where()
                + " group by 1", filter)));

        Map<String, long[]> categories = new LinkedHashMap<>();
        Map<String, String> categoryLabels = new HashMap<>();
        Map<String, Long> sizes = new LinkedHashMap<>();
        SIZE_BUCKETS.forEach(bucket -> sizes.put(bucket, 0L));
        for (Object[] row : communities) {
            String categoryKey = row[8] == null ? "none" : text(row[8]);
            categoryLabels.putIfAbsent(categoryKey, text(row[2]));
            long[] totals = categories.computeIfAbsent(categoryKey, key -> new long[2]);
            totals[0]++;
            totals[1] += count(row[4]);
            sizes.merge(sizeBucket(count(row[4])), 1L, Long::sum);
        }
        List<DashboardSlice> byCategory = categories.entrySet().stream()
                .map(entry -> new DashboardSlice(entry.getKey(), categoryLabels.get(entry.getKey()),
                        entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(DashboardSlice::value).reversed()
                        .thenComparing(slice -> slice.label() == null ? "" : slice.label()))
                .toList();

        List<CommunityPoint> scatter = communities.stream().limit(SCATTER_LIMIT)
                .map(row -> new CommunityPoint((UUID) row[0], text(row[1]), text(row[2]), count(row[4]), count(row[6]),
                        count(row[7])))
                .toList();

        List<CommunityRankingRow> ranking = communities.stream()
                .sorted(Comparator.<Object[]>comparingLong(row -> count(row[5])).reversed()
                        .thenComparing(Comparator.<Object[]>comparingLong(row -> count(row[4])).reversed())
                        .thenComparing(row -> text(row[1])))
                .limit(RANKING_SIZE)
                .map(row -> new CommunityRankingRow((UUID) row[0], text(row[1]), text(row[2]), text(row[3]),
                        count(row[4]), count(row[5]), count(row[6]), count(row[7])))
                .toList();

        return new CommunityDashboardResponse(
                window.toRange(),
                kpis,
                List.of(newMembers, createdSeries, postsSeries),
                byCategory,
                sizes.entrySet().stream().map(entry -> DashboardSlice.of(entry.getKey(), entry.getValue())).toList(),
                scatter,
                ranking);
    }

    static Sql communityFilter(int categoryId, String privacy) {
        Sql sql = new Sql();
        if (categoryId > 0) {
            sql.and("c.fk_category = :categoryId", "categoryId", categoryId);
        }
        if ("PUBLIC".equals(privacy) || "PRIVATE".equals(privacy)) {
            sql.and("c.privacy = :privacy", "privacy", privacy);
        }
        return sql;
    }

    static String sizeBucket(long members) {
        if (members <= 1) {
            return "1";
        }
        if (members <= 5) {
            return "2_5";
        }
        if (members <= 10) {
            return "6_10";
        }
        return members <= 25 ? "11_25" : "26_plus";
    }
}
