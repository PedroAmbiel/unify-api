package br.com.unify.matchable.social.services;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.services.ScoringWeights;
import br.com.unify.matchable.social.enums.FeedSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Ranking do feed da aba Início ({@code GET /users/feed}).
 *
 * <p>Objetivo de produto: o feed nunca fica vazio e ajuda a descobrir pessoas e
 * comunidades sem ir atrás delas. Em vez de listar só quem eu sigo e as
 * comunidades em que participo (ordem cronológica), cada publicação ativa do
 * sistema recebe uma pontuação e a página é a fatia ordenada por essa
 * pontuação. Fontes (ver {@link FeedSource}):
 *
 * <pre>
 * score = peso da fonte                (sigo o autor / sou membro da comunidade)
 *       + afinidade                    (interesses em comum com o autor; categoria,
 *                                       destaque e pessoas que sigo na comunidade)
 *       + engajamento                  (curtidas e comentários, com teto)
 *       + recência                     (decai com a idade da publicação)
 * </pre>
 *
 * <p>Regras fixas de elegibilidade (não são pontuação, são filtro):
 * <ul>
 *   <li>Meus posts pessoais nunca entram (ficam na aba Perfil).</li>
 *   <li>Posts de comunidade só entram se a comunidade está ativa e é PÚBLICA,
 *       ou se eu sou membro dela (aí vale também para privadas).</li>
 *   <li>Privacidade (semana 04): autores bloqueados em qualquer direção
 *       nunca entram ({@link Context#hiddenAuthorUserIds()}); post pessoal
 *       de quem restringiu a seguidores só entra se eu sigo o autor
 *       ({@link #authorVisibilityFilter()}).</li>
 *   <li>Banimento (semana 06): post de conta suspensa nunca entra.</li>
 * </ul>
 *
 * <p>Toda a pontuação é calculada no Postgres (query nativa), então a paginação
 * por offset é consistente entre páginas e o custo por página não cresce com
 * o número de páginas já vistas. Os pesos são as dimensões {@code FEED_*} de
 * {@link ScoringDimension} (administráveis pelo backoffice) e entram na query
 * como parâmetros; mudar um peso não muda o contrato da API nem o SQL.
 *
 * <pre>
 * fonte        FEED_FOLLOWING (post pessoal de quem eu sigo)
 *              FEED_MEMBER_COMMUNITY (comunidade em que sou membro)
 *              FEED_FOLLOWED_AUTHOR_IN_COMMUNITY (pública, não membro, autor que eu sigo)
 * perfil       FEED_SHARED_INTEREST por interesse em comum, até FEED_SHARED_INTERESTS_CAP
 * comunidade   FEED_SAME_CATEGORY + FEED_FEATURED_COMMUNITY
 *              + FEED_FOLLOWED_MEMBER por pessoa que sigo e é membro, até FEED_FOLLOWED_MEMBERS_CAP
 * engajamento  FEED_LIKE por curtida + FEED_COMMENT por comentário, até FEED_ENGAGEMENT_CAP
 * recência     FEED_RECENCY / (1 + idade em horas / FEED_RECENCY_HALF_LIFE_HOURS)
 * </pre>
 */
public final class HomeFeedRankingPolicy {

    private HomeFeedRankingPolicy() {
    }

    /** UUID que nunca existe: mantém {@code in (...)} válido quando a lista vem vazia. */
    static final UUID NO_MATCH_ID = new UUID(0L, 0L);
    /** Id de lookup que nunca existe (lookups começam em 1). */
    static final int NO_MATCH_LOOKUP_ID = -1;

    /**
     * Tudo que o ranking sabe sobre quem está lendo o feed.
     *
     * @param currentUserId       {@code users.id} de quem lê (exclui os próprios posts pessoais).
     * @param followedUserIds     {@code users.id} dos perfis que eu sigo.
     * @param memberCommunityIds  comunidades em que sou membro (públicas ou privadas).
     * @param interestTypeIds     meus {@code interest_types}.
     * @param communityCategoryIds categorias das comunidades em que já participo.
     * @param hiddenAuthorUserIds autores que NUNCA devem aparecer (bloqueados em qualquer direção).
     */
    public record Context(
            UUID currentUserId,
            Collection<UUID> followedUserIds,
            Collection<UUID> memberCommunityIds,
            Collection<Integer> interestTypeIds,
            Collection<Integer> communityCategoryIds,
            Collection<UUID> hiddenAuthorUserIds
    ) {
    }

    /** Uma linha do ranking: o post e por que ele entrou. */
    public record RankedPost(UUID postId, FeedSource source, double score) {
    }

    /**
     * Filtro de visibilidade do AUTOR de post pessoal ({@code feedVisibility} e conta
     * privada — gêmeo de {@code UserPrivacySettings.restrictsPostsToFollowers}).
     * Post de comunidade segue a privacidade da comunidade, não a do autor.
     * {@code not exists} (nunca inner join): quem nunca salvou configuração
     * não tem linha em user_privacy_settings e conta como PUBLIC.
     */
    static String authorVisibilityFilter() {
        return """
                p.origin <> 'PERSONAL'
                or p.fk_user in (:followedUserIds)
                or not exists (
                    select 1 from user_privacy_settings ps
                    where ps.fk_user = p.fk_user
                      and (ps.feed_visibility = 'FOLLOWERS_ONLY' or ps.follow_approval_required = true))
                """;
    }

    /**
     * Executa o ranking e devolve {@code limit} linhas a partir de {@code offset},
     * já na ordem do feed (score desc, mais recente primeiro em empate).
     *
     * @param weights foto dos pesos vigentes ({@code ScoringWeightRegistry.current()}).
     */
    public static List<RankedPost> rank(
            EntityManager entityManager, Context context, ScoringWeights weights, int offset, int limit) {
        Query query = entityManager.createNativeQuery(SQL)
                .setParameter("currentUserId", context.currentUserId())
                .setParameter("followedUserIds", uuidsOrSentinel(context.followedUserIds()))
                .setParameter("memberCommunityIds", uuidsOrSentinel(context.memberCommunityIds()))
                .setParameter("interestTypeIds", lookupsOrSentinel(context.interestTypeIds()))
                .setParameter("communityCategoryIds", lookupsOrSentinel(context.communityCategoryIds()))
                .setParameter("hiddenAuthorUserIds", uuidsOrSentinel(context.hiddenAuthorUserIds()))
                .setFirstResult(offset)
                .setMaxResults(limit);
        bindWeights(query, weights);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new RankedPost(
                        (UUID) row[0],
                        FeedSource.valueOf((String) row[1]),
                        ((Number) row[2]).doubleValue()
                ))
                .toList();
    }

    private static Collection<UUID> uuidsOrSentinel(Collection<UUID> ids) {
        return ids == null || ids.isEmpty() ? List.of(NO_MATCH_ID) : ids;
    }

    private static Collection<Integer> lookupsOrSentinel(Collection<Integer> ids) {
        return ids == null || ids.isEmpty() ? List.of(NO_MATCH_LOOKUP_ID) : ids;
    }

    /** Parâmetro do SQL → dimensão. Os nomes batem com os {@code :parâmetros} de {@link #SQL}. */
    static final Map<String, ScoringDimension> WEIGHT_PARAMETERS = Map.ofEntries(
            Map.entry("weightFollowing", ScoringDimension.FEED_FOLLOWING),
            Map.entry("weightMemberCommunity", ScoringDimension.FEED_MEMBER_COMMUNITY),
            Map.entry("weightFollowedAuthorInCommunity", ScoringDimension.FEED_FOLLOWED_AUTHOR_IN_COMMUNITY),
            Map.entry("weightSharedInterest", ScoringDimension.FEED_SHARED_INTEREST),
            Map.entry("capSharedInterests", ScoringDimension.FEED_SHARED_INTERESTS_CAP),
            Map.entry("weightSameCategory", ScoringDimension.FEED_SAME_CATEGORY),
            Map.entry("weightFeaturedCommunity", ScoringDimension.FEED_FEATURED_COMMUNITY),
            Map.entry("weightFollowedMember", ScoringDimension.FEED_FOLLOWED_MEMBER),
            Map.entry("capFollowedMembers", ScoringDimension.FEED_FOLLOWED_MEMBERS_CAP),
            Map.entry("weightLike", ScoringDimension.FEED_LIKE),
            Map.entry("weightComment", ScoringDimension.FEED_COMMENT),
            Map.entry("capEngagement", ScoringDimension.FEED_ENGAGEMENT_CAP),
            Map.entry("weightRecency", ScoringDimension.FEED_RECENCY),
            Map.entry("recencyHalfLifeHours", ScoringDimension.FEED_RECENCY_HALF_LIFE_HOURS));

    private static void bindWeights(Query query, ScoringWeights weights) {
        WEIGHT_PARAMETERS.forEach((parameter, dimension) -> query.setParameter(parameter, weights.get(dimension)));
    }

    /**
     * Uma query só: elegibilidade no {@code where}, fonte e score no
     * {@code select}. Pesos e ids entram como parâmetros (o texto do SQL não
     * muda quando o backoffice troca um peso). {@code now()} é fixo dentro da
     * transação, o que mantém a paginação estável durante a mesma requisição.
     */
    static final String SQL = String.format(Locale.ROOT, """
            select p.id as post_id,
                   case
                       when p.origin = 'PERSONAL' and p.fk_user in (:followedUserIds) then '%1$s'
                       when p.origin = 'COMMUNITY' and p.fk_community in (:memberCommunityIds) then '%2$s'
                       when p.origin = 'PERSONAL' then '%3$s'
                       else '%4$s'
                   end as feed_source,
                   (
                       case
                           when p.origin = 'PERSONAL' and p.fk_user in (:followedUserIds) then :weightFollowing
                           when p.origin = 'COMMUNITY' and p.fk_community in (:memberCommunityIds) then :weightMemberCommunity
                           when p.origin = 'COMMUNITY' and p.fk_user in (:followedUserIds) then :weightFollowedAuthorInCommunity
                           else 0
                       end
                       + case
                           when p.origin = 'PERSONAL' then least(:capSharedInterests, :weightSharedInterest * (
                               select count(*) from user_profile_interest_types i
                               where i.fk_user_profile = ap.id and i.fk_interest_type in (:interestTypeIds)))
                           else
                               (case when c.fk_category in (:communityCategoryIds) then :weightSameCategory else 0 end)
                               + (case when c.featured then :weightFeaturedCommunity else 0 end)
                               + least(:capFollowedMembers, :weightFollowedMember * (
                                   select count(*) from community_memberships m
                                   join user_profiles mp on mp.id = m.fk_user_profile
                                   where m.fk_community = c.id and mp.fk_user in (:followedUserIds)))
                       end
                       + least(:capEngagement,
                           :weightLike * (select count(*) from post_likes l where l.fk_post = p.id)
                           + :weightComment * (select count(*) from post_comments cm where cm.fk_post = p.id and cm.active = true))
                       + :weightRecency / (1 + greatest(0, extract(epoch from (now() - p.created_at)) / 3600.0) / :recencyHalfLifeHours)
                   ) as score,
                   p.created_at
            from posts p
            left join communities c on c.id = p.fk_community
            left join user_profiles ap on ap.fk_user = p.fk_user
            where p.active = true
              and not (p.origin = 'PERSONAL' and p.fk_user = :currentUserId)
              and p.fk_user not in (:hiddenAuthorUserIds)
              and not exists (select 1 from users bu where bu.id = p.fk_user and bu.banned = true)
              and (
                  p.origin = 'PERSONAL'
                  or (c.active = true and (c.privacy = 'PUBLIC' or c.id in (:memberCommunityIds)))
              )
              and (%5$s)
            order by score desc, p.created_at desc, p.id desc
            """,
            FeedSource.FOLLOWING.name(),
            FeedSource.MEMBER_COMMUNITY.name(),
            FeedSource.SUGGESTED_PROFILE.name(),
            FeedSource.SUGGESTED_COMMUNITY.name(),
            authorVisibilityFilter()
    );
}
