package br.com.unify.matchable.admin.resources;

import java.util.UUID;

import br.com.unify.matchable.admin.dto.AdminPostKeepRequest;
import br.com.unify.matchable.admin.dto.AdminPostRemovalRequest;
import br.com.unify.matchable.admin.services.AdminModerationService;
import br.com.unify.matchable.common.image.ImageResponses;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

/**
 * Backoffice — moderação de publicações denunciadas, de comunidade e pessoais
 * (semana 07). Remover é definitivo: não existe rota para desfazer. Erros de
 * regra saem pelo GlobalExceptionMapper (400/404/409).
 */
@Path("/admin/posts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminPostResource extends AuthenticatedResource {

    @Inject
    AdminModerationService moderationService;

    @Context
    Request request;

    /** {@code filter}: PENDING (padrão), REMOVED ou ALL. */
    @GET
    @Path("/reported")
    public Response listReportedPosts(@QueryParam("filter") String filter,
                                      @QueryParam("page") Integer page,
                                      @QueryParam("size") Integer size) {
        return Response.ok(moderationService.listReportedPosts(filter, page, size)).build();
    }

    @GET
    @Path("/{postId}")
    public Response getPost(@PathParam("postId") UUID postId) {
        return Response.ok(moderationService.getPost(postId)).build();
    }

    @GET
    @Path("/{postId}/media")
    @Produces("image/jpeg")
    public Response getPostMedia(@PathParam("postId") UUID postId) {
        return ImageResponses.jpeg(moderationService.getPostMedia(postId), request);
    }

    @POST
    @Path("/{postId}/remove")
    @Transactional
    public Response remove(@PathParam("postId") UUID postId, AdminPostRemovalRequest body) {
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        return Response.ok(moderationService.removePost(admin, postId, body)).build();
    }

    @POST
    @Path("/{postId}/keep")
    @Transactional
    public Response keep(@PathParam("postId") UUID postId, AdminPostKeepRequest body) {
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        return Response.ok(moderationService.keepPost(admin, postId, body)).build();
    }
}
