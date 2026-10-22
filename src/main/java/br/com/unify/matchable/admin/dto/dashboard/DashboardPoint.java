package br.com.unify.matchable.admin.dto.dashboard;

import java.time.LocalDate;

/** Valor de um grupo (dia, semana ou mês) de uma série; {@code bucket} = primeiro dia do grupo. */
public record DashboardPoint(LocalDate bucket, double value) {
}
