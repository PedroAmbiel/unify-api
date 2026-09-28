package br.com.unify.matchable.user.resources;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.user.dto.UserPrivacySettingsUpdateRequest;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.UserPrivacySettingsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Privacidade do usuário autenticado. Classe própria com o caminho completo
 * (e não um sub-recurso de {@code /users}) pela regra de roteamento do
 * projeto: {@code UserProfileResource @Path("/users/me")} sombrearia
 * {@code /users/me/...} declarado em outra classe {@code @Path("/users")}.
 */
@Path("/users/me/privacy-settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserPrivacySettingsResource extends AuthenticatedResource {

    @Inject
    UserPrivacySettingsService userPrivacySettingsService;

    @GET
    @Transactional
    public Response getSettings() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userPrivacySettingsService.getSettings(user)).build();
    }

    @PUT
    @Transactional
    public Response saveSettings(UserPrivacySettingsUpdateRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userPrivacySettingsService.saveSettings(user, request)).build();
        } catch (IllegalArgumentException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(ErrorCode.VALIDATION_INVALID_FORMAT, exception.getMessage()))
                    .build();
        }
    }
}
