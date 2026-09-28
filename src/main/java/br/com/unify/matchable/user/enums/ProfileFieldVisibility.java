package br.com.unify.matchable.user.enums;

/**
 * Quem vê uma parte do perfil ({@link ProfileField}). O dono sempre vê tudo.
 * "Seguir" é o {@code UserFollow} entre perfis.
 */
public enum ProfileFieldVisibility {
    /** Todos que podem ver o perfil público (default; sem linha no banco). */
    PUBLIC,
    /** Escondido para quem NÃO me segue. */
    FOLLOWERS_ONLY,
    /** Escondido para quem EU não sigo. */
    FOLLOWING_ONLY,
    /** Escondido para todos. */
    HIDDEN
}
