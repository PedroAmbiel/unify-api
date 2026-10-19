package br.com.unify.matchable.community.services;

/** Comment text is optional only when a real image is attached. */
public record CommunityCommentContent(String body, String imageDescription) {
    public static CommunityCommentContent normalize(String body, String description, boolean hasImage) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty() && !hasImage)
            throw new IllegalArgumentException("Escreva um comentário ou envie uma imagem");
        if (text.length() > 2000)
            throw new IllegalArgumentException("O comentário deve ter até 2000 caracteres");
        return new CommunityCommentContent(text, PostImageDescription.normalize(description, hasImage));
    }
}
