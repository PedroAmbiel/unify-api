package br.com.unify.matchable.user.resources;

import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.UserBlockService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code GET /users/me/blocks}: quem EU bloqueei (nunca quem me bloqueou).
 *
 * <p>Bloquear/desbloquear ({@code POST|DELETE /users/{userProfileId}/block})
 * ficam em {@code SocialResource}, ao lado de follow: é o mesmo formato de
 * rota e uma classe separada com {@code @Path("/users")} disputaria o prefixo.
 * A listagem precisa de classe própria com o caminho completo porque
 * {@code UserProfileResource @Path("/users/me")} sombrearia {@code /me/blocks}.
 */
@Path("/users/me/blocks")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserBlockResource extends AuthenticatedResource {

    @Inject
    UserBlockService userBlockService;

    @GET
    @Transactional
    public Response listBlocked() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userBlockService.listBlocked(user)).build();
    }
}
