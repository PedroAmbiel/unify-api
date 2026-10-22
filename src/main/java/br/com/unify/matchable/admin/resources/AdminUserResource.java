package br.com.unify.matchable.admin.resources;

import java.util.UUID;

import br.com.unify.matchable.admin.dto.AdminBanRequest;
import br.com.unify.matchable.admin.services.AdminModerationService;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Backoffice — busca de contas, banimento e desbanimento (semana 06).
 * Nenhuma rota aqui altera papel: promoção a ADMIN só por configuração.
 */
@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminUserResource extends AuthenticatedResource {

    @Inject
    AdminModerationService moderationService;

    /** {@code filter}: ALL, ACTIVE, BANNED ou ADMIN. */
    @GET
    public Response searchUsers(@QueryParam("query") String query,
                                @QueryParam("filter") String filter,
                                @QueryParam("page") Integer page,
                                @QueryParam("size") Integer size) {
        return Response.ok(moderationService.searchUsers(query, filter, page, size)).build();
    }

    @GET
    @Path("/{userId}")
    public Response getUser(@PathParam("userId") UUID userId) {
        return Response.ok(moderationService.getUser(userId)).build();
    }

    @POST
    @Path("/{userId}/ban")
    @Transactional
    public Response ban(@PathParam("userId") UUID userId, AdminBanRequest body) {
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        return Response.ok(moderationService.banUser(admin, userId, body)).build();
    }

    /** Sem corpo: aceita qualquer Content-Type para não devolver 415 a um POST vazio. */
    @POST
    @Path("/{userId}/unban")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response unban(@PathParam("userId") UUID userId) {
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        return Response.ok(moderationService.unbanUser(admin, userId)).build();
    }
}
