package br.com.unify.matchable.admin.dto;

/** @param adminNote por que a publicação fica no ar (10..1000 caracteres). */
public record AdminPostKeepRequest(
        String adminNote
) {
}
