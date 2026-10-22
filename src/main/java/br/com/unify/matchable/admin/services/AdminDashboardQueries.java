package br.com.unify.matchable.admin.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.unify.matchable.admin.dto.DashboardSummaryResponse;
import br.com.unify.matchable.admin.dto.DashboardSummaryResponse.ChatSummary;
import br.com.unify.matchable.admin.dto.DashboardSummaryResponse.CommunitiesSummary;
import br.com.unify.matchable.admin.dto.DashboardSummaryResponse.FollowsSummary;
import br.com.unify.matchable.admin.dto.DashboardSummaryResponse.InteractionsSummary;
import br.com.unify.matchable.admin.dto.DashboardSummaryResponse.MatchesSummary;
import br.com.unify.matchable.admin.dto.DashboardSummaryResponse.PostsSummary;
import br.com.unify.matchable.admin.dto.DashboardSummaryResponse.ReportsSummary;
import br.com.unify.matchable.admin.dto.DashboardSummaryResponse.UsersSummary;
import br.com.unify.matchable.admin.dto.TimeSeriesResponse;
import br.com.unify.matchable.admin.dto.TimeSeriesResponse.TimeSeriesPoint;
import br.com.unify.matchable.admin.dto.TopCommunitiesResponse;
import br.com.unify.matchable.admin.dto.TopCommunitiesResponse.TopCommunityByMembers;
import br.com.unify.matchable.admin.dto.TopCommunitiesResponse.TopCommunityByPosts;
import br.com.unify.matchable.admin.enums.DashboardMetric;
import br.com.unify.matchable.moderation.enums.UserReportStatus;
import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.user.enums.UserRole;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

/**
 * Consultas agregadas do dashboard, atrás do cache de 60 s. Recebe parâmetros
 * já validados e normalizados pelo {@link AdminDashboardServiceImplementation}:
 * o cache do Quarkus não aceita chave nula, e "NEW_USERS"/"new_users" precisam
 * cair na mesma entrada.
 */
@ApplicationScoped
public class AdminDashboardQueries {

    /**
     * "Dia" = dia civil de Brasília, não de UTC: sem a conversão, eventos entre
     * 21h e 23h59 cairiam no dia seguinte do gráfico. O Brasil não tem horário
     * de verão desde 2019; vários fusos exigiriam parâmetro por usuário.
     */
    static final ZoneId LOCAL_ZONE = ZoneId.of("America/Sao_Paulo");

    @Inject
    EntityManager entityManager;

    @Transactional
    @CacheResult(cacheName = "admin-dashboard-summary")
    public DashboardSummaryResponse summary() {
        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        UsersSummary users = new UsersSummary(
                count("select count(u) from User u"),
                count("select count(u) from User u where u.verified = true"),
                count("select count(u) from User u where u.banned = true"),
                count("select count(u) from User u where u.role = :role", "role", UserRole.ADMIN),
                count("select count(u) from User u where u.createdAt >= :since", "since", sevenDaysAgo),
                count("select count(u) from User u where u.createdAt >= :since", "since", thirtyDaysAgo),
                count("select count(a) from UserProfileAudio a"));

        MatchesSummary matches = new MatchesSummary(
                count("select count(m) from UserPossibleMatch m"),
                count("select count(m) from UserPossibleMatch m "
                        + "where m.starterAccepted = true and m.pendingAccepted = true"),
                count("select count(m) from UserPossibleMatch m "
                        + "where m.starterAccepted = true and m.pendingAccepted = true and m.matchedAt >= :since",
                        "since", sevenDaysAgo));

        ChatSummary chat = new ChatSummary(
                count("select count(c) from Conversation c"),
                count("select count(m) from ChatMessage m"),
                count("select count(m) from ChatMessage m where m.createdAt >= :since", "since", sevenDaysAgo));

        CommunitiesSummary communities = new CommunitiesSummary(
                count("select count(c) from Community c"),
                count("select count(c) from Community c where c.active = true"),
                count("select count(m) from CommunityMembership m where m.community.active = true"));

        PostsSummary posts = new PostsSummary(
                count("select count(p) from Post p where p.origin = :origin and p.active = true",
                        "origin", PostOrigin.COMMUNITY),
                count("select count(p) from Post p where p.origin = :origin and p.active = true",
                        "origin", PostOrigin.PERSONAL),
                count("select count(p) from Post p where p.moderatedAt is not null"));

        return new DashboardSummaryResponse(
                users,
                matches,
                chat,
                communities,
                posts,
                buildInteractions(),
                buildReports(),
                new FollowsSummary(count("select count(f) from UserFollow f")),
                now.toEpochMilli());
    }

    /** Chave = (métrica, dias); sem {@code @CacheKey}, os dois parâmetros entram na chave. */
    @Transactional
    @CacheResult(cacheName = "admin-dashboard-timeseries")
    public TimeSeriesResponse timeSeries(DashboardMetric metric, int days) {
        LocalDate today = LocalDate.now(LOCAL_ZONE);
        LocalDate firstDay = today.minusDays(days - 1L);
        Instant since = firstDay.atStartOfDay(LOCAL_ZONE).toInstant();

        // Filtra por intervalo (usa o índice da coluna) e só depois agrupa pelo
        // dia local; os dias sem evento são completados em Java.
        String column = metric.instantColumn();
        String sql = "select cast((" + column + " at time zone '" + LOCAL_ZONE.getId() + "') as date) as day, "
                + "count(*) as total from " + metric.table()
                + " where " + column + " >= :since"
                + (metric.extraFilter() == null ? "" : " and " + metric.extraFilter())
                + " group by 1";

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("since", since)
                .getResultList();

        Map<LocalDate, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }
        return new TimeSeriesResponse(metric.code(), days, fillDays(firstDay, days, counts));
    }

    @Transactional
    @CacheResult(cacheName = "admin-dashboard-top-communities")
    public TopCommunitiesResponse topCommunities(int limit) {
        // Mesmas regras de contagem do app: conta banida não conta como membro
        // nem como autora (semana 06). Projeção escalar, sem hidratar Community.
        List<TopCommunityByMembers> byMembers = entityManager.createQuery(
                        "select c.id, c.name, count(m) from CommunityMembership m "
                                + "join m.community c join m.userProfile profile join profile.user u "
                                + "where c.active = true and u.banned = false "
                                + "group by c.id, c.name order by count(m) desc, c.name asc",
                        Object[].class)
                .setMaxResults(limit)
                .getResultList().stream()
                .map(row -> new TopCommunityByMembers((UUID) row[0], (String) row[1], ((Number) row[2]).longValue()))
                .toList();

        List<TopCommunityByPosts> byPosts = entityManager.createQuery(
                        "select c.id, c.name, count(p) from Post p join p.community c join p.author a "
                                + "where p.origin = :origin and p.active = true and c.active = true and a.banned = false "
                                + "group by c.id, c.name order by count(p) desc, c.name asc",
                        Object[].class)
                .setParameter("origin", PostOrigin.COMMUNITY)
                .setMaxResults(limit)
                .getResultList().stream()
                .map(row -> new TopCommunityByPosts((UUID) row[0], (String) row[1], ((Number) row[2]).longValue()))
                .toList();

        return new TopCommunitiesResponse(byMembers, byPosts);
    }

    // -------------------------------------------------------------------- apoio

    /** Um ponto por dia, do mais antigo ao mais recente; dia ausente = 0. */
    static List<TimeSeriesPoint> fillDays(LocalDate firstDay, int days, Map<LocalDate, Long> counts) {
        List<TimeSeriesPoint> points = new ArrayList<>(days);
        for (int offset = 0; offset < days; offset++) {
            LocalDate day = firstDay.plusDays(offset);
            points.add(new TimeSeriesPoint(day, counts.getOrDefault(day, 0L)));
        }
        return points;
    }

    /** O driver devolve {@code java.sql.Date} ou {@code LocalDate} conforme a versão do Hibernate. */
    static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private InteractionsSummary buildInteractions() {
        // Tabela única de posts: curtidas e comentários separados pela origem do
        // post. Comentário apagado pelo autor (soft delete, V20) não conta.
        long communityLikes = count("select count(l) from PostLike l where l.post.origin = :origin",
                "origin", PostOrigin.COMMUNITY);
        long communityComments = count("select count(c) from PostComment c where c.post.origin = :origin and c.active = true",
                "origin", PostOrigin.COMMUNITY);
        long personalLikes = count("select count(l) from PostLike l where l.post.origin = :origin",
                "origin", PostOrigin.PERSONAL);
        long personalComments = count("select count(c) from PostComment c where c.post.origin = :origin and c.active = true",
                "origin", PostOrigin.PERSONAL);

        return new InteractionsSummary(communityLikes, communityComments, personalLikes, personalComments,
                communityLikes + communityComments + personalLikes + personalComments);
    }

    private ReportsSummary buildReports() {
        Map<UserReportStatus, Long> byStatus = new EnumMap<>(UserReportStatus.class);
        entityManager.createQuery("select r.status, count(r) from UserReport r group by r.status", Object[].class)
                .getResultList()
                .forEach(row -> byStatus.put((UserReportStatus) row[0], ((Number) row[1]).longValue()));

        long open = byStatus.getOrDefault(UserReportStatus.OPEN, 0L);
        long underReview = byStatus.getOrDefault(UserReportStatus.UNDER_REVIEW, 0L);
        long resolved = byStatus.getOrDefault(UserReportStatus.RESOLVED, 0L);
        long dismissed = byStatus.getOrDefault(UserReportStatus.DISMISSED, 0L);
        long pendingPostReports = count(
                "select count(r) from UserReport r where r.reportedPost is not null and r.status in :statuses",
                "statuses", UserReportStatus.PENDING);

        return new ReportsSummary(open, underReview, resolved, dismissed, open + underReview, pendingPostReports,
                open + underReview + resolved + dismissed);
    }

    /** JPQL escalar: o Hibernate gera SELECT COUNT(*) puro, sem instanciar entidades. */
    private long count(String jpql) {
        return entityManager.createQuery(jpql, Long.class).getSingleResult();
    }

    private long count(String jpql, String parameter, Object value) {
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        query.setParameter(parameter, value);
        return query.getSingleResult();
    }
}
