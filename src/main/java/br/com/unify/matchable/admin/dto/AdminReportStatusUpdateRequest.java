package br.com.unify.matchable.admin.dto;

/**
 * @param status    UNDER_REVIEW | RESOLVED | DISMISSED. Texto (não enum) para
 *                  valor inválido virar 400 com mensagem em português.
 * @param adminNote obrigatória para RESOLVED e DISMISSED.
 */
public record AdminReportStatusUpdateRequest(
        String status,
        String adminNote
) {
}
