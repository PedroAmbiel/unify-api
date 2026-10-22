package br.com.unify.matchable.admin.services;

import br.com.unify.matchable.admin.dto.DashboardSummaryResponse;
import br.com.unify.matchable.admin.dto.TimeSeriesResponse;
import br.com.unify.matchable.admin.dto.TopCommunitiesResponse;
import br.com.unify.matchable.admin.dto.dashboard.CommunityDashboardResponse;
import br.com.unify.matchable.admin.dto.dashboard.GrowthDashboardResponse;
import br.com.unify.matchable.admin.dto.dashboard.MatchDashboardResponse;
import br.com.unify.matchable.admin.dto.dashboard.PostDashboardResponse;

/**
 * Métricas somente leitura do backoffice (semana 07). Tudo é COUNT/GROUP BY
 * no banco; o resultado fica 60 s em cache (quarkus-cache), então o painel pode
 * mostrar números com até um minuto de atraso. Parâmetro inválido vira
 * {@link IllegalArgumentException} (400 pelo mapper global).
 */
public interface AdminDashboardService {

    DashboardSummaryResponse getSummary();

    /**
     * @param metric código de {@link br.com.unify.matchable.admin.enums.DashboardMetric}.
     * @param days   1..180; nulo = 30.
     */
    TimeSeriesResponse getTimeSeries(String metric, Integer days);

    /** @param limit 1..50; nulo = 5. */
    TopCommunitiesResponse getTopCommunities(Integer limit);

    // ------------------------------------------------- painéis por dimensão
    // Comum: days 1..180 (nulo = 30); granularity DAY, WEEK ou MONTH (nulo = padrão do período).

    /**
     * @param inactivityDays janela que separa conta ativa de inativa, 1..180 (nulo = 30).
     * @param genderId       opcional: só perfis com este gênero.
     * @param disabilityId   opcional: só perfis que declararam esta deficiência.
     */
    GrowthDashboardResponse getGrowth(Integer days, String granularity, Integer inactivityDays,
                                      Integer genderId, Integer disabilityId);

    /** @param connectionTypeId opcional: só curtidas de quem procura este tipo de conexão. */
    MatchDashboardResponse getMatches(Integer days, String granularity, Integer connectionTypeId);

    /**
     * @param origin     ALL (nulo), PERSONAL ou COMMUNITY.
     * @param categoryId opcional: só posts de comunidades desta categoria (não combina com PERSONAL).
     */
    PostDashboardResponse getPosts(Integer days, String granularity, String origin, Integer categoryId);

    /** @param privacy ALL (nulo), PUBLIC ou PRIVATE. */
    CommunityDashboardResponse getCommunities(Integer days, String granularity, Integer categoryId, String privacy);
}
