package br.com.unify.matchable.admin.dto.dashboard;

import java.util.List;

/**
 * Dimensão crescimento: cadastros, uso e situação das contas (só contas de
 * usuário; administradores ficam de fora). "Ativa" = usou o app nos últimos
 * {@code inactivityDays} dias.
 */
public record GrowthDashboardResponse(
        DashboardRange range,
        int inactivityDays,
        List<DashboardKpi> kpis,
        List<DashboardSeries> series,
        List<DashboardSlice> accountStatus,
        List<DashboardSlice> byAgeGroup,
        List<DashboardSlice> byDisability,
        List<DashboardSlice> byGender,
        List<CohortRow> cohorts
) {
}
