package br.com.unify.matchable.admin.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.com.unify.matchable.admin.dto.dashboard.DashboardPoint;
import br.com.unify.matchable.admin.dto.dashboard.DashboardRange;
import br.com.unify.matchable.admin.dto.dashboard.DashboardSeries;
import br.com.unify.matchable.admin.enums.DashboardGranularity;

/**
 * Período analisado e o período anterior de mesmo tamanho (base das
 * tendências "vs período anterior"). Dias civis de America/Sao_Paulo: o
 * período atual vai de {@code start} até hoje (inclusive); o anterior são os
 * {@code days} dias imediatamente antes.
 */
public record DashboardWindow(
        int days,
        DashboardGranularity granularity,
        LocalDate today,
        LocalDate start,
        LocalDate previousStart,
        Instant now,
        Instant startInstant,
        Instant previousStartInstant
) {

    public static final ZoneId LOCAL_ZONE = ZoneId.of("America/Sao_Paulo");

    public static DashboardWindow of(int days, DashboardGranularity granularity, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, LOCAL_ZONE);
        LocalDate start = today.minusDays(days - 1L);
        LocalDate previousStart = start.minusDays(days);
        return new DashboardWindow(days, granularity, today, start, previousStart, now,
                start.atStartOfDay(LOCAL_ZONE).toInstant(),
                previousStart.atStartOfDay(LOCAL_ZONE).toInstant());
    }

    public LocalDate previousEnd() {
        return start.minusDays(1);
    }

    /** Início de cada grupo do período atual, do mais antigo ao mais recente. */
    public List<LocalDate> buckets() {
        List<LocalDate> buckets = new ArrayList<>();
        LocalDate last = granularity.bucketStart(today);
        for (LocalDate bucket = granularity.bucketStart(start); !bucket.isAfter(last); bucket = granularity.next(bucket)) {
            buckets.add(bucket);
        }
        return buckets;
    }

    /** Série com um ponto por grupo; grupo sem valor = 0. */
    public DashboardSeries series(String key, Map<LocalDate, Double> values) {
        List<DashboardPoint> points = buckets().stream()
                .map(bucket -> new DashboardPoint(bucket, values.getOrDefault(bucket, 0d)))
                .toList();
        return new DashboardSeries(key, points);
    }

    public DashboardRange toRange() {
        return new DashboardRange(start, today, days, granularity.name(), previousStart, previousEnd(),
                now.toEpochMilli());
    }

    /** Grupo de uma coluna timestamptz, já no dia civil local. */
    public String bucketOfInstant(String column) {
        return "cast(date_trunc('" + granularity.sqlUnit() + "', " + column + " at time zone '"
                + LOCAL_ZONE.getId() + "') as date)";
    }

    /** Grupo de uma coluna date (já é dia civil local). */
    public String bucketOfDate(String column) {
        return "cast(date_trunc('" + granularity.sqlUnit() + "', cast(" + column + " as timestamp)) as date)";
    }

    /** Dia civil local de uma coluna timestamptz. */
    public static String localDate(String column) {
        return "cast(" + column + " at time zone '" + LOCAL_ZONE.getId() + "' as date)";
    }
}
