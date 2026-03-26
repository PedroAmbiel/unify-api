package br.com.unify.matchable.auth.resources;

import br.com.unify.matchable.auth.dto.RefreshTokenRequest;
import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.SignUpRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.auth.services.TokenService;
import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.ServicesUser;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    ServicesUser servicesUser;

    @Inject
    TokenService tokenService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/signup")
    @PermitAll
    @Transactional
    public Response signUp(SignUpRequest request,
                           @HeaderParam("User-Agent") String userAgent,
                           @HeaderParam("X-Forwarded-For") String forwardedFor) {
        if (request.login() == null || request.login().isBlank()) {
            ErrorResponse error = ErrorResponse.of(ErrorCode.VALIDATION_LOGIN_REQUIRED);
            return Response.status(error.code()).entity(error).build();
        }
        if (request.password() == null || request.password().length() < 8) {
            ErrorResponse error = ErrorResponse.of(ErrorCode.VALIDATION_PASSWORD_TOO_SHORT);
            return Response.status(error.code()).entity(error).build();
        }
        if (request.subscriptionMethod() == null) {
            ErrorResponse error = ErrorResponse.of(ErrorCode.VALIDATION_SUBSCRIPTION_METHOD_REQUIRED);
            return Response.status(error.code()).entity(error).build();
        }

        try {
            User user = servicesUser.createUser(request);
            TokenResponse tokens = tokenService.generateTokens(user, userAgent, forwardedFor);
            return Response.status(Response.Status.CREATED).entity(tokens).build();
        } catch (IllegalArgumentException e) {
            ErrorResponse error = ErrorResponse.of(ErrorCode.USER_ALREADY_EXISTS, e.getMessage());
            return Response.status(error.code()).entity(error).build();
        }
    }

    @POST
    @Path("/signin")
    @PermitAll
    @Transactional
    public Response signIn(SignInRequest request,
                           @HeaderParam("User-Agent") String userAgent,
                           @HeaderParam("X-Forwarded-For") String forwardedFor) {
        User user = servicesUser.findByLogin(request.login());
        if (user == null || !BcryptUtil.matches(request.password(), user.password)) {
            ErrorResponse error = ErrorResponse.of(ErrorCode.AUTH_INVALID_CREDENTIALS);
            return Response.status(error.code()).entity(error).build();
        }

        TokenResponse tokens = tokenService.generateTokens(user, userAgent, forwardedFor);
        return Response.ok(tokens).build();
    }

    @POST
    @Path("/refresh")
    @PermitAll
    @Transactional
    public Response refresh(RefreshTokenRequest request,
                            @HeaderParam("User-Agent") String userAgent,
                            @HeaderParam("X-Forwarded-For") String forwardedFor) {
        TokenResponse tokens = tokenService.refreshTokens(request.refreshToken(), userAgent, forwardedFor);
        if (tokens == null) {
            ErrorResponse error = ErrorResponse.of(ErrorCode.TOKEN_REFRESH_INVALID_OR_EXPIRED);
            return Response.status(error.code()).entity(error).build();
        }
        return Response.ok(tokens).build();
    }

    @POST
    @Path("/logout")
    @RolesAllowed("user")
    @Transactional
    public Response logout() {
        String userId = jwt.getSubject();
        User user = User.findById(java.util.UUID.fromString(userId));
        if (user != null) {
            tokenService.revokeAllConnections(user);
        }
        return Response.noContent().build();
    }
}
