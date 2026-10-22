package br.com.unify.matchable.admin.services;

import java.util.Locale;
import java.util.Set;

import br.com.unify.matchable.admin.dto.DashboardSummaryResponse;
import br.com.unify.matchable.admin.dto.TimeSeriesResponse;
import br.com.unify.matchable.admin.dto.TopCommunitiesResponse;
import br.com.unify.matchable.admin.dto.dashboard.CommunityDashboardResponse;
import br.com.unify.matchable.admin.dto.dashboard.GrowthDashboardResponse;
import br.com.unify.matchable.admin.dto.dashboard.MatchDashboardResponse;
import br.com.unify.matchable.admin.dto.dashboard.PostDashboardResponse;
import br.com.unify.matchable.admin.enums.DashboardGranularity;
import br.com.unify.matchable.admin.enums.DashboardMetric;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Valida e normaliza os parâmetros; as contagens (e o cache) ficam em
 * {@link AdminDashboardQueries}. Parâmetro inválido nunca chega ao cache.
 */
@ApplicationScoped
public class AdminDashboardServiceImplementation implements AdminDashboardService {

    static final int DEFAULT_DAYS = 30;
    static final int MIN_DAYS = 1;
    static final int MAX_DAYS = 180;
    static final int DEFAULT_TOP_LIMIT = 5;
    static final int MAX_TOP_LIMIT = 50;

    static final String DAYS_OUT_OF_RANGE_MESSAGE =
            "O período deve ter entre " + MIN_DAYS + " e " + MAX_DAYS + " dias";
    static final String LIMIT_OUT_OF_RANGE_MESSAGE =
            "O limite deve ficar entre 1 e " + MAX_TOP_LIMIT;
    static final int DEFAULT_INACTIVITY_DAYS = 30;
    static final String GRANULARITY_INVALID_MESSAGE = "Agrupamento inválido. Use DAY, WEEK ou MONTH";
    static final String INACTIVITY_OUT_OF_RANGE_MESSAGE =
            "A janela de inatividade deve ter entre " + MIN_DAYS + " e " + MAX_DAYS + " dias";
    static final String FILTER_ID_INVALID_MESSAGE = "Filtro inválido: o identificador deve ser positivo";
    static final String ORIGIN_INVALID_MESSAGE = "Origem inválida. Use ALL, PERSONAL ou COMMUNITY";
    static final String PRIVACY_INVALID_MESSAGE = "Privacidade inválida. Use ALL, PUBLIC ou PRIVATE";
    static final String CATEGORY_WITH_PERSONAL_MESSAGE =
            "A categoria de comunidade não se aplica a publicações pessoais";

    @Inject
    GrowthDashboardQueries growthQueries;

    @Inject
    MatchDashboardQueries matchQueries;

    @Inject
    PostDashboardQueries postQueries;

    @Inject
    CommunityDashboardQueries communityQueries;

    @Inject
    AdminDashboardQueries queries;

    @Override
    public DashboardSummaryResponse getSummary() {
        return queries.summary();
    }

    @Override
    public TimeSeriesResponse getTimeSeries(String metric, Integer days) {
        DashboardMetric resolved = DashboardMetric.fromCode(metric).orElseThrow(() -> new IllegalArgumentException(
                "Métrica inválida: " + metric + ". Use " + DashboardMetric.describeCodes()));
        int resolvedDays = days == null ? DEFAULT_DAYS : days;
        if (resolvedDays < MIN_DAYS || resolvedDays > MAX_DAYS) {
            throw new IllegalArgumentException(DAYS_OUT_OF_RANGE_MESSAGE);
        }
        return queries.timeSeries(resolved, resolvedDays);
    }

    @Override
    public TopCommunitiesResponse getTopCommunities(Integer limit) {
        int resolvedLimit = limit == null ? DEFAULT_TOP_LIMIT : limit;
        if (resolvedLimit < 1 || resolvedLimit > MAX_TOP_LIMIT) {
            throw new IllegalArgumentException(LIMIT_OUT_OF_RANGE_MESSAGE);
        }
        return queries.topCommunities(resolvedLimit);
    }

    // ------------------------------------------------- painéis por dimensão

    @Override
    public GrowthDashboardResponse getGrowth(Integer days, String granularity, Integer inactivityDays,
                                             Integer genderId, Integer disabilityId) {
        int resolvedDays = resolveDays(days);
        int resolvedInactivity = inactivityDays == null ? DEFAULT_INACTIVITY_DAYS : inactivityDays;
        if (resolvedInactivity < MIN_DAYS || resolvedInactivity > MAX_DAYS) {
            throw new IllegalArgumentException(INACTIVITY_OUT_OF_RANGE_MESSAGE);
        }
        return growthQueries.growth(resolvedDays, resolveGranularity(granularity, resolvedDays), resolvedInactivity,
                resolveId(genderId), resolveId(disabilityId));
    }

    @Override
    public MatchDashboardResponse getMatches(Integer days, String granularity, Integer connectionTypeId) {
        int resolvedDays = resolveDays(days);
        return matchQueries.matches(resolvedDays, resolveGranularity(granularity, resolvedDays), resolveId(connectionTypeId));
    }

    @Override
    public PostDashboardResponse getPosts(Integer days, String granularity, String origin, Integer categoryId) {
        int resolvedDays = resolveDays(days);
        String resolvedOrigin = resolveChoice(origin, Set.of("ALL", "PERSONAL", "COMMUNITY"), ORIGIN_INVALID_MESSAGE);
        int resolvedCategory = resolveId(categoryId);
        if (resolvedCategory > 0 && "PERSONAL".equals(resolvedOrigin)) {
            throw new IllegalArgumentException(CATEGORY_WITH_PERSONAL_MESSAGE);
        }
        return postQueries.posts(resolvedDays, resolveGranularity(granularity, resolvedDays), resolvedOrigin, resolvedCategory);
    }

    @Override
    public CommunityDashboardResponse getCommunities(Integer days, String granularity, Integer categoryId, String privacy) {
        int resolvedDays = resolveDays(days);
        return communityQueries.communities(resolvedDays, resolveGranularity(granularity, resolvedDays),
                resolveId(categoryId), resolveChoice(privacy, Set.of("ALL", "PUBLIC", "PRIVATE"), PRIVACY_INVALID_MESSAGE));
    }

    private static int resolveDays(Integer days) {
        int resolved = days == null ? DEFAULT_DAYS : days;
        if (resolved < MIN_DAYS || resolved > MAX_DAYS) {
            throw new IllegalArgumentException(DAYS_OUT_OF_RANGE_MESSAGE);
        }
        return resolved;
    }

    private static DashboardGranularity resolveGranularity(String value, int days) {
        if (value == null || value.isBlank()) {
            return DashboardGranularity.defaultFor(days);
        }
        return DashboardGranularity.parse(value).orElseThrow(() -> new IllegalArgumentException(GRANULARITY_INVALID_MESSAGE));
    }

    /** 0 = sem filtro (o cache não aceita chave nula). */
    private static int resolveId(Integer id) {
        if (id == null) {
            return 0;
        }
        if (id <= 0) {
            throw new IllegalArgumentException(FILTER_ID_INVALID_MESSAGE);
        }
        return id;
    }

    private static String resolveChoice(String value, Set<String> allowed, String message) {
        if (value == null || value.isBlank()) {
            return "ALL";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
