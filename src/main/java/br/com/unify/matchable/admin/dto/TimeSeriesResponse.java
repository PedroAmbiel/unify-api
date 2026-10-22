package br.com.unify.matchable.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Série diária de uma métrica do dashboard: exatamente {@code days} pontos,
 * do mais antigo ao mais recente, dia civil em America/Sao_Paulo. Dia sem
 * evento vem com {@code value = 0} (nunca omitido).
 */
public record TimeSeriesResponse(
        String metric,
        int days,
        List<TimeSeriesPoint> points
) {

    public record TimeSeriesPoint(LocalDate date, long value) {
    }
}
