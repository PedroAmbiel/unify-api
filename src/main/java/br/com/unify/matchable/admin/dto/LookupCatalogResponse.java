package br.com.unify.matchable.admin.dto;

import br.com.unify.matchable.admin.enums.LookupIconMode;

/** Um catálogo administrável em {@code GET /admin/lookups}. */
public record LookupCatalogResponse(
        String code,
        String label,
        LookupIconMode iconMode,
        long totalItems,
        long activeItems
) {
}
