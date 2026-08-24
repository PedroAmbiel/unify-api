package br.com.unify.matchable.community.enums;

/**
 * Visibilidade de entrada da comunidade.
 *
 * <p>{@code PUBLIC}: qualquer usuário entra imediatamente ao solicitar.
 * {@code PRIVATE}: a entrada gera uma solicitação pendente que precisa ser
 * aprovada por um ADMIN ou MODERATOR da comunidade.
 */
public enum CommunityPrivacy {
    PUBLIC,
    PRIVATE
}
