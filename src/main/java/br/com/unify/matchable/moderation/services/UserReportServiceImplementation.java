package br.com.unify.matchable.moderation.services;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.moderation.dto.ReportCreateRequest;
import br.com.unify.matchable.moderation.dto.ReportReasonOptionResponse;
import br.com.unify.matchable.moderation.dto.ReportResponse;
import br.com.unify.matchable.moderation.entity.UserReport;
import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserReportServiceImplementation implements UserReportService {

    static final int DESCRIPTION_MAX_LENGTH = 2000;

    static final String REASON_REQUIRED_MESSAGE = "Selecione um motivo para a denúncia";
    static final String REPORTED_USER_REQUIRED_MESSAGE = "Informe a pessoa que está sendo denunciada";
    static final String SELF_REPORT_FORBIDDEN_MESSAGE = "Não é possível denunciar o próprio perfil";
    static final String REPORTED_USER_NOT_FOUND_MESSAGE = "Usuário denunciado não encontrado";
    static final String REPORTED_POST_NOT_FOUND_MESSAGE = "Publicação denunciada não encontrada";
    static final String REPORTED_POST_AUTHOR_MISMATCH_MESSAGE =
            "A publicação denunciada não pertence ao usuário informado";
    static final String DUPLICATE_OPEN_REPORT_MESSAGE = "Você já tem uma denúncia em aberto para este alvo";

    @Override
    @Transactional
    public ReportResponse createReport(User reporter, ReportCreateRequest request) {
        if (request == null || request.reason() == null) {
            throw new IllegalArgumentException(REASON_REQUIRED_MESSAGE);
        }

        if (request.reportedUserId() == null) {
            throw new IllegalArgumentException(REPORTED_USER_REQUIRED_MESSAGE);
        }

        User reportedUser = resolveReportedUser(request.reportedUserId());
        if (reportedUser == null) {
            throw new NoSuchElementException(REPORTED_USER_NOT_FOUND_MESSAGE);
        }

        if (reportedUser.id.equals(reporter.id)) {
            throw new IllegalArgumentException(SELF_REPORT_FORBIDDEN_MESSAGE);
        }

        Post reportedPost = null;
        if (request.reportedPostId() != null) {
            reportedPost = Post.findById(request.reportedPostId());
            if (reportedPost == null) {
                throw new NoSuchElementException(REPORTED_POST_NOT_FOUND_MESSAGE);
            }
            if (reportedPost.author == null || !reportedPost.author.id.equals(reportedUser.id)) {
                throw new IllegalArgumentException(REPORTED_POST_AUTHOR_MISMATCH_MESSAGE);
            }
        }

        if (UserReport.existsOpenReport(reporter, reportedUser, reportedPost)) {
            throw new IllegalStateException(DUPLICATE_OPEN_REPORT_MESSAGE);
        }

        UserReport report = new UserReport();
        report.id = UUIDv7Generator.generate();
        report.reporter = reporter;
        report.reportedUser = reportedUser;
        report.reportedPost = reportedPost;
        report.reason = request.reason();
        report.description = normalizeDescription(request.description());
        report.status = UserReportStatus.OPEN;
        report.createdAt = Instant.now();
        report.persist();

        return toResponse(report);
    }

    @Override
    public List<ReportReasonOptionResponse> listReasons() {
        return Arrays.stream(ReportReason.values())
                .map(reason -> new ReportReasonOptionResponse(reason, reason.getDescription()))
                .toList();
    }

    /**
     * O frontend nem sempre tem o id do {@link User} (o discovery só expõe o
     * id do {@link UserProfile}); aceita os dois para não obrigar uma segunda
     * chamada só para descobrir o alvo. O alvo persistido é sempre o User.
     */
    private User resolveReportedUser(UUID reportedUserId) {
        User user = User.findById(reportedUserId);
        if (user != null) {
            return user;
        }

        UserProfile profile = UserProfile.findById(reportedUserId);
        return profile == null ? null : profile.user;
    }

    String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.length() > DESCRIPTION_MAX_LENGTH ? trimmed.substring(0, DESCRIPTION_MAX_LENGTH) : trimmed;
    }

    private ReportResponse toResponse(UserReport report) {
        return new ReportResponse(
                report.id,
                report.reportedUser.id,
                report.reportedPost != null ? report.reportedPost.id : null,
                report.reason,
                report.description,
                report.status,
                report.createdAt
        );
    }
}
