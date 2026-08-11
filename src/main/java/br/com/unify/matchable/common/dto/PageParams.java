package br.com.unify.matchable.common.dto;

/**
 * Regra global de paginacao da API.
 *
 * <p>{@code page} default 0 e nunca negativo; {@code size} default 20 e no
 * maximo 100. Valores fora da faixa levantam {@link IllegalArgumentException},
 * que o {@code GlobalExceptionMapper} traduz em 400 VALIDATION_INVALID_ARGUMENT.
 */
public final class PageParams {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static final String INVALID_PAGE_MESSAGE = "O parâmetro 'page' deve ser maior ou igual a zero";
    public static final String INVALID_SIZE_MESSAGE = "O parâmetro 'size' deve estar entre 1 e " + MAX_SIZE;

    private PageParams() {
    }

    public static int resolvePage(Integer page) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        if (resolvedPage < 0) {
            throw new IllegalArgumentException(INVALID_PAGE_MESSAGE);
        }
        return resolvedPage;
    }

    public static int resolveSize(Integer size) {
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw new IllegalArgumentException(INVALID_SIZE_MESSAGE);
        }
        return resolvedSize;
    }
}
