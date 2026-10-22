package br.com.unify.matchable.admin.dto;

/**
 * Item de catálogo visto pelo backoffice.
 *
 * @param ionicIcon     nulo quando o catálogo não tem ícone.
 * @param usageCount    referências atuais (perfis, preferências, comunidades).
 * @param protectedItem id fixo usado por regra de negócio: não desativa nem exclui.
 */
public record AdminLookupItemResponse(
        Integer id,
        String description,
        boolean active,
        String ionicIcon,
        long usageCount,
        boolean protectedItem
) {
}
