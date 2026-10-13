package br.com.unify.matchable.notification.resources;

import java.util.UUID;

import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.notification.services.NotificationService;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Caixa de entrada de notificações do usuário autenticado.
 *
 * <p>Erros de regra (página inválida, notificação de outra pessoa) sobem como
 * exceção para o {@code GlobalExceptionMapper} (400/404): os testes deste
 * resource são {@code @QuarkusTest} de ponta a ponta, sem try/catch local.
 *
 * <p>Os PUT não têm corpo, por isso não declaram {@code @Consumes} (senão um
 * PUT sem Content-Type daria 415 antes do 401 — ver RoutingContractTest).
 */
@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class NotificationResource extends AuthenticatedResource {

    @Inject
    NotificationService notificationService;

    @GET
    @Transactional
    public Response list(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(notificationService.getPage(user, page, size)).build();
    }

    @GET
    @Path("/unread-count")
    @Transactional
    public Response unreadCount() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(notificationService.getUnreadCount(user)).build();
    }

    @PUT
    @Path("/{notificationId}/read")
    @Transactional
    public Response markAsRead(@PathParam("notificationId") UUID notificationId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(notificationService.markAsRead(user, notificationId)).build();
    }

    @PUT
    @Path("/read-all")
    @Transactional
    public Response markAllAsRead() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(notificationService.markAllAsRead(user)).build();
    }
}
