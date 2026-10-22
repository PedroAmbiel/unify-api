package br.com.unify.matchable.admin.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.dto.dashboard.DashboardPoint;
import br.com.unify.matchable.admin.dto.dashboard.DashboardSeries;
import br.com.unify.matchable.admin.enums.DashboardGranularity;

/** Semana 07 (ajuste) — regras puras dos painéis por dimensão. */
class DashboardWindowTest {

    /** 22/10/2026 10:00 em Brasília (quinta-feira). */
    private static final Instant NOW = Instant.parse("2026-10-22T13:00:00Z");

    @Test
    void windowComparesWithThePreviousPeriodOfTheSameSize() {
        DashboardWindow window = DashboardWindow.of(7, DashboardGranularity.DAY, NOW);
        assertEquals(LocalDate.of(2026, 10, 22), window.today());
        assertEquals(LocalDate.of(2026, 10, 16), window.start());
        assertEquals(LocalDate.of(2026, 10, 9), window.previousStart());
        assertEquals(LocalDate.of(2026, 10, 15), window.previousEnd());
        assertEquals(Instant.parse("2026-10-16T03:00:00Z"), window.startInstant(), "meia-noite de Brasília");
        assertEquals(7, window.buckets().size());
    }

    @Test
    void localDayUsesBrasiliaNotUtc() {
        // 01:30 UTC do dia 23 ainda é dia 22 em Brasília
        DashboardWindow window = DashboardWindow.of(1, DashboardGranularity.DAY, Instant.parse("2026-10-23T01:30:00Z"));
        assertEquals(LocalDate.of(2026, 10, 22), window.today());
    }

    @Test
    void weeklyAndMonthlyBucketsStartOnMondayAndOnTheFirstDay() {
        List<LocalDate> weeks = DashboardWindow.of(30, DashboardGranularity.WEEK, NOW).buckets();
        assertEquals(LocalDate.of(2026, 9, 21), weeks.get(0), "segunda da semana que contém 23/09");
        assertEquals(LocalDate.of(2026, 10, 19), weeks.get(weeks.size() - 1));
        assertEquals(5, weeks.size());

        List<LocalDate> months = DashboardWindow.of(90, DashboardGranularity.MONTH, NOW).buckets();
        assertEquals(List.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1)), months);
    }

    @Test
    void seriesFillsEmptyBucketsWithZero() {
        DashboardWindow window = DashboardWindow.of(3, DashboardGranularity.DAY, NOW);
        DashboardSeries series = window.series("x", Map.of(LocalDate.of(2026, 10, 21), 4d));
        assertEquals(List.of(0d, 4d, 0d), series.points().stream().map(DashboardPoint::value).toList());
    }

    @Test
    void cumulativeSeriesStartsFromTheBaseBeforeThePeriod() {
        DashboardWindow window = DashboardWindow.of(3, DashboardGranularity.DAY, NOW);
        DashboardSeries increments = window.series("new", Map.of(LocalDate.of(2026, 10, 20), 2d, LocalDate.of(2026, 10, 22), 1d));
        DashboardSeries total = GrowthDashboardQueries.cumulative("total", 10, increments);
        assertEquals(List.of(12d, 12d, 13d), total.points().stream().map(DashboardPoint::value).toList());
    }

    @Test
    void granularityDefaultsAndParsing() {
        assertEquals(DashboardGranularity.DAY, DashboardGranularity.defaultFor(30));
        assertEquals(DashboardGranularity.WEEK, DashboardGranularity.defaultFor(90));
        assertEquals(DashboardGranularity.WEEK, DashboardGranularity.defaultFor(180));
        assertEquals(DashboardGranularity.MONTH, DashboardGranularity.parse(" month ").orElseThrow());
        assertTrue(DashboardGranularity.parse("hour").isEmpty());
    }

    @Test
    void communitySizeBuckets() {
        assertEquals("1", CommunityDashboardQueries.sizeBucket(1));
        assertEquals("2_5", CommunityDashboardQueries.sizeBucket(5));
        assertEquals("6_10", CommunityDashboardQueries.sizeBucket(6));
        assertEquals("11_25", CommunityDashboardQueries.sizeBucket(25));
        assertEquals("26_plus", CommunityDashboardQueries.sizeBucket(26));
    }
}
