package br.com.unify.matchable.admin.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import br.com.unify.matchable.admin.dto.AdminBanRequest;
import br.com.unify.matchable.admin.dto.AdminPostDetailResponse;
import br.com.unify.matchable.admin.dto.AdminPostKeepRequest;
import br.com.unify.matchable.admin.dto.AdminPostRemovalRequest;
import br.com.unify.matchable.admin.dto.AdminPostSummaryResponse;
import br.com.unify.matchable.admin.dto.AdminReportDetailResponse;
import br.com.unify.matchable.admin.dto.AdminReportStatusUpdateRequest;
import br.com.unify.matchable.admin.dto.AdminReportSummaryResponse;
import br.com.unify.matchable.admin.dto.AdminReportedPostResponse;
import br.com.unify.matchable.admin.dto.AdminUserDetailResponse;
import br.com.unify.matchable.admin.dto.AdminUserSummaryResponse;
import br.com.unify.matchable.auth.entity.ActiveConnection;
import br.com.unify.matchable.common.dto.PageParams;
import br.com.unify.matchable.common.dto.PageResponse;
import br.com.unify.matchable.common.image.OidImageService;
import br.com.unify.matchable.moderation.entity.UserReport;
import br.com.unify.matchable.moderation.enums.UserReportStatus;
import br.com.unify.matchable.notification.entity.UserPushToken;
import br.com.unify.matchable.notification.services.NotificationService;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.enums.UserRole;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AdminModerationServiceImplementation implements AdminModerationService {

    static final int MIN_BAN_REASON_LENGTH = 10;
    static final int MAX_BAN_REASON_LENGTH = 500;
    static final int MIN_ADMIN_NOTE_LENGTH = 10;
    static final int MAX_ADMIN_NOTE_LENGTH = 1000;

    static final String REPORT_NOT_FOUND_MESSAGE = "Denúncia não encontrada";
    static final String USER_NOT_FOUND_MESSAGE = "Usuário não encontrado";
    static final String POST_MEDIA_NOT_FOUND_MESSAGE = "A denúncia não tem imagem de publicação";
    static final String STATUS_REQUIRED_MESSAGE = "Informe o novo status da denúncia";
    static final String REPORT_CLOSED_MESSAGE = "Denúncia já encerrada não muda de status";
    static final String REPORT_REOPEN_MESSAGE = "Uma denúncia não volta para aberta";
    static final String REPORT_ALREADY_UNDER_REVIEW_MESSAGE = "A denúncia já está em análise";
    static final String ADMIN_NOTE_REQUIRED_MESSAGE =
            "Para encerrar a denúncia, escreva uma nota com ao menos " + MIN_ADMIN_NOTE_LENGTH + " caracteres";
    static final String ADMIN_NOTE_TOO_LONG_MESSAGE =
            "A nota deve ter no máximo " + MAX_ADMIN_NOTE_LENGTH + " caracteres";
    static final String BAN_SELF_MESSAGE = "Um administrador não pode banir a própria conta";
    static final String BAN_ADMIN_MESSAGE = "Contas administrativas não podem ser banidas pelo backoffice";
    static final String ALREADY_BANNED_MESSAGE = "Este usuário já está banido";
    static final String NOT_BANNED_MESSAGE = "Este usuário não está banido";
    static final String BAN_REASON_TOO_SHORT_MESSAGE =
            "Informe o motivo do banimento com ao menos " + MIN_BAN_REASON_LENGTH + " caracteres";
    static final String BAN_REASON_TOO_LONG_MESSAGE =
            "O motivo do banimento deve ter no máximo " + MAX_BAN_REASON_LENGTH + " caracteres";

    /** Nota gravada nas denúncias encerradas pelo banimento em cascata. */
    static final String BAN_CASCADE_NOTE_PREFIX = "Encerrada pelo banimento da conta denunciada. Motivo: ";

    static final int MIN_REMOVAL_REASON_LENGTH = 10;
    static final int MAX_REMOVAL_REASON_LENGTH = 500;

    static final String POST_NOT_FOUND_MESSAGE = "Publicação não encontrada";
    static final String POST_WITHOUT_IMAGE_MESSAGE = "A publicação não tem imagem";
    static final String POST_ALREADY_REMOVED_MESSAGE = "Esta publicação já foi removida pela moderação";
    static final String POST_WITHOUT_PENDING_REPORTS_MESSAGE = "Esta publicação não tem denúncias pendentes";
    static final String REMOVAL_REASON_TOO_SHORT_MESSAGE =
            "Informe o motivo da remoção com ao menos " + MIN_REMOVAL_REASON_LENGTH + " caracteres";
    static final String REMOVAL_REASON_TOO_LONG_MESSAGE =
            "O motivo da remoção deve ter no máximo " + MAX_REMOVAL_REASON_LENGTH + " caracteres";

    /** Notas gravadas nas denúncias encerradas pela decisão sobre a publicação. */
    static final String POST_REMOVED_NOTE_PREFIX = "Publicação removida pela moderação. Motivo: ";
    static final String POST_KEPT_NOTE_PREFIX = "Publicação mantida pela moderação. ";

    @Inject
    AdminAuditService auditService;

    @Inject
    NotificationService notificationService;

    @Inject
    OidImageService oidImageService;

    @Inject
    EntityManager entityManager;

    // ---------------------------------------------------------------- denúncias

    @Override
    @Transactional
    public PageResponse<AdminReportSummaryResponse> listReports(String status, UUID reportedUserId, Integer page, Integer size) {
        int resolvedPage = PageParams.resolvePage(page);
        int resolvedSize = PageParams.resolveSize(size);

        List<String> conditions = new ArrayList<>();
        Parameters parameters = new Parameters();

        List<UserReportStatus> statuses = parseStatusFilter(status);
        if (statuses != null) {
            conditions.add("status in :statuses");
            parameters.and("statuses", statuses);
        }
        if (reportedUserId != null) {
            conditions.add("reportedUser.id = :reportedUserId");
            parameters.and("reportedUserId", reportedUserId);
        }

        String where = conditions.isEmpty() ? "" : String.join(" and ", conditions) + " ";
        PanacheQuery<UserReport> query = UserReport.find(where + "order by createdAt desc, id desc", parameters);

        long total = query.count();
        List<AdminReportSummaryResponse> reports = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(this::toReportSummary)
                .toList();
        return PageResponse.of(reports, resolvedPage, resolvedSize, total);
    }

    @Override
    @Transactional
    public AdminReportDetailResponse getReport(UUID reportId) {
        UserReport report = requireReport(reportId);
        List<AdminReportSummaryResponse> history = UserReport.listByReportedUser(report.reportedUser).stream()
                .map(this::toReportSummary)
                .toList();

        return new AdminReportDetailResponse(
                toReportSummary(report),
                toUserDetail(report.reportedUser),
                history);
    }

    @Override
    @Transactional
    public byte[] getReportedPostMedia(UUID reportId) {
        UserReport report = requireReport(reportId);
        Post post = report.reportedPost;
        if (post == null || post.mediaOid == null) {
            throw new NoSuchElementException(POST_MEDIA_NOT_FOUND_MESSAGE);
        }
        return oidImageService.readOidBlob(post.mediaOid);
    }

    @Override
    @Transactional
    public AdminReportSummaryResponse updateReportStatus(User admin, UUID reportId, AdminReportStatusUpdateRequest request) {
        UserReport report = requireReport(reportId);
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new IllegalArgumentException(STATUS_REQUIRED_MESSAGE);
        }

        UserReportStatus target = parseStatus(request.status());
        ensureTransitionAllowed(report.status, target);

        String note = trimToNull(request.adminNote());
        if (note != null && note.length() > MAX_ADMIN_NOTE_LENGTH) {
            throw new IllegalArgumentException(ADMIN_NOTE_TOO_LONG_MESSAGE);
        }
        if (target.isClosed() && (note == null || note.length() < MIN_ADMIN_NOTE_LENGTH)) {
            throw new IllegalArgumentException(ADMIN_NOTE_REQUIRED_MESSAGE);
        }

        UserReportStatus previous = report.status;
        Instant now = Instant.now();
        report.status = target;
        if (note != null) {
            report.adminNote = note;
        }
        report.reviewedAt = now;
        report.reviewedBy = admin;
        if (target.isClosed()) {
            report.resolvedAt = now;
        }

        auditService.record(admin, AdminAuditService.ACTION_REPORT_STATUS_CHANGE, AdminAuditService.TARGET_REPORT,
                reportId.toString(), previous + " -> " + target + (note == null ? "" : " | nota: " + note));

        if (target.isClosed()) {
            notifyReporter(report);
        }
        return toReportSummary(report);
    }

    /**
     * OPEN -> UNDER_REVIEW -> (RESOLVED | DISMISSED); OPEN pode ser encerrada
     * direto (triagem rápida). Encerrada não reabre.
     */
    private static void ensureTransitionAllowed(UserReportStatus from, UserReportStatus to) {
        if (from.isClosed()) {
            throw new IllegalStateException(REPORT_CLOSED_MESSAGE);
        }
        if (to == UserReportStatus.OPEN) {
            throw new IllegalStateException(REPORT_REOPEN_MESSAGE);
        }
        if (from == UserReportStatus.UNDER_REVIEW && to == UserReportStatus.UNDER_REVIEW) {
            throw new IllegalStateException(REPORT_ALREADY_UNDER_REVIEW_MESSAGE);
        }
    }

    /** Contrato da semana 05: o rótulo vai no texto "Sua denúncia foi {rótulo}". */
    private void notifyReporter(UserReport report) {
        if (report.reporter == null) {
            return;
        }
        notificationService.emitReportUpdate(
                report.reporter,
                report.id,
                report.status == UserReportStatus.RESOLVED ? "resolvida" : "analisada");
    }

    // ----------------------------------------------------------------- usuários

    @Override
    @Transactional
    public PageResponse<AdminUserSummaryResponse> searchUsers(String query, String filter, Integer page, Integer size) {
        int resolvedPage = PageParams.resolvePage(page);
        int resolvedSize = PageParams.resolveSize(size);

        List<String> conditions = new ArrayList<>();
        Parameters parameters = new Parameters();

        String term = trimToNull(query);
        if (term != null) {
            conditions.add("(lower(name) like :term or lower(lastName) like :term or lower(email) like :term "
                    + "or lower(concat(name, ' ', lastName)) like :term)");
            parameters.and("term", "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%");
        }

        switch (parseUserFilter(filter)) {
            case "ACTIVE" -> conditions.add("banned = false");
            case "BANNED" -> conditions.add("banned = true");
            case "ADMIN" -> {
                conditions.add("role = :role");
                parameters.and("role", UserRole.ADMIN);
            }
            default -> {
                // ALL: sem filtro
            }
        }

        String where = conditions.isEmpty() ? "" : String.join(" and ", conditions) + " ";
        PanacheQuery<User> panacheQuery = User.find(where + "order by lower(name), lower(lastName), id", parameters);

        long total = panacheQuery.count();
        List<AdminUserSummaryResponse> users = panacheQuery.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(AdminUserSummaryResponse::from)
                .toList();
        return PageResponse.of(users, resolvedPage, resolvedSize, total);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse getUser(UUID userId) {
        return toUserDetail(requireUser(userId));
    }

    @Override
    @Transactional
    public AdminUserDetailResponse banUser(User admin, UUID userId, AdminBanRequest request) {
        User target = requireUser(userId);
        if (target.id.equals(admin.id)) {
            throw new IllegalStateException(BAN_SELF_MESSAGE);
        }
        if (target.isAdmin()) {
            throw new IllegalStateException(BAN_ADMIN_MESSAGE);
        }
        if (target.banned) {
            throw new IllegalStateException(ALREADY_BANNED_MESSAGE);
        }

        String reason = trimToNull(request == null ? null : request.reason());
        if (reason == null || reason.length() < MIN_BAN_REASON_LENGTH) {
            throw new IllegalArgumentException(BAN_REASON_TOO_SHORT_MESSAGE);
        }
        if (reason.length() > MAX_BAN_REASON_LENGTH) {
            throw new IllegalArgumentException(BAN_REASON_TOO_LONG_MESSAGE);
        }

        Instant now = Instant.now();

        // 1) marca a conta (os três campos juntos: ck_users_ban_consistency)
        target.banned = true;
        target.bannedAt = now;
        target.banReason = reason;

        // 2) derruba as sessões (refresh tokens); o access token vivo cai no BannedUserFilter
        ActiveConnection.revokeAllForUser(target);

        // 3) nenhum push chega mais ao aparelho da conta suspensa
        UserPushToken.delete("user", target);

        // 4) encerra as denúncias pendentes contra a conta e avisa cada denunciante UMA vez
        List<UserReport> pendingReports = UserReport.list(
                "reportedUser = ?1 and status in ?2", target, UserReportStatus.PENDING);
        closeReports(pendingReports, UserReportStatus.RESOLVED, BAN_CASCADE_NOTE_PREFIX + reason, admin, now);

        auditService.record(admin, AdminAuditService.ACTION_BAN_USER, AdminAuditService.TARGET_USER,
                userId.toString(), "motivo: " + reason + " | denúncias encerradas: " + pendingReports.size());
        return toUserDetail(target);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse unbanUser(User admin, UUID userId) {
        User target = requireUser(userId);
        if (!target.banned) {
            throw new IllegalStateException(NOT_BANNED_MESSAGE);
        }

        String previousReason = target.banReason;
        target.banned = false;
        target.bannedAt = null;
        target.banReason = null;

        // As sessões antigas continuam revogadas: a pessoa entra de novo pelo login.
        auditService.record(admin, AdminAuditService.ACTION_UNBAN_USER, AdminAuditService.TARGET_USER,
                userId.toString(), "motivo anterior: " + previousReason);
        return toUserDetail(target);
    }


    // -------------------------------------------------------------- publicações

    @Override
    @Transactional
    public PageResponse<AdminPostSummaryResponse> listReportedPosts(String filter, Integer page, Integer size) {
        int resolvedPage = PageParams.resolvePage(page);
        int resolvedSize = PageParams.resolveSize(size);
        String resolvedFilter = parsePostFilter(filter);

        // PENDING: no ar e com denúncia pendente; REMOVED: removida pela moderação.
        String postCondition = switch (resolvedFilter) {
            case "PENDING" -> "p.moderatedAt is null";
            case "REMOVED" -> "p.moderatedAt is not null";
            default -> "1 = 1";
        };
        boolean onlyPending = "PENDING".equals(resolvedFilter);

        TypedQuery<Long> countQuery = entityManager.createQuery(
                "select count(p) from Post p where " + postCondition
                        + " and exists (select 1 from UserReport r where r.reportedPost = p"
                        + (onlyPending ? " and r.status in :pending" : "") + ")",
                Long.class);
        if (onlyPending) {
            countQuery.setParameter("pending", UserReportStatus.PENDING);
        }
        long total = countQuery.getSingleResult();

        // Uma linha por post com as contagens; só os ids da página são hidratados depois.
        TypedQuery<Object[]> pageQuery = entityManager.createQuery(
                "select p.id, count(r), "
                        + "sum(case when r.status in :pending then 1 else 0 end), max(r.createdAt) "
                        + "from UserReport r join r.reportedPost p where " + postCondition + " "
                        + "group by p.id "
                        + (onlyPending ? "having sum(case when r.status in :pending then 1 else 0 end) > 0 " : "")
                        + "order by max(r.createdAt) desc, p.id desc",
                Object[].class);
        pageQuery.setParameter("pending", UserReportStatus.PENDING);
        List<Object[]> rows = pageQuery
                .setFirstResult(resolvedPage * resolvedSize)
                .setMaxResults(resolvedSize)
                .getResultList();

        List<UUID> ids = rows.stream().map(row -> (UUID) row[0]).toList();
        Map<UUID, Post> postsById = new HashMap<>();
        if (!ids.isEmpty()) {
            Post.<Post>list("id in ?1", ids).forEach(post -> postsById.put(post.id, post));
        }

        List<AdminPostSummaryResponse> content = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Post post = postsById.get((UUID) row[0]);
            if (post == null) {
                continue;
            }
            content.add(new AdminPostSummaryResponse(
                    toReportedPost(post),
                    AdminUserSummaryResponse.from(post.author),
                    ((Number) row[1]).longValue(),
                    row[2] == null ? 0L : ((Number) row[2]).longValue(),
                    (Instant) row[3]));
        }
        return PageResponse.of(content, resolvedPage, resolvedSize, total);
    }

    @Override
    @Transactional
    public AdminPostDetailResponse getPost(UUID postId) {
        return toPostDetail(requirePost(postId));
    }

    @Override
    @Transactional
    public byte[] getPostMedia(UUID postId) {
        Post post = requirePost(postId);
        if (post.mediaOid == null) {
            throw new NoSuchElementException(POST_WITHOUT_IMAGE_MESSAGE);
        }
        return oidImageService.readOidBlob(post.mediaOid);
    }

    @Override
    @Transactional
    public AdminPostDetailResponse removePost(User admin, UUID postId, AdminPostRemovalRequest request) {
        Post post = requirePost(postId);
        if (post.isModerated()) {
            throw new IllegalStateException(POST_ALREADY_REMOVED_MESSAGE);
        }

        String reason = trimToNull(request == null ? null : request.reason());
        if (reason == null || reason.length() < MIN_REMOVAL_REASON_LENGTH) {
            throw new IllegalArgumentException(REMOVAL_REASON_TOO_SHORT_MESSAGE);
        }
        if (reason.length() > MAX_REMOVAL_REASON_LENGTH) {
            throw new IllegalArgumentException(REMOVAL_REASON_TOO_LONG_MESSAGE);
        }

        Instant now = Instant.now();
        boolean wasVisible = post.active;

        // 1) some para todos: toda leitura do app filtra active = true. Os campos de
        //    moderação impedem a volta (ck_posts_moderation_inactive) e ficam como evidência.
        post.active = false;
        post.moderatedAt = now;
        post.moderatedBy = admin;
        post.moderationReason = reason;

        // 2) encerra as denúncias pendentes do post, um aviso por denunciante
        List<UserReport> pendingReports = UserReport.list(
                "reportedPost = ?1 and status in ?2", post, UserReportStatus.PENDING);
        closeReports(pendingReports, UserReportStatus.RESOLVED, POST_REMOVED_NOTE_PREFIX + reason, admin, now);

        // 3) o autor fica sabendo (post já apagado por ele não precisa de aviso)
        if (wasVisible) {
            notificationService.emitPostRemoved(
                    post.author, post.id, post.community == null ? null : post.community.id, post.body);
        }

        auditService.record(admin, AdminAuditService.ACTION_POST_REMOVE, AdminAuditService.TARGET_POST,
                postId.toString(), "motivo: " + reason + " | denúncias encerradas: " + pendingReports.size());
        return toPostDetail(post);
    }

    @Override
    @Transactional
    public AdminPostDetailResponse keepPost(User admin, UUID postId, AdminPostKeepRequest request) {
        Post post = requirePost(postId);
        if (post.isModerated()) {
            throw new IllegalStateException(POST_ALREADY_REMOVED_MESSAGE);
        }

        String note = trimToNull(request == null ? null : request.adminNote());
        if (note == null || note.length() < MIN_ADMIN_NOTE_LENGTH) {
            throw new IllegalArgumentException(ADMIN_NOTE_REQUIRED_MESSAGE);
        }
        if (note.length() > MAX_ADMIN_NOTE_LENGTH) {
            throw new IllegalArgumentException(ADMIN_NOTE_TOO_LONG_MESSAGE);
        }

        List<UserReport> pendingReports = UserReport.list(
                "reportedPost = ?1 and status in ?2", post, UserReportStatus.PENDING);
        if (pendingReports.isEmpty()) {
            throw new IllegalStateException(POST_WITHOUT_PENDING_REPORTS_MESSAGE);
        }
        closeReports(pendingReports, UserReportStatus.DISMISSED, POST_KEPT_NOTE_PREFIX + note, admin, Instant.now());

        auditService.record(admin, AdminAuditService.ACTION_POST_KEEP, AdminAuditService.TARGET_POST,
                postId.toString(), "nota: " + note + " | denúncias descartadas: " + pendingReports.size());
        return toPostDetail(post);
    }

    /**
     * Encerra as denúncias de uma vez (ban, remoção ou manutenção do post) e
     * avisa cada denunciante UMA vez, mesmo que ele tenha várias na lista.
     */
    private void closeReports(List<UserReport> reports, UserReportStatus status, String note, User admin, Instant now) {
        String storedNote = truncate(note, MAX_ADMIN_NOTE_LENGTH);
        Set<UUID> notifiedReporters = new HashSet<>();
        for (UserReport report : reports) {
            report.status = status;
            report.adminNote = storedNote;
            report.reviewedAt = now;
            report.reviewedBy = admin;
            report.resolvedAt = now;
            if (report.reporter != null && notifiedReporters.add(report.reporter.id)) {
                notifyReporter(report);
            }
        }
    }

    private AdminPostDetailResponse toPostDetail(Post post) {
        List<AdminReportSummaryResponse> reports = UserReport.<UserReport>list(
                        "reportedPost = ?1 order by createdAt desc, id desc", post).stream()
                .map(this::toReportSummary)
                .toList();
        return new AdminPostDetailResponse(toReportedPost(post), toUserDetail(post.author), reports);
    }

    // -------------------------------------------------------------------- apoio

    private static UserReport requireReport(UUID reportId) {
        UserReport report = reportId == null ? null : UserReport.findById(reportId);
        if (report == null) {
            throw new NoSuchElementException(REPORT_NOT_FOUND_MESSAGE);
        }
        return report;
    }

    private static Post requirePost(UUID postId) {
        Post post = postId == null ? null : Post.findById(postId);
        if (post == null) {
            throw new NoSuchElementException(POST_NOT_FOUND_MESSAGE);
        }
        return post;
    }

    private static User requireUser(UUID userId) {
        User user = userId == null ? null : User.findById(userId);
        if (user == null) {
            throw new NoSuchElementException(USER_NOT_FOUND_MESSAGE);
        }
        return user;
    }

    /** {@code null} = sem filtro de status. */
    static List<UserReportStatus> parseStatusFilter(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        if ("PENDING".equalsIgnoreCase(normalized)) {
            return UserReportStatus.PENDING;
        }
        return List.of(parseStatus(normalized));
    }

    static UserReportStatus parseStatus(String value) {
        try {
            return UserReportStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Status inválido: " + value + ". Use OPEN, UNDER_REVIEW, RESOLVED ou DISMISSED");
        }
    }

    static String parsePostFilter(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "PENDING";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "PENDING", "REMOVED", "ALL" -> upper;
            default -> throw new IllegalArgumentException(
                    "Filtro inválido: " + value + ". Use PENDING, REMOVED ou ALL");
        };
    }

    static String parseUserFilter(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "ALL";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "ALL", "ACTIVE", "BANNED", "ADMIN" -> upper;
            default -> throw new IllegalArgumentException(
                    "Filtro inválido: " + value + ". Use ALL, ACTIVE, BANNED ou ADMIN");
        };
    }

    /** O termo vira parâmetro (sem injeção); só os curingas do LIKE precisam de escape. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    AdminReportSummaryResponse toReportSummary(UserReport report) {
        return new AdminReportSummaryResponse(
                report.id,
                report.status,
                report.reason,
                report.reason == null ? null : report.reason.getDescription(),
                report.description,
                report.createdAt,
                AdminUserSummaryResponse.from(report.reporter),
                AdminUserSummaryResponse.from(report.reportedUser),
                toReportedPost(report.reportedPost),
                report.adminNote,
                report.reviewedAt,
                report.reviewedBy == null ? null : fullName(report.reviewedBy),
                report.resolvedAt);
    }

    private static AdminReportedPostResponse toReportedPost(Post post) {
        if (post == null) {
            return null;
        }
        return new AdminReportedPostResponse(
                post.id,
                post.origin,
                post.body,
                post.mediaOid != null,
                post.imageDescription,
                post.active,
                post.community == null ? null : post.community.id,
                post.community == null ? null : post.community.name,
                post.createdAt,
                post.editedAt,
                post.moderatedAt,
                post.moderatedBy == null ? null : fullName(post.moderatedBy),
                post.moderationReason);
    }

    AdminUserDetailResponse toUserDetail(User user) {
        UserProfile profile = UserProfile.findByUser(user);
        long activeSessions = ActiveConnection.count(
                "user = ?1 and revoked = false and expiresAt > ?2", user, Instant.now());
        long reportsReceived = UserReport.count("reportedUser", user);
        long pendingReportsReceived = UserReport.count(
                "reportedUser = ?1 and status in ?2", user, UserReportStatus.PENDING);
        long reportsMade = UserReport.count("reporter", user);

        return new AdminUserDetailResponse(
                user.id,
                user.name,
                user.lastName,
                user.email,
                user.cellphone,
                user.birthdate,
                user.verified,
                user.role == null ? UserRole.USER : user.role,
                user.banned,
                user.bannedAt,
                user.banReason,
                profile == null ? null : profile.id,
                profile == null ? null : profile.bio,
                activeSessions,
                reportsReceived,
                pendingReportsReceived,
                reportsMade);
    }

    private static String fullName(User user) {
        return (user.name + " " + user.lastName).trim();
    }
}
