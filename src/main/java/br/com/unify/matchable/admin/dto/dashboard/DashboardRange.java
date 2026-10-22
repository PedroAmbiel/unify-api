package br.com.unify.matchable.admin.dto.dashboard;

import java.time.LocalDate;

/**
 * Período de um painel por dimensão: atual ({@code start}..{@code end}, hoje
 * inclusive) e o anterior de mesmo tamanho, usado nas tendências.
 *
 * @param granularity DAY, WEEK ou MONTH.
 */
public record DashboardRange(
        LocalDate start,
        LocalDate end,
        int days,
        String granularity,
        LocalDate previousStart,
        LocalDate previousEnd,
        long generatedAtEpochMillis
) {
}
