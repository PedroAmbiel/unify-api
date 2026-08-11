package br.com.unify.matchable.common.dto;

import java.util.List;

/**
 * Envelope generico de paginacao usado por todos os endpoints de listagem.
 *
 * <p>Formato JSON:
 * <pre>
 * {
 *   "content": [ ... ],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 42,
 *   "totalPages": 3,
 *   "hasNext": true
 * }
 * </pre>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /**
     * Monta a resposta calculando {@code totalPages} e {@code hasNext} a partir
     * do total de elementos.
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        List<T> safeContent = content == null ? List.of() : content;
        int safeSize = size < 1 ? PageParams.DEFAULT_SIZE : size;
        int totalPages = totalElements <= 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        return new PageResponse<>(
                safeContent,
                page,
                safeSize,
                Math.max(totalElements, 0),
                totalPages,
                page + 1 < totalPages
        );
    }

    /** Pagina vazia preservando os parametros pedidos pelo cliente. */
    public static <T> PageResponse<T> empty(int page, int size) {
        return of(List.of(), page, size, 0L);
    }
}
