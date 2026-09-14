package br.com.unify.matchable.social.services;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
 *   <li>Perfis de usuário: hoje todos são públicos. O ponto único para a
 *       semana 04 (perfil privado / {@code feedVisibility} / bloqueio) é
 *       {@link #authorVisibilityFilter()} + {@link Context#hiddenAuthorUserIds()}.</li>
 * </ul>
 *
 * <p>Toda a pontuação é calculada no Postgres (query nativa), então a paginação
 * por offset é consistente entre páginas e o custo por página não cresce com
 * o número de páginas já vistas. Os pesos são as constantes abaixo; mudar um
 * peso não muda o contrato da API.
 */
public final class HomeFeedRankingPolicy {

    private HomeFeedRankingPolicy() {
    }

    // ---- Peso da fonte --------------------------------------------------
    /** Post pessoal de quem eu sigo. */
    public static final double WEIGHT_FOLLOWING = 60d;
    /** Post de comunidade em que sou membro. */
    public static final double WEIGHT_MEMBER_COMMUNITY = 50d;
    /** Post de comunidade pública (não membro) escrito por alguém que eu sigo. */
    public static final double WEIGHT_FOLLOWED_AUTHOR_IN_COMMUNITY = 25d;

    // ---- Afinidade: perfis sugeridos ---------------------------------------
    /** Por interesse em comum entre o autor e eu ({@code user_profile_interest_types}). */
    public static final double WEIGHT_SHARED_INTEREST = 15d;
    public static final double CAP_SHARED_INTERESTS = 45d;

    // ---- Afinidade: comunidades sugeridas ----------------------------------
    /** Categoria igual à de alguma comunidade em que já participo. */
    public static final double WEIGHT_SAME_CATEGORY = 20d;
    /** Comunidade marcada como destaque. */
    public static final double WEIGHT_FEATURED_COMMUNITY = 5d;
    /** Por pessoa que eu sigo e que é membro da comunidade. */
    public static final double WEIGHT_FOLLOWED_MEMBER = 10d;
    public static final double CAP_FOLLOWED_MEMBERS = 30d;

    // ---- Engajamento (vale para todas as fontes) ----------------------------
    public static final double WEIGHT_LIKE = 2d;
    public static final double WEIGHT_COMMENT = 4d;
    public static final double CAP_ENGAGEMENT = 30d;

    // ---- Recência ------------------------------------------------------------
    /** Pontos de uma publicação recém-criada; cai pela metade a cada {@link #RECENCY_HALF_LIFE_HOURS}. */
    public static final double WEIGHT_RECENCY = 40d;
    public static final double RECENCY_HALF_LIFE_HOURS = 48d;

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
     * @param hiddenAuthorUserIds autores que NUNCA devem aparecer (semana 04: bloqueios). Vazio hoje.
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
     * Filtro de visibilidade do AUTOR de post pessoal. Hoje todo perfil é
     * público, então o filtro é neutro. Semana 04: perfil privado /
     * {@code feedVisibility = FOLLOWERS_ONLY} entra aqui como um
     * {@code left join user_privacy_settings ... coalesce(...)} (nunca inner
     * join: quem nunca salvou configuração conta como público).
     */
    static String authorVisibilityFilter() {
        return "true";
    }

    /**
     * Executa o ranking e devolve {@code limit} linhas a partir de {@code offset},
     * já na ordem do feed (score desc, mais recente primeiro em empate).
     */
    public static List<RankedPost> rank(EntityManager entityManager, Context context, int offset, int limit) {
        Query query = entityManager.createNativeQuery(SQL)
                .setParameter("currentUserId", context.currentUserId())
                .setParameter("followedUserIds", uuidsOrSentinel(context.followedUserIds()))
                .setParameter("memberCommunityIds", uuidsOrSentinel(context.memberCommunityIds()))
                .setParameter("interestTypeIds", lookupsOrSentinel(context.interestTypeIds()))
                .setParameter("communityCategoryIds", lookupsOrSentinel(context.communityCategoryIds()))
                .setParameter("hiddenAuthorUserIds", uuidsOrSentinel(context.hiddenAuthorUserIds()))
                .setFirstResult(offset)
                .setMaxResults(limit);

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

    /**
     * Uma query só: elegibilidade no {@code where}, fonte e score no
     * {@code select}. Os pesos entram como literais (constantes acima), os
     * ids como parâmetros. {@code now()} é fixo dentro da transação, o que
     * mantém a paginação estável durante a mesma requisição.
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
                           when p.origin = 'PERSONAL' and p.fk_user in (:followedUserIds) then %5$s
                           when p.origin = 'COMMUNITY' and p.fk_community in (:memberCommunityIds) then %6$s
                           when p.origin = 'COMMUNITY' and p.fk_user in (:followedUserIds) then %7$s
                           else 0
                       end
                       + case
                           when p.origin = 'PERSONAL' then least(%9$s, %8$s * (
                               select count(*) from user_profile_interest_types i
                               where i.fk_user_profile = ap.id and i.fk_interest_type in (:interestTypeIds)))
                           else
                               (case when c.fk_category in (:communityCategoryIds) then %10$s else 0 end)
                               + (case when c.featured then %11$s else 0 end)
                               + least(%13$s, %12$s * (
                                   select count(*) from community_memberships m
                                   join user_profiles mp on mp.id = m.fk_user_profile
                                   where m.fk_community = c.id and mp.fk_user in (:followedUserIds)))
                       end
                       + least(%16$s,
                           %14$s * (select count(*) from post_likes l where l.fk_post = p.id)
                           + %15$s * (select count(*) from post_comments cm where cm.fk_post = p.id))
                       + %17$s / (1 + greatest(0, extract(epoch from (now() - p.created_at)) / 3600.0) / %18$s)
                   ) as score,
                   p.created_at
            from posts p
            left join communities c on c.id = p.fk_community
            left join user_profiles ap on ap.fk_user = p.fk_user
            where p.active = true
              and not (p.origin = 'PERSONAL' and p.fk_user = :currentUserId)
              and p.fk_user not in (:hiddenAuthorUserIds)
              and (
                  p.origin = 'PERSONAL'
                  or (c.active = true and (c.privacy = 'PUBLIC' or c.id in (:memberCommunityIds)))
              )
              and (%19$s)
            order by score desc, p.created_at desc, p.id desc
            """,
            FeedSource.FOLLOWING.name(),
            FeedSource.MEMBER_COMMUNITY.name(),
            FeedSource.SUGGESTED_PROFILE.name(),
            FeedSource.SUGGESTED_COMMUNITY.name(),
            WEIGHT_FOLLOWING,
            WEIGHT_MEMBER_COMMUNITY,
            WEIGHT_FOLLOWED_AUTHOR_IN_COMMUNITY,
            WEIGHT_SHARED_INTEREST,
            CAP_SHARED_INTERESTS,
            WEIGHT_SAME_CATEGORY,
            WEIGHT_FEATURED_COMMUNITY,
            WEIGHT_FOLLOWED_MEMBER,
            CAP_FOLLOWED_MEMBERS,
            WEIGHT_LIKE,
            WEIGHT_COMMENT,
            CAP_ENGAGEMENT,
            WEIGHT_RECENCY,
            RECENCY_HALF_LIFE_HOURS,
            authorVisibilityFilter()
    );
}
