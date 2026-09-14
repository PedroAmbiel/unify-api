package br.com.unify.matchable.moderation.resources;

import java.util.NoSuchElementException;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.moderation.dto.ReportCreateRequest;
import br.com.unify.matchable.moderation.services.UserReportService;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code /users/reports} é o prefixo literal mais longo para as suas próprias
 * requisições, então não colide com {@code /users/me} nem com
 * {@code /users/{userProfileId}/...} do SocialResource (ver RoutingContractTest).
 *
 * Os try/catch locais existem porque UserReportResourceTest chama os métodos
 * direto (sem GlobalExceptionMapper), igual ao CommunityResource.
 */
@Path("/users/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserReportResource extends AuthenticatedResource {

    @Inject
    UserReportService userReportService;

    @POST
    @Transactional
    public Response createReport(ReportCreateRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.status(Response.Status.CREATED)
                    .entity(userReportService.createReport(user, request))
                    .build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/reasons")
    public Response listReasons() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userReportService.listReasons()).build();
    }

    private Response validationErrorResponse(String details) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(ErrorCode.VALIDATION_INVALID_FORMAT, details))
                .build();
    }

    private Response conflictResponse(String details) {
        return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.of(ErrorCode.RESOURCE_CONFLICT, details))
                .build();
    }

    private Response resourceNotFoundResponse(String details) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, details))
                .build();
    }
}
