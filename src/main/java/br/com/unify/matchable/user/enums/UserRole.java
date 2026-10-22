package br.com.unify.matchable.user.enums;

/**
 * Papel do usuário (V27). Cardinalidade fechada de propósito: um usuário tem
 * exatamente um papel e ADMIN acumula as permissões de USER — no JWT vira
 * {@code groups = {"user", "admin"}}, então nenhum endpoint
 * {@code @RolesAllowed("user")} quebra para administradores.
 */
public enum UserRole {
    USER,
    ADMIN
}
