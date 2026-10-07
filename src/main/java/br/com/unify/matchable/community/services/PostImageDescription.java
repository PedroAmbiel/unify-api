package br.com.unify.matchable.community.services;

/** Shared validation for creation and edits. Description never lives in body. */
public final class PostImageDescription {
    public static final int MAX_LENGTH = 240;
    private PostImageDescription() {}
    public static String normalize(String value, boolean hasImage) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) return null;
        if (!hasImage) throw new IllegalArgumentException("A descrição exige uma imagem");
        if (normalized.length() > MAX_LENGTH)
            throw new IllegalArgumentException("A descrição da imagem deve ter até 240 caracteres");
        return normalized;
    }
}
