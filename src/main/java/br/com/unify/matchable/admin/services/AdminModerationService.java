package br.com.unify.matchable.admin.services;

import java.util.UUID;

import br.com.unify.matchable.admin.dto.AdminBanRequest;
import br.com.unify.matchable.admin.dto.AdminReportDetailResponse;
import br.com.unify.matchable.admin.dto.AdminReportStatusUpdateRequest;
import br.com.unify.matchable.admin.dto.AdminReportSummaryResponse;
import br.com.unify.matchable.admin.dto.AdminUserDetailResponse;
import br.com.unify.matchable.admin.dto.AdminUserSummaryResponse;
import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.user.entity.User;

/**
 * Moderação do backoffice (semana 06): fila de denúncias e banimento.
 * Erros seguem o mapper global (400 / 404 / 409).
 */
public interface AdminModerationService {

    /**
     * @param status         ALL (ou vazio), PENDING (OPEN + UNDER_REVIEW) ou um status exato.
     * @param reportedUserId opcional: só denúncias contra esta conta.
     */
    PageResponse<AdminReportSummaryResponse> listReports(String status, UUID reportedUserId, Integer page, Integer size);

    AdminReportDetailResponse getReport(UUID reportId);

    /** Imagem da publicação denunciada (mesmo já apagada pelo autor). */
    byte[] getReportedPostMedia(UUID reportId);

    AdminReportSummaryResponse updateReportStatus(User admin, UUID reportId, AdminReportStatusUpdateRequest request);

    /**
     * @param query  nome, sobrenome ou e-mail (parcial, sem diferenciar maiúsculas).
     * @param filter ALL (ou vazio), ACTIVE, BANNED ou ADMIN.
     */
    PageResponse<AdminUserSummaryResponse> searchUsers(String query, String filter, Integer page, Integer size);

    AdminUserDetailResponse getUser(UUID userId);

    /**
     * Bane em cascata: marca a conta, revoga todas as sessões, apaga os tokens
     * de push, encerra as denúncias pendentes contra ela como RESOLVED e avisa
     * cada denunciante uma vez.
     */
    AdminUserDetailResponse banUser(User admin, UUID userId, AdminBanRequest request);

    AdminUserDetailResponse unbanUser(User admin, UUID userId);
}
