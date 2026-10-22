package br.com.unify.matchable.admin.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * Coorte semanal de cadastros: quantas pessoas da semana {@code cohortStart}
 * usaram o app em cada semana seguinte ({@code activeByWeek[0]} = semana 1).
 * Só traz as semanas que já começaram.
 */
public record CohortRow(LocalDate cohortStart, long size, List<Long> activeByWeek) {
}
