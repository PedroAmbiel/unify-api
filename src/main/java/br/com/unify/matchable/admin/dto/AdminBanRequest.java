package br.com.unify.matchable.admin.dto;

/**
 * @param reason obrigatório, 10..500 caracteres. Fica na auditoria e é
 *               mostrado ao próprio usuário na tela de conta suspensa.
 */
public record AdminBanRequest(
        String reason
) {
}
