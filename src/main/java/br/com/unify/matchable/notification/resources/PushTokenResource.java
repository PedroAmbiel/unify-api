package br.com.unify.matchable.notification.resources;

import java.util.UUID;

import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.notification.dto.PushTokenRegisterRequest;
import br.com.unify.matchable.notification.services.NotificationService;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Tokens de push do aparelho. Classe própria com o caminho completo (mesmo
 * padrão de {@code UserBlockResource}): RESTEasy Reactive escolhe a classe
 * pelo prefixo literal mais longo e não volta atrás entre classes.
 *
 * <p>Registrar é idempotente (o mesmo token só troca de dono). Remover usa o
 * {@code id} devolvido no registro: o token não vai na URL.
 */
@Path("/notifications/push-tokens")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class PushTokenResource extends AuthenticatedResource {

    @Inject
    NotificationService notificationService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response register(PushTokenRegisterRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(notificationService.registerPushToken(user, request)).build();
    }

    @DELETE
    @Path("/{pushTokenId}")
    @Transactional
    public Response remove(@PathParam("pushTokenId") UUID pushTokenId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        notificationService.removePushToken(user, pushTokenId);
        return Response.noContent().build();
    }
}
