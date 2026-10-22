package br.com.unify.matchable.admin.resources;

import java.util.UUID;

import br.com.unify.matchable.admin.dto.AdminReportStatusUpdateRequest;
import br.com.unify.matchable.admin.services.AdminModerationService;
import br.com.unify.matchable.common.image.ImageResponses;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

/**
 * Backoffice — fila de moderação de denúncias (semana 06). Erros de regra
 * saem pelo GlobalExceptionMapper (400/404/409), sempre em JSON.
 */
@Path("/admin/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminReportResource extends AuthenticatedResource {

    @Inject
    AdminModerationService moderationService;

    @Context
    Request request;

    /** {@code status}: ALL, PENDING, OPEN, UNDER_REVIEW, RESOLVED ou DISMISSED. */
    @GET
    public Response listReports(@QueryParam("status") String status,
                                @QueryParam("reportedUserId") UUID reportedUserId,
                                @QueryParam("page") Integer page,
                                @QueryParam("size") Integer size) {
        return Response.ok(moderationService.listReports(status, reportedUserId, page, size)).build();
    }

    @GET
    @Path("/{reportId}")
    public Response getReport(@PathParam("reportId") UUID reportId) {
        return Response.ok(moderationService.getReport(reportId)).build();
    }

    @GET
    @Path("/{reportId}/post-media")
    @Produces("image/jpeg")
    public Response getReportedPostMedia(@PathParam("reportId") UUID reportId) {
        return ImageResponses.jpeg(moderationService.getReportedPostMedia(reportId), request);
    }

    @PUT
    @Path("/{reportId}/status")
    @Transactional
    public Response updateStatus(@PathParam("reportId") UUID reportId, AdminReportStatusUpdateRequest body) {
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        return Response.ok(moderationService.updateReportStatus(admin, reportId, body)).build();
    }
}
