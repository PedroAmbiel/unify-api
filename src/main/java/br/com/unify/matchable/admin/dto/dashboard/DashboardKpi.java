package br.com.unify.matchable.admin.dto.dashboard;

/**
 * Indicador com o valor do período atual e do anterior (tendência).
 *
 * @param current  nulo quando não há base para calcular (ex.: taxa sem denominador).
 * @param previous nulo em indicadores de "situação atual", sem comparação.
 * @param unit     COUNT, PERCENT (0..100), AVERAGE ou HOURS.
 */
public record DashboardKpi(
        String key,
        Double current,
        Double previous,
        String unit
) {

    public static final String COUNT = "COUNT";
    public static final String PERCENT = "PERCENT";
    public static final String AVERAGE = "AVERAGE";
    public static final String HOURS = "HOURS";
}
