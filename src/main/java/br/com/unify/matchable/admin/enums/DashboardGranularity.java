package br.com.unify.matchable.admin.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

/**
 * Agrupamento das séries do dashboard. Semana começa na segunda-feira (ISO,
 * igual ao {@code date_trunc('week', ...)} do Postgres). O nome da unidade é
 * constante de código e pode compor o SQL.
 */
public enum DashboardGranularity {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String sqlUnit;

    DashboardGranularity(String sqlUnit) {
        this.sqlUnit = sqlUnit;
    }

    public String sqlUnit() {
        return sqlUnit;
    }

    /** Primeiro dia do grupo que contém {@code day}. */
    public LocalDate bucketStart(LocalDate day) {
        return switch (this) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    public LocalDate next(LocalDate bucketStart) {
        return switch (this) {
            case DAY -> bucketStart.plusDays(1);
            case WEEK -> bucketStart.plusWeeks(1);
            case MONTH -> bucketStart.plusMonths(1);
        };
    }

    public static Optional<DashboardGranularity> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Padrão por período: dia até 30 dias, semana até 180, mês acima. */
    public static DashboardGranularity defaultFor(int days) {
        if (days <= 30) {
            return DAY;
        }
        return days <= 180 ? WEEK : MONTH;
    }
}
