package br.com.unify.matchable.user.enums;

/**
 * Quem pode ver as publicações PESSOAIS de um usuário (posts de comunidade
 * seguem a privacidade da comunidade, não esta configuração).
 */
public enum FeedVisibility {
    /** Qualquer usuário autenticado (inclusive como sugestão no Início). */
    PUBLIC,
    /** Só quem segue o autor. */
    FOLLOWERS_ONLY
}
