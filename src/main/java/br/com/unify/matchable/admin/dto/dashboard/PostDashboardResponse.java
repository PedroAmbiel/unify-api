package br.com.unify.matchable.admin.dto.dashboard;

import java.util.List;

/** Dimensão publicações (pessoais e de comunidade). */
public record PostDashboardResponse(
        DashboardRange range,
        List<DashboardKpi> kpis,
        List<DashboardSeries> series,
        List<DashboardHeatCell> heatmap,
        List<DashboardSlice> status,
        List<DashboardSlice> byCategory,
        List<PostRankingRow> topPosts
) {
}
