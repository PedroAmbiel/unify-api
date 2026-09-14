package br.com.unify.matchable.post.enums;

/**
 * Origem de uma linha de {@code posts}. Decisão de 2026-09-14: posts
 * de comunidade e posts pessoais (feed por follow) vivem na MESMA tabela para
 * que likes/comentários ({@code post_likes}/{@code post_comments})
 * e denúncias ({@code user_reports.fk_reported_post}) sirvam aos dois sem
 * duplicar tabelas. {@code PERSONAL} exige {@code fk_community} nulo.
 */
public enum PostOrigin {
    COMMUNITY,
    PERSONAL
}
