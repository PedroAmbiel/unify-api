package br.com.unify.matchable.admin.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.dto.TimeSeriesResponse.TimeSeriesPoint;
import br.com.unify.matchable.admin.enums.DashboardMetric;

/** Semana 07 — regras puras do dashboard (sem banco). */
class AdminDashboardQueriesTest {

    @Test
    void fillDaysCompletesMissingDaysWithZeroInChronologicalOrder() {
        LocalDate first = LocalDate.of(2026, 2, 27);
        List<TimeSeriesPoint> points = AdminDashboardQueries.fillDays(first, 4, Map.of(
                LocalDate.of(2026, 2, 28), 5L,
                LocalDate.of(2026, 3, 2), 2L,
                LocalDate.of(2026, 3, 9), 99L));

        assertEquals(List.of(
                new TimeSeriesPoint(LocalDate.of(2026, 2, 27), 0L),
                new TimeSeriesPoint(LocalDate.of(2026, 2, 28), 5L),
                new TimeSeriesPoint(LocalDate.of(2026, 3, 1), 0L),
                new TimeSeriesPoint(LocalDate.of(2026, 3, 2), 2L)), points);
    }

    @Test
    void toLocalDateAcceptsTheTypesTheDriverMayReturn() {
        LocalDate expected = LocalDate.of(2026, 10, 22);
        assertEquals(expected, AdminDashboardQueries.toLocalDate(expected));
        assertEquals(expected, AdminDashboardQueries.toLocalDate(java.sql.Date.valueOf(expected)));
        assertEquals(expected, AdminDashboardQueries.toLocalDate("2026-10-22"));
    }

    @Test
    void metricCodesAreCaseInsensitiveAndListedInErrors() {
        assertEquals(DashboardMetric.NEW_USERS, DashboardMetric.fromCode(" NEW_users ").orElseThrow());
        assertTrue(DashboardMetric.fromCode("users").isEmpty());
        assertTrue(DashboardMetric.fromCode(null).isEmpty());
        assertEquals("new_users, new_mutual_matches, messages_sent, posts_created ou reports_opened",
                DashboardMetric.describeCodes());
    }
}
