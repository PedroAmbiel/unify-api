package br.com.unify.matchable.user.resources;

import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.user.dto.UserAccessibilitySettingsUpsertRequest;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.UserAccessibilitySettingsService;
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

@Path("/users/me/accessibility-settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserAccessibilitySettingsResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    UserAccessibilitySettingsService userAccessibilitySettingsService;

    @GET
    @Transactional
    public Response getSettings() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userAccessibilitySettingsService.getSettings(user)).build();
    }

    @PUT
    @Transactional
    public Response saveSettings(UserAccessibilitySettingsUpsertRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userAccessibilitySettingsService.saveSettings(user, request)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        }
    }

    protected User findCurrentUser() {
        return User.findById(UUID.fromString(jwt.getSubject()));
    }

    private Response userNotFoundResponse() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(ErrorCode.USER_NOT_FOUND))
                .build();
    }

    private Response validationErrorResponse(String details) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(ErrorCode.VALIDATION_INVALID_FORMAT, details))
                .build();
    }
}
