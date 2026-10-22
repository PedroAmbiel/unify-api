package br.com.unify.matchable.admin.services;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.unify.matchable.admin.dto.dashboard.DashboardHeatCell;
import br.com.unify.matchable.admin.dto.dashboard.DashboardKpi;
import br.com.unify.matchable.admin.dto.dashboard.DashboardSlice;
import br.com.unify.matchable.admin.dto.dashboard.PostDashboardResponse;
import br.com.unify.matchable.admin.dto.dashboard.PostRankingRow;
import br.com.unify.matchable.admin.enums.DashboardGranularity;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Dimensão publicações (tabela única {@code posts}). Conta tudo o que foi
 * publicado no período, inclusive o que foi apagado ou removido depois (mede
 * atividade); a situação atual sai em {@code status}. Filtros: origem e
 * categoria da comunidade ({@code 0} = sem filtro; categoria implica posts de
 * comunidade).
 */
@ApplicationScoped
public class PostDashboardQueries extends DashboardSqlSupport {

    static final int TOP_POSTS = 5;
    static final int EXCERPT_LENGTH = 140;

    @Transactional
    @CacheResult(cacheName = "admin-dashboard-posts")
    public PostDashboardResponse posts(int days, DashboardGranularity granularity, String origin, int categoryId) {
        DashboardWindow window = DashboardWindow.of(days, granularity, Instant.now());
        Sql filter = postFilter(origin, categoryId)
                .param("curStart", window.startInstant())
                .param("prevStart", window.previousStartInstant());

        String engagement = "((select count(*) from post_likes l where l.fk_post = p.id) "
                + "+ (select count(*) from post_comments cm where cm.fk_post = p.id and cm.active))";
        String described = "p.media_oid is not null and coalesce(trim(p.image_description), '') <> ''";
        Object[] created = row("select "
                + "count(*) filter (where p.created_at >= :curStart), "
                + "count(*) filter (where p.created_at < :curStart), "
                + "count(*) filter (where p.created_at >= :curStart and p.media_oid is not null), "
                + "count(*) filter (where p.created_at < :curStart and p.media_oid is not null), "
                + "count(*) filter (where p.created_at >= :curStart and " + described + "), "
                + "count(*) filter (where p.created_at < :curStart and " + described + "), "
                + "coalesce(sum(" + engagement + ") filter (where p.created_at >= :curStart), 0), "
                + "coalesce(sum(" + engagement + ") filter (where p.created_at < :curStart), 0), "
                + "count(*) filter (where p.created_at >= :curStart and p.active), "
                + "count(*) filter (where p.created_at >= :curStart and not p.active and p.moderated_at is null), "
                + "count(*) filter (where p.created_at >= :curStart and p.moderated_at is not null) "
                + "from posts p where p.created_at >= :prevStart" + filter.where(), filter);

        Object[] moderated = row("select count(*) filter (where p.moderated_at >= :curStart), "
                + "count(*) filter (where p.moderated_at < :curStart) "
                + "from posts p where p.moderated_at >= :prevStart" + filter.where(), filter);
        Object[] likes = row("select count(*) filter (where l.created_at >= :curStart), "
                + "count(*) filter (where l.created_at < :curStart) "
                + "from post_likes l join posts p on p.id = l.fk_post where l.created_at >= :prevStart"
                + filter.where(), filter);
        Object[] comments = row("select count(*) filter (where cm.created_at >= :curStart), "
                + "count(*) filter (where cm.created_at < :curStart) "
                + "from post_comments cm join posts p on p.id = cm.fk_post "
                + "where cm.active and cm.created_at >= :prevStart" + filter.where(), filter);
        Object[] reports = row("select count(*) filter (where r.created_at >= :curStart), "
                + "count(*) filter (where r.created_at < :curStart) "
                + "from user_reports r join posts p on p.id = r.fk_reported_post where r.created_at >= :prevStart"
                + filter.where(), filter);

        long posts = count(at(created, 0));
        long postsPrevious = count(at(created, 1));
        long withImage = count(at(created, 2));
        long withImagePrevious = count(at(created, 3));

        List<DashboardKpi> kpis = List.of(
                countKpi("posts_created", posts, postsPrevious),
                countKpi("post_likes", count(at(likes, 0)), count(at(likes, 1))),
                countKpi("comments", count(at(comments, 0)), count(at(comments, 1))),
                new DashboardKpi("engagement_per_post", average(number(at(created, 6)), posts),
                        average(number(at(created, 7)), postsPrevious), DashboardKpi.AVERAGE),
                new DashboardKpi("image_share", percent(withImage, posts), percent(withImagePrevious, postsPrevious),
                        DashboardKpi.PERCENT),
                new DashboardKpi("image_description_share", percent(count(at(created, 4)), withImage),
                        percent(count(at(created, 5)), withImagePrevious), DashboardKpi.PERCENT),
                countKpi("removed_by_moderation", count(at(moderated, 0)), count(at(moderated, 1))),
                countKpi("post_reports", count(at(reports, 0)), count(at(reports, 1))));

        Map<String, Map<java.time.LocalDate, Double>> byOrigin = new HashMap<>();
        rows("select " + window.bucketOfInstant("p.created_at") + ", p.origin, count(*) from posts p "
                + "where p.created_at >= :curStart" + filter.where() + " group by 1, 2", filter)
                .forEach(row -> byOrigin.computeIfAbsent(text(row[1]), key -> new HashMap<>())
                        .merge(AdminDashboardQueries.toLocalDate(row[0]), number(row[2]), Double::sum));
        var likesSeries = window.series("post_likes", byBucket(rows("select " + window.bucketOfInstant("l.created_at")
                + ", count(*) from post_likes l join posts p on p.id = l.fk_post where l.created_at >= :curStart"
                + filter.where() + " group by 1", filter)));
        var commentsSeries = window.series("comments", byBucket(rows("select " + window.bucketOfInstant("cm.created_at")
                + ", count(*) from post_comments cm join posts p on p.id = cm.fk_post "
                + "where cm.active and cm.created_at >= :curStart" + filter.where() + " group by 1", filter)));

        String zone = DashboardWindow.LOCAL_ZONE.getId();
        List<DashboardHeatCell> heatmap = rows("select cast(extract(isodow from p.created_at at time zone '" + zone
                + "') as integer), cast(extract(hour from p.created_at at time zone '" + zone + "') as integer), count(*) "
                + "from posts p where p.created_at >= :curStart" + filter.where() + " group by 1, 2", filter).stream()
                .map(row -> new DashboardHeatCell((int) count(row[0]), (int) count(row[1]), count(row[2])))
                .toList();

        List<DashboardSlice> status = List.of(
                DashboardSlice.of("live", count(at(created, 8))),
                DashboardSlice.of("deleted", count(at(created, 9))),
                DashboardSlice.of("moderated", count(at(created, 10))));

        List<DashboardSlice> byCategory = "PERSONAL".equals(origin) ? List.of() : rows("select cc.id, cc.description, count(*) "
                + "from posts p join communities c on c.id = p.fk_community "
                + "left join community_categories cc on cc.id = c.fk_category "
                + "where p.origin = 'COMMUNITY' and p.created_at >= :curStart" + filter.where()
                + " group by cc.id, cc.description order by 3 desc, 2", filter).stream()
                .map(row -> new DashboardSlice(row[0] == null ? "none" : text(row[0]), text(row[1]), count(row[2]), null))
                .toList();

        List<PostRankingRow> topPosts = rows("select * from (select p.id, p.origin, left(p.body, " + EXCERPT_LENGTH + ") as excerpt, "
                + "c.name as community_name, concat(u.name, ' ', u.last_name) as author_name, "
                + "(select count(*) from post_likes l where l.fk_post = p.id) as likes, "
                + "(select count(*) from post_comments cm where cm.fk_post = p.id and cm.active) as comments, "
                + "p.created_at, p.active, p.moderated_at is not null as moderated "
                + "from posts p join users u on u.id = p.fk_user left join communities c on c.id = p.fk_community "
                + "where p.created_at >= :curStart" + filter.where() + ") t "
                + "order by t.likes + t.comments desc, t.created_at desc limit " + TOP_POSTS, filter).stream()
                .map(row -> new PostRankingRow((UUID) row[0], text(row[1]), text(row[2]), text(row[3]), text(row[4]),
                        count(row[5]), count(row[6]), toInstant(row[7]), Boolean.TRUE.equals(row[8]),
                        Boolean.TRUE.equals(row[9])))
                .toList();

        return new PostDashboardResponse(
                window.toRange(),
                kpis,
                List.of(
                        window.series("personal_posts", byOrigin.getOrDefault("PERSONAL", Map.of())),
                        window.series("community_posts", byOrigin.getOrDefault("COMMUNITY", Map.of())),
                        likesSeries,
                        commentsSeries),
                heatmap,
                status,
                byCategory,
                topPosts);
    }

    static Sql postFilter(String origin, int categoryId) {
        Sql sql = new Sql();
        if (categoryId > 0) {
            sql.and("p.origin = 'COMMUNITY'");
            sql.and("p.fk_community in (select fc.id from communities fc where fc.fk_category = :categoryId)",
                    "categoryId", categoryId);
        } else if ("PERSONAL".equals(origin) || "COMMUNITY".equals(origin)) {
            sql.and("p.origin = :origin", "origin", origin);
        }
        return sql;
    }

    static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant();
        }
        return value == null ? null : Instant.parse(value.toString());
    }
}
