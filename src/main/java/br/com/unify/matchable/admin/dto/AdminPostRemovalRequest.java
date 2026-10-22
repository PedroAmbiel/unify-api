package br.com.unify.matchable.admin.dto;

/** @param reason motivo interno da remoção (10..500 caracteres); não vai para o autor. */
public record AdminPostRemovalRequest(
        String reason
) {
}
