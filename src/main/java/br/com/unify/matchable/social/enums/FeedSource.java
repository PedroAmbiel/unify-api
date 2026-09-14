package br.com.unify.matchable.social.enums;

/**
 * Por que uma publicação entrou no feed da aba Início ({@code GET /users/feed}).
 *
 * <p>O feed é ranqueado (ver {@code HomeFeedRankingPolicy}) e mistura quatro
 * fontes. O cliente usa este valor para rotular sugestões ("Sugestão para
 * você") e oferecer a ação rápida certa: seguir a pessoa ou entrar na
 * comunidade.
 *
 * <ul>
 *   <li>{@link #FOLLOWING}: post pessoal de alguém que eu sigo.</li>
 *   <li>{@link #MEMBER_COMMUNITY}: post de uma comunidade em que sou membro
 *       (pública ou privada).</li>
 *   <li>{@link #SUGGESTED_PROFILE}: post pessoal de alguém que eu NÃO sigo,
 *       recomendado por interesses em comum, curtidas e comentários.</li>
 *   <li>{@link #SUGGESTED_COMMUNITY}: post de uma comunidade PÚBLICA em que eu
 *       não sou membro. Comunidades privadas nunca entram aqui.</li>
 * </ul>
 */
public enum FeedSource {
    FOLLOWING,
    MEMBER_COMMUNITY,
    SUGGESTED_PROFILE,
    SUGGESTED_COMMUNITY
}
