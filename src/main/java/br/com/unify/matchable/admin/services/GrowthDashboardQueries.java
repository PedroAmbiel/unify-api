package br.com.unify.matchable.admin.services;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.unify.matchable.admin.dto.dashboard.CohortRow;
import br.com.unify.matchable.admin.dto.dashboard.DashboardKpi;
import br.com.unify.matchable.admin.dto.dashboard.DashboardPoint;
import br.com.unify.matchable.admin.dto.dashboard.DashboardSeries;
import br.com.unify.matchable.admin.dto.dashboard.DashboardSlice;
import br.com.unify.matchable.admin.dto.dashboard.GrowthDashboardResponse;
import br.com.unify.matchable.admin.enums.DashboardGranularity;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Dimensão crescimento. Só contas de usuário ({@code role = 'USER'}); "uso"
 * vem de {@code user_activity_days} (V36). Filtros opcionais por gênero e
 * deficiência declarados no perfil ({@code 0} = sem filtro, chave de cache
 * nunca nula).
 */
@ApplicationScoped
public class GrowthDashboardQueries extends DashboardSqlSupport {

    /** Coortes semanais exibidas (a atual incluída). */
    static final int COHORT_WEEKS = 8;

    static final List<String> AGE_GROUPS = List.of("under_18", "18_24", "25_34", "35_44", "45_59", "60_plus");

    @Transactional
    @CacheResult(cacheName = "admin-dashboard-growth")
    public GrowthDashboardResponse growth(int days, DashboardGranularity granularity, int inactivityDays,
                                          int genderId, int disabilityId) {
        DashboardWindow window = DashboardWindow.of(days, granularity, Instant.now());
        Sql filter = userFilter(genderId, disabilityId)
                .param("curStart", window.startInstant())
                .param("prevStart", window.previousStartInstant())
                .param("curDate", window.start())
                .param("prevDate", window.previousStart())
                .param("today", window.today());

        Object[] signups = row("select count(*) filter (where u.created_at >= :curStart), "
                + "count(*) filter (where u.created_at < :curStart) "
                + "from users u where u.created_at >= :prevStart" + filter.where(), filter);

        Object[] totals = row("select count(*), count(*) filter (where u.created_at < :curStart) "
                + "from users u where 1 = 1" + filter.where(), filter);

        // Pessoas distintas com uso no período e quantos dias de uso somaram.
        Object[] activity = row("select count(distinct a.fk_user) filter (where a.activity_date >= :curDate), "
                + "count(*) filter (where a.activity_date >= :curDate), "
                + "count(distinct a.fk_user) filter (where a.activity_date < :curDate), "
                + "count(*) filter (where a.activity_date < :curDate) "
                + "from user_activity_days a join users u on u.id = a.fk_user "
                + "where a.activity_date >= :prevDate and a.activity_date <= :today" + filter.where(), filter);

        // Situação das contas agora e taxa de atividade (janela de inatividade) agora e no fim do período anterior.
        Sql status = filter.copy()
                .param("recentFrom", window.today().minusDays(inactivityDays))
                .param("previousRecentFrom", window.previousEnd().minusDays(inactivityDays));
        String recent = "exists (select 1 from user_activity_days a where a.fk_user = u.id and a.activity_date > :recentFrom)";
        String previousRecent = "exists (select 1 from user_activity_days a where a.fk_user = u.id "
                + "and a.activity_date > :previousRecentFrom and a.activity_date < :curDate)";
        Object[] accounts = row("select "
                + "count(*) filter (where u.banned), "
                + "count(*) filter (where not u.banned and not u.verified), "
                + "count(*) filter (where not u.banned and u.verified and " + recent + "), "
                + "count(*) filter (where not u.banned and u.verified and not " + recent + "), "
                + "count(*) filter (where not u.banned and u.verified and u.created_at < :curStart), "
                + "count(*) filter (where not u.banned and u.verified and u.created_at < :curStart and " + previousRecent + ") "
                + "from users u where 1 = 1" + status.where(), status);

        // Retenção de 7 dias: cadastrou e voltou entre o 1º e o 7º dia depois do cadastro.
        Sql retention = filter.copy().param("cohortCutoff", window.now().minus(7, ChronoUnit.DAYS));
        String signupDay = DashboardWindow.localDate("u.created_at");
        String retained = "exists (select 1 from user_activity_days a where a.fk_user = u.id "
                + "and a.activity_date between " + signupDay + " + 1 and " + signupDay + " + 7)";
        Object[] retentionRow = row("select "
                + "count(*) filter (where u.created_at >= :curStart and u.created_at < :cohortCutoff), "
                + "count(*) filter (where u.created_at >= :curStart and u.created_at < :cohortCutoff and " + retained + "), "
                + "count(*) filter (where u.created_at < :curStart), "
                + "count(*) filter (where u.created_at < :curStart and " + retained + ") "
                + "from users u where u.created_at >= :prevStart" + retention.where(), retention);

        long activeCurrent = count(at(activity, 0));
        long activeDaysCurrent = count(at(activity, 1));
        long activePrevious = count(at(activity, 2));
        long activeDaysPrevious = count(at(activity, 3));
        long activeNow = count(at(accounts, 2));
        long inactiveNow = count(at(accounts, 3));

        List<DashboardKpi> kpis = List.of(
                countKpi("new_users", count(at(signups, 0)), count(at(signups, 1))),
                countKpi("active_users", activeCurrent, activePrevious),
                countKpi("total_users", count(at(totals, 0)), count(at(totals, 1))),
                snapshotKpi("inactive_users", inactiveNow),
                new DashboardKpi("activity_rate", percent(activeNow, activeNow + inactiveNow),
                        percent(count(at(accounts, 5)), count(at(accounts, 4))), DashboardKpi.PERCENT),
                // Frequência: em média, em quantos % dos dias do período quem usou voltou a usar.
                new DashboardKpi("usage_frequency", percent(activeDaysCurrent, activeCurrent * days),
                        percent(activeDaysPrevious, activePrevious * days), DashboardKpi.PERCENT),
                new DashboardKpi("retention_7d", percent(count(at(retentionRow, 1)), count(at(retentionRow, 0))),
                        percent(count(at(retentionRow, 3)), count(at(retentionRow, 2))), DashboardKpi.PERCENT));

        DashboardSeries newUsers = window.series("new_users", byBucket(rows(
                "select " + window.bucketOfInstant("u.created_at") + ", count(*) from users u "
                        + "where u.created_at >= :curStart" + filter.where() + " group by 1", filter)));
        DashboardSeries activeUsers = window.series("active_users", byBucket(rows(
                "select " + window.bucketOfDate("a.activity_date") + ", count(distinct a.fk_user) "
                        + "from user_activity_days a join users u on u.id = a.fk_user "
                        + "where a.activity_date >= :curDate and a.activity_date <= :today" + filter.where()
                        + " group by 1", filter)));
        DashboardSeries totalUsers = cumulative("total_users", count(at(totals, 1)), newUsers);

        List<DashboardSlice> accountStatus = List.of(
                DashboardSlice.of("active", activeNow),
                DashboardSlice.of("inactive", inactiveNow),
                DashboardSlice.of("unverified", count(at(accounts, 1))),
                DashboardSlice.of("banned", count(at(accounts, 0))));

        return new GrowthDashboardResponse(
                window.toRange(),
                inactivityDays,
                kpis,
                List.of(newUsers, activeUsers, totalUsers),
                accountStatus,
                ageGroups(filter),
                byDisability(filter),
                byGender(filter),
                cohorts(window, filter));
    }

    // ---------------------------------------------------------------- partes

    static Sql userFilter(int genderId, int disabilityId) {
        Sql sql = new Sql().and("u.role = 'USER'");
        if (genderId > 0) {
            sql.and("exists (select 1 from user_profiles fp where fp.fk_user = u.id and fp.fk_gender = :genderId)",
                    "genderId", genderId);
        }
        if (disabilityId > 0) {
            sql.and("exists (select 1 from user_profiles dp join user_profile_disabilities upd "
                    + "on upd.fk_user_profile = dp.id where dp.fk_user = u.id and upd.fk_disability = :disabilityId)",
                    "disabilityId", disabilityId);
        }
        return sql;
    }

    /** Total de contas no fim de cada grupo: base antes do período + cadastros acumulados. */
    static DashboardSeries cumulative(String key, long base, DashboardSeries increments) {
        List<DashboardPoint> points = new ArrayList<>(increments.points().size());
        double running = base;
        for (DashboardPoint point : increments.points()) {
            running += point.value();
            points.add(new DashboardPoint(point.bucket(), running));
        }
        return new DashboardSeries(key, points);
    }

    private List<DashboardSlice> ageGroups(Sql filter) {
        Map<String, Long> counts = new LinkedHashMap<>();
        AGE_GROUPS.forEach(group -> counts.put(group, 0L));
        rows("select case when t.age < 18 then 'under_18' when t.age < 25 then '18_24' when t.age < 35 then '25_34' "
                + "when t.age < 45 then '35_44' when t.age < 60 then '45_59' else '60_plus' end, count(*) "
                + "from (select date_part('year', age(current_date, u.birthdate)) as age from users u where 1 = 1"
                + filter.where() + ") t group by 1", filter)
                .forEach(row -> counts.put(text(row[0]), count(row[1])));
        return counts.entrySet().stream().map(entry -> DashboardSlice.of(entry.getKey(), entry.getValue())).toList();
    }

    private List<DashboardSlice> byDisability(Sql filter) {
        List<DashboardSlice> slices = new ArrayList<>(rows("select d.id, d.description, count(distinct u.id) "
                + "from users u join user_profiles p on p.fk_user = u.id "
                + "join user_profile_disabilities upd on upd.fk_user_profile = p.id "
                + "join disabilities d on d.id = upd.fk_disability where 1 = 1" + filter.where()
                + " group by d.id, d.description order by 3 desc, 2", filter).stream()
                .map(row -> new DashboardSlice(text(row[0]), text(row[1]), count(row[2]), null))
                .toList());
        long none = count(at(row("select count(*) from users u where not exists (select 1 from user_profiles p "
                + "join user_profile_disabilities upd on upd.fk_user_profile = p.id where p.fk_user = u.id)"
                + filter.where(), filter), 0));
        slices.add(DashboardSlice.of("none", none));
        return slices;
    }

    private List<DashboardSlice> byGender(Sql filter) {
        return rows("select g.id, g.description, count(*) from users u "
                + "left join user_profiles p on p.fk_user = u.id left join genders g on g.id = p.fk_gender "
                + "where 1 = 1" + filter.where() + " group by g.id, g.description order by 3 desc, 2", filter).stream()
                .map(row -> new DashboardSlice(row[0] == null ? "none" : text(row[0]), text(row[1]), count(row[2]), null))
                .toList();
    }

    private List<CohortRow> cohorts(DashboardWindow window, Sql filter) {
        LocalDate thisWeek = window.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeek = thisWeek.minusWeeks(COHORT_WEEKS - 1L);
        Sql cohort = filter.copy().param("cohortStart", firstWeek.atStartOfDay(DashboardWindow.LOCAL_ZONE).toInstant());
        String cohortWeek = "cast(date_trunc('week', u.created_at at time zone '" + DashboardWindow.LOCAL_ZONE.getId()
                + "') as date)";

        Map<LocalDate, Long> sizes = new HashMap<>();
        rows("select " + cohortWeek + ", count(*) from users u where u.created_at >= :cohortStart" + cohort.where()
                + " group by 1", cohort)
                .forEach(row -> sizes.put(AdminDashboardQueries.toLocalDate(row[0]), count(row[1])));

        Map<LocalDate, Map<Integer, Long>> active = new HashMap<>();
        rows("select " + cohortWeek + ", (a.activity_date - " + cohortWeek + ") / 7, count(distinct u.id) "
                + "from users u join user_activity_days a on a.fk_user = u.id "
                + "where u.created_at >= :cohortStart and a.activity_date >= " + cohortWeek + " + 7"
                + cohort.where() + " group by 1, 2", cohort)
                .forEach(row -> active.computeIfAbsent(AdminDashboardQueries.toLocalDate(row[0]), key -> new HashMap<>())
                        .put((int) count(row[1]), count(row[2])));

        List<CohortRow> rows = new ArrayList<>(COHORT_WEEKS);
        for (LocalDate week = firstWeek; !week.isAfter(thisWeek); week = week.plusWeeks(1)) {
            int elapsedWeeks = (int) ChronoUnit.WEEKS.between(week, thisWeek);
            Map<Integer, Long> byOffset = active.getOrDefault(week, Map.of());
            List<Long> activeByWeek = new ArrayList<>(elapsedWeeks);
            for (int offset = 1; offset <= elapsedWeeks; offset++) {
                activeByWeek.add(byOffset.getOrDefault(offset, 0L));
            }
            rows.add(new CohortRow(week, sizes.getOrDefault(week, 0L), activeByWeek));
        }
        return rows;
    }
}
