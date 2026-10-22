package br.com.unify.matchable.admin.dto.dashboard;

import java.util.List;

/** Dimensão comunidades (só comunidades ativas nas distribuições e rankings). */
public record CommunityDashboardResponse(
        DashboardRange range,
        List<DashboardKpi> kpis,
        List<DashboardSeries> series,
        List<DashboardSlice> byCategory,
        List<DashboardSlice> sizeDistribution,
        List<CommunityPoint> scatter,
        List<CommunityRankingRow> ranking
) {
}
