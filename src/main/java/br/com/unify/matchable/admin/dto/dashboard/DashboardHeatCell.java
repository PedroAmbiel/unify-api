package br.com.unify.matchable.admin.dto.dashboard;

/** Célula do mapa de calor: {@code weekday} 1 = segunda .. 7 = domingo, {@code hour} 0..23 (Brasília). */
public record DashboardHeatCell(int weekday, int hour, long value) {
}
