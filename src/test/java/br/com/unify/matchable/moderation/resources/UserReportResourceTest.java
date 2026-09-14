package br.com.unify.matchable.moderation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.moderation.dto.ReportCreateRequest;
import br.com.unify.matchable.moderation.dto.ReportReasonOptionResponse;
import br.com.unify.matchable.moderation.dto.ReportResponse;
import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;
import br.com.unify.matchable.moderation.services.UserReportService;
import br.com.unify.matchable.user.entity.User;
import jakarta.ws.rs.core.Response;

/** Mesmo estilo de CommunityResourceTest: chamada direta ao método + stub do serviço. */
class UserReportResourceTest {

    @Test
    void createReportReturnsCreatedWithBody() {
        StubUserReportService service = new StubUserReportService();
        UUID reportedUserId = UUID.randomUUID();
        service.nextResponse = new ReportResponse(
                UUID.randomUUID(), reportedUserId, null, ReportReason.HARASSMENT, "msg",
                UserReportStatus.OPEN, Instant.now()
        );
        TestableUserReportResource resource = buildResource(service);

        Response response = resource.createReport(
                new ReportCreateRequest(reportedUserId, null, ReportReason.HARASSMENT, "msg")
        );

        assertEquals(201, response.getStatus());
        ReportResponse body = assertInstanceOf(ReportResponse.class, response.getEntity());
        assertEquals(reportedUserId, body.reportedUserId());
        assertEquals(UserReportStatus.OPEN, body.status());
    }

    @Test
    void createReportMapsIllegalArgumentToBadRequest() {
        StubUserReportService service = new StubUserReportService();
        service.nextException = new IllegalArgumentException("Não é possível denunciar o próprio perfil");
        TestableUserReportResource resource = buildResource(service);

        Response response = resource.createReport(
                new ReportCreateRequest(UUID.randomUUID(), null, ReportReason.SCAM, null)
        );

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_INVALID_FORMAT", body.error());
    }

    @Test
    void createReportMapsIllegalStateToConflict() {
        StubUserReportService service = new StubUserReportService();
        service.nextException = new IllegalStateException("duplicada");
        TestableUserReportResource resource = buildResource(service);

        Response response = resource.createReport(
                new ReportCreateRequest(UUID.randomUUID(), null, ReportReason.SCAM, null)
        );

        assertEquals(409, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_CONFLICT", body.error());
    }

    @Test
    void createReportMapsNoSuchElementToNotFound() {
        StubUserReportService service = new StubUserReportService();
        service.nextException = new NoSuchElementException("não encontrado");
        TestableUserReportResource resource = buildResource(service);

        Response response = resource.createReport(
                new ReportCreateRequest(UUID.randomUUID(), null, ReportReason.SCAM, null)
        );

        assertEquals(404, response.getStatus());
    }

    @Test
    void createReportWithoutCurrentUserReturnsNotFound() {
        TestableUserReportResource resource = buildResource(new StubUserReportService());
        resource.currentUser = null;

        Response response = resource.createReport(
                new ReportCreateRequest(UUID.randomUUID(), null, ReportReason.SCAM, null)
        );

        assertEquals(404, response.getStatus());
    }

    @Test
    void listReasonsReturnsOk() {
        StubUserReportService service = new StubUserReportService();
        TestableUserReportResource resource = buildResource(service);

        Response response = resource.listReasons();

        assertEquals(200, response.getStatus());
        List<?> body = assertInstanceOf(List.class, response.getEntity());
        assertEquals(ReportReason.values().length, body.size());
    }

    private TestableUserReportResource buildResource(UserReportService service) {
        TestableUserReportResource resource = new TestableUserReportResource();
        resource.userReportService = service;
        User user = new User();
        user.id = UUID.randomUUID();
        resource.currentUser = user;
        return resource;
    }

    private static final class TestableUserReportResource extends UserReportResource {
        private User currentUser;

        @Override
        protected User findCurrentUser() {
            return currentUser;
        }
    }

    private static final class StubUserReportService implements UserReportService {
        private ReportResponse nextResponse;
        private RuntimeException nextException;

        @Override
        public ReportResponse createReport(User reporter, ReportCreateRequest request) {
            if (nextException != null) {
                throw nextException;
            }
            return nextResponse;
        }

        @Override
        public List<ReportReasonOptionResponse> listReasons() {
            return java.util.Arrays.stream(ReportReason.values())
                    .map(reason -> new ReportReasonOptionResponse(reason, reason.getDescription()))
                    .toList();
        }
    }
}
