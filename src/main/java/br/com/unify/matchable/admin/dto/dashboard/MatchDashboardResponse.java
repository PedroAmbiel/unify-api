package br.com.unify.matchable.admin.dto.dashboard;

import java.util.List;

/**
 * Dimensão matches: curtidas, matches mútuos, recusas e conversas.
 * {@code funnel} e {@code outcomes} acompanham as curtidas feitas no período.
 */
public record MatchDashboardResponse(
        DashboardRange range,
        List<DashboardKpi> kpis,
        List<DashboardSeries> series,
        List<FunnelStep> funnel,
        List<DashboardSlice> outcomes,
        List<DashboardSlice> timeToMatch,
        List<DashboardSlice> byConnectionType
) {
}
