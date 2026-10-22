package br.com.unify.matchable.admin.services;

import java.util.UUID;

import br.com.unify.matchable.admin.dto.AdminBanRequest;
import br.com.unify.matchable.admin.dto.AdminPostDetailResponse;
import br.com.unify.matchable.admin.dto.AdminPostKeepRequest;
import br.com.unify.matchable.admin.dto.AdminPostRemovalRequest;
import br.com.unify.matchable.admin.dto.AdminPostSummaryResponse;
import br.com.unify.matchable.admin.dto.AdminReportDetailResponse;
import br.com.unify.matchable.admin.dto.AdminReportStatusUpdateRequest;
import br.com.unify.matchable.admin.dto.AdminReportSummaryResponse;
import br.com.unify.matchable.admin.dto.AdminUserDetailResponse;
import br.com.unify.matchable.admin.dto.AdminUserSummaryResponse;
import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.user.entity.User;

/**
 * Moderação do backoffice: fila de denúncias e banimento (semana 06) e
 * moderação de publicações denunciadas (semana 07). Erros seguem o mapper
 * global (400 / 404 / 409).
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

    // ------------------------------------------------- publicações (semana 07)

    /**
     * Publicações com ao menos uma denúncia, uma linha por post, denúncia mais
     * recente primeiro.
     *
     * @param filter PENDING (ou vazio: com denúncia pendente e ainda no ar),
     *               REMOVED (removidas pela moderação) ou ALL.
     */
    PageResponse<AdminPostSummaryResponse> listReportedPosts(String filter, Integer page, Integer size);

    AdminPostDetailResponse getPost(UUID postId);

    /** Imagem da publicação (mesmo removida ou apagada pelo autor). */
    byte[] getPostMedia(UUID postId);

    /**
     * Remoção DEFINITIVA: o post some para todos (inclusive o autor) e não há
     * como desfazer. Encerra como RESOLVED as denúncias pendentes contra ele,
     * avisa cada denunciante uma vez e avisa o autor.
     */
    AdminPostDetailResponse removePost(User admin, UUID postId, AdminPostRemovalRequest request);

    /** Mantém o post no ar e descarta (DISMISSED) todas as denúncias pendentes contra ele. */
    AdminPostDetailResponse keepPost(User admin, UUID postId, AdminPostKeepRequest request);
}
