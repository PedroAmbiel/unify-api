package br.com.unify.matchable.admin.dto.dashboard;

import java.util.List;

/** Série temporal: um ponto por grupo do período, grupo sem evento = 0. */
public record DashboardSeries(String key, List<DashboardPoint> points) {
}
