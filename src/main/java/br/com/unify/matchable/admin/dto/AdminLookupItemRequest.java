package br.com.unify.matchable.admin.dto;

/**
 * Criação ({@code POST}) e edição ({@code PUT}) de item de catálogo.
 * {@code ionicIcon} é obrigatório quando o catálogo exige ícone e ignorado
 * quando o catálogo não tem ícone. Na edição, ícone nulo mantém o atual;
 * vazio remove (só em catálogo de ícone opcional).
 */
public record AdminLookupItemRequest(
        String description,
        String ionicIcon
) {
}
