package br.com.unify.matchable.admin.services;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.unify.matchable.admin.dto.dashboard.DashboardKpi;
import br.com.unify.matchable.admin.dto.dashboard.DashboardSlice;
import br.com.unify.matchable.admin.dto.dashboard.FunnelStep;
import br.com.unify.matchable.admin.dto.dashboard.MatchDashboardResponse;
import br.com.unify.matchable.admin.enums.DashboardGranularity;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Dimensão matches, sobre {@code user_possible_matches} (uma linha por par):
 * curtida = quem começou aceitou ({@code starter_accepted}); mútuo = os dois
 * aceitaram ({@code matched_at}); recusa = {@code declined_at} (o "dispensar"
 * do discovery e a recusa de convite). "Conversa com resposta" = os dois lados
 * mandaram ao menos uma mensagem. Filtro opcional pelo tipo de conexão que
 * quem curtiu procura ({@code 0} = sem filtro).
 */
@ApplicationScoped
public class MatchDashboardQueries extends DashboardSqlSupport {

    static final List<String> TIME_TO_MATCH_BUCKETS = List.of("lt_1h", "1_6h", "6_24h", "1_3d", "3_7d", "gt_7d");

    private static final String MUTUAL = "m.starter_accepted and m.pending_accepted";

    @Transactional
    @CacheResult(cacheName = "admin-dashboard-matches")
    public MatchDashboardResponse matches(int days, DashboardGranularity granularity, int connectionTypeId) {
        DashboardWindow window = DashboardWindow.of(days, granularity, Instant.now());
        Sql filter = new Sql()
                .param("curStart", window.startInstant())
                .param("prevStart", window.previousStartInstant());
        if (connectionTypeId > 0) {
            filter.and("exists (select 1 from user_match_preferences mp where mp.fk_user_profile = m.fk_starter_user_profile "
                    + "and mp.fk_connection_type = :connectionTypeId)", "connectionTypeId", connectionTypeId);
        }

        Object[] pairs = row("select "
                + "count(*) filter (where m.starter_accepted and m.created_at >= :curStart), "
                + "count(*) filter (where m.starter_accepted and m.created_at >= :prevStart and m.created_at < :curStart), "
                + "count(*) filter (where " + MUTUAL + " and m.matched_at >= :curStart), "
                + "count(*) filter (where " + MUTUAL + " and m.matched_at >= :prevStart and m.matched_at < :curStart), "
                + "count(*) filter (where m.declined_at >= :curStart), "
                + "count(*) filter (where m.declined_at >= :prevStart and m.declined_at < :curStart), "
                // curtidas do período que já viraram match (taxa de match e funil)
                + "count(*) filter (where " + MUTUAL + " and m.created_at >= :curStart), "
                + "count(*) filter (where " + MUTUAL + " and m.created_at >= :prevStart and m.created_at < :curStart), "
                + "count(*) filter (where m.starter_accepted and m.pending_accepted is null and m.declined_at is null), "
                + "count(*) filter (where m.starter_accepted and m.created_at >= :curStart "
                + "and m.pending_accepted is null and m.declined_at is null), "
                + "count(*) filter (where m.starter_accepted and m.created_at >= :curStart and m.pending_accepted = false) "
                + "from user_possible_matches m where 1 = 1" + filter.where(), filter);

        String hours = "extract(epoch from (m.matched_at - m.created_at)) / 3600.0";
        Object[] median = row("select "
                + "percentile_cont(0.5) within group (order by " + hours + ") filter (where m.matched_at >= :curStart), "
                + "percentile_cont(0.5) within group (order by " + hours + ") filter (where m.matched_at < :curStart) "
                + "from user_possible_matches m where " + MUTUAL + " and m.matched_at >= :prevStart" + filter.where(), filter);

        String sent = "exists (select 1 from conversations c join chat_messages cm on cm.fk_conversation = c.id "
                + "where c.fk_user_possible_match = m.id and cm.fk_sender_user_profile = m.%s)";
        String starterSent = sent.formatted("fk_starter_user_profile");
        String pendingSent = sent.formatted("fk_pending_user_profile");
        Object[] conversations = row("select "
                + "count(*) filter (where m.matched_at >= :curStart and " + starterSent + " and " + pendingSent + "), "
                + "count(*) filter (where m.matched_at >= :prevStart and m.matched_at < :curStart and "
                + starterSent + " and " + pendingSent + "), "
                + "count(*) filter (where m.created_at >= :curStart and (" + starterSent + " or " + pendingSent + ")), "
                + "count(*) filter (where m.created_at >= :curStart and " + starterSent + " and " + pendingSent + ") "
                + "from user_possible_matches m where " + MUTUAL
                + " and (m.matched_at >= :prevStart or m.created_at >= :curStart)" + filter.where(), filter);

        long likes = count(at(pairs, 0));
        long likesPrevious = count(at(pairs, 1));
        long mutual = count(at(pairs, 2));
        long mutualPrevious = count(at(pairs, 3));
        long replied = count(at(conversations, 0));
        long repliedPrevious = count(at(conversations, 1));

        List<DashboardKpi> kpis = List.of(
                countKpi("likes", likes, likesPrevious),
                countKpi("mutual_matches", mutual, mutualPrevious),
                countKpi("declines", count(at(pairs, 4)), count(at(pairs, 5))),
                new DashboardKpi("match_rate", percent(count(at(pairs, 6)), likes),
                        percent(count(at(pairs, 7)), likesPrevious), DashboardKpi.PERCENT),
                snapshotKpi("pending_now", count(at(pairs, 8))),
                countKpi("conversations_replied", replied, repliedPrevious),
                new DashboardKpi("conversation_rate", percent(replied, mutual), percent(repliedPrevious, mutualPrevious),
                        DashboardKpi.PERCENT),
                new DashboardKpi("median_hours_to_match", numberOrNull(at(median, 0)), numberOrNull(at(median, 1)),
                        DashboardKpi.HOURS));

        List<FunnelStep> funnel = List.of(
                new FunnelStep("likes", likes),
                new FunnelStep("mutual", count(at(pairs, 6))),
                new FunnelStep("conversation_started", count(at(conversations, 2))),
                new FunnelStep("both_replied", count(at(conversations, 3))));

        List<DashboardSlice> outcomes = List.of(
                DashboardSlice.of("mutual", count(at(pairs, 6))),
                DashboardSlice.of("pending", count(at(pairs, 9))),
                DashboardSlice.of("declined", count(at(pairs, 10))));

        var likesSeries = window.series("likes", byBucket(rows("select " + window.bucketOfInstant("m.created_at")
                + ", count(*) from user_possible_matches m where m.starter_accepted and m.created_at >= :curStart"
                + filter.where() + " group by 1", filter)));
        var mutualSeries = window.series("mutual_matches", byBucket(rows("select " + window.bucketOfInstant("m.matched_at")
                + ", count(*) from user_possible_matches m where " + MUTUAL + " and m.matched_at >= :curStart"
                + filter.where() + " group by 1", filter)));
        var declineSeries = window.series("declines", byBucket(rows("select " + window.bucketOfInstant("m.declined_at")
                + ", count(*) from user_possible_matches m where m.declined_at >= :curStart"
                + filter.where() + " group by 1", filter)));
        // Resposta = quando o segundo lado mandou a primeira mensagem.
        String firstMessage = "(select min(cm.created_at) from chat_messages cm where cm.fk_conversation = c.id "
                + "and cm.fk_sender_user_profile = m.%s)";
        var repliedSeries = window.series("conversations_replied", byBucket(rows("select "
                + window.bucketOfInstant("t.replied_at") + ", count(*) from (select greatest("
                + firstMessage.formatted("fk_starter_user_profile") + ", "
                + firstMessage.formatted("fk_pending_user_profile") + ") as replied_at, "
                + firstMessage.formatted("fk_starter_user_profile") + " as starter_first, "
                + firstMessage.formatted("fk_pending_user_profile") + " as pending_first "
                + "from conversations c join user_possible_matches m on m.id = c.fk_user_possible_match where 1 = 1"
                + filter.where() + ") t where t.starter_first is not null and t.pending_first is not null "
                + "and t.replied_at >= :curStart group by 1", filter)));

        Map<String, Long> timeToMatch = new LinkedHashMap<>();
        TIME_TO_MATCH_BUCKETS.forEach(bucket -> timeToMatch.put(bucket, 0L));
        rows("select case when t.h < 1 then 'lt_1h' when t.h < 6 then '1_6h' when t.h < 24 then '6_24h' "
                + "when t.h < 72 then '1_3d' when t.h < 168 then '3_7d' else 'gt_7d' end, count(*) "
                + "from (select " + hours + " as h from user_possible_matches m where " + MUTUAL
                + " and m.matched_at >= :curStart" + filter.where() + ") t group by 1", filter)
                .forEach(row -> timeToMatch.put(text(row[0]), count(row[1])));

        List<DashboardSlice> byConnectionType = rows("select ct.id, ct.description, count(*) "
                + "from user_possible_matches m "
                + "left join user_match_preferences mp on mp.fk_user_profile = m.fk_starter_user_profile "
                + "left join connection_types ct on ct.id = mp.fk_connection_type "
                + "where " + MUTUAL + " and m.matched_at >= :curStart" + filter.where()
                + " group by ct.id, ct.description order by 3 desc, 2", filter).stream()
                .map(row -> new DashboardSlice(row[0] == null ? "none" : text(row[0]), text(row[1]), count(row[2]), null))
                .toList();

        return new MatchDashboardResponse(
                window.toRange(),
                kpis,
                List.of(likesSeries, mutualSeries, declineSeries, repliedSeries),
                funnel,
                outcomes,
                timeToMatch.entrySet().stream().map(entry -> DashboardSlice.of(entry.getKey(), entry.getValue())).toList(),
                byConnectionType);
    }
}
