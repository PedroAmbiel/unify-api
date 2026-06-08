package br.com.unify.matchable.user.resources;

import java.util.UUID;
import java.util.NoSuchElementException;

import org.eclipse.microprofile.jwt.JsonWebToken;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.user.dto.MatchDecisionRequest;
import br.com.unify.matchable.user.dto.PotentialMatchesRequest;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.UserMatchService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/users/me/matches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserMatchResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    UserMatchService userMatchService;

    @POST
    @Path("/discovery")
    @Transactional
    public Response getPotentialMatches(PotentialMatchesRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userMatchService.getPotentialMatches(user, request)).build();
    }

    @POST
    @Transactional
    public Response registerDecision(MatchDecisionRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userMatchService.registerDecision(user, request)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/mutual")
    @Transactional
    public Response getMutualMatches() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userMatchService.getMutualMatches(user)).build();
    }

    @GET
    @Path("/mutual/paged")
    @Transactional
    public Response getMutualMatchesPage(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userMatchService.getMutualMatchesPage(user, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/images/{imageId}")
    @Produces("image/jpeg")
    @Transactional
    public Response getMatchedProfileImage(@PathParam("imageId") UUID imageId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userMatchService.getMatchedProfileImage(user, imageId)).type("image/jpeg").build();
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
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