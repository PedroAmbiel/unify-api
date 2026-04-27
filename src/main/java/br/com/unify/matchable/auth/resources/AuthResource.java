package br.com.unify.matchable.auth.resources;

import br.com.unify.matchable.auth.dto.EmailVerificationRequest;
import br.com.unify.matchable.auth.dto.ForgotPasswordRequest;
import br.com.unify.matchable.auth.dto.RefreshTokenRequest;
import br.com.unify.matchable.auth.dto.ResendEmailVerificationRequest;
import br.com.unify.matchable.auth.dto.ResetPasswordRequest;
import br.com.unify.matchable.auth.dto.SignInRequest;
import br.com.unify.matchable.auth.dto.SignUpRequest;
import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.auth.dto.VerificationCodeDispatchResponse;
import br.com.unify.matchable.auth.services.EmailVerificationService;
import br.com.unify.matchable.auth.services.PasswordResetService;
import br.com.unify.matchable.auth.services.TokenService;
import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.dto.MessageResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.common.validation.EmailValidator;
import br.com.unify.matchable.common.validation.PasswordValidator;
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

import java.time.LocalDate;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final int MINIMUM_SIGNUP_AGE = 18;

    private static final String PASSWORD_RESET_REQUEST_MESSAGE = "Caso o email esteja cadastrado, será enviado um email com um link de redefinição de senha";

    @Inject
    ServicesUser servicesUser;

    @Inject
    TokenService tokenService;

    @Inject
    EmailVerificationService emailVerificationService;

    @Inject
    PasswordResetService passwordResetService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/signup")
    @PermitAll
    @Transactional
    public Response signUp(SignUpRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            return errorResponse(ErrorCode.VALIDATION_LOGIN_REQUIRED);
        }
        if (request.birthdate() == null) {
            return errorResponse(ErrorCode.VALIDATION_REQUIRED_FIELD_MISSING, "birthdate");
        }
        if (!EmailValidator.isValid(request.email())) {
            return errorResponse(ErrorCode.VALIDATION_INVALID_FORMAT, "email");
        }
        if (!PasswordValidator.hasMinimumLength(request.password())) {
            return errorResponse(ErrorCode.VALIDATION_PASSWORD_TOO_SHORT);
        }
        if (!PasswordValidator.isValid(request.password())) {
            return errorResponse(ErrorCode.VALIDATION_INVALID_FORMAT, PasswordValidator.COMPLEXITY_REQUIREMENTS_MESSAGE);
        }
        if (!isAdult(request.birthdate())) {
            return errorResponse(ErrorCode.VALIDATION_UNDERAGE_USER);
        }

        try {
            User user = servicesUser.createUser(request);
            VerificationCodeDispatchResponse verificationResponse = emailVerificationService.issueCode(user);
            return Response.status(Response.Status.ACCEPTED).entity(verificationResponse).build();
        } catch (IllegalArgumentException e) {
            return errorResponse(ErrorCode.USER_ALREADY_EXISTS, e.getMessage());
        }
    }

    @POST
    @Path("/signin")
    @PermitAll
    @Transactional
    public Response signIn(SignInRequest request,
                           @HeaderParam("User-Agent") String userAgent,
                           @HeaderParam("X-Forwarded-For") String forwardedFor) {
        User user = servicesUser.findByEmail(request.email());
        if (user == null || !BcryptUtil.matches(request.password(), user.password)) {
            return errorResponse(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (emailVerificationService.requiresVerification(user)) {
            return errorResponse(ErrorCode.USER_EMAIL_NOT_VERIFIED);
        }

        TokenResponse tokens = tokenService.generateTokens(user, userAgent, forwardedFor);
        return Response.ok(tokens).build();
    }

    @POST
    @Path("/verify-email")
    @PermitAll
    @Transactional
    public Response verifyEmail(EmailVerificationRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            return errorResponse(ErrorCode.VALIDATION_LOGIN_REQUIRED);
        }
        if (request.code() == null || request.code().isBlank()) {
            return errorResponse(ErrorCode.VALIDATION_VERIFICATION_CODE_REQUIRED);
        }

        User user = servicesUser.findByEmail(request.email());
        if (user == null) {
            return errorResponse(ErrorCode.USER_NOT_FOUND);
        }
        if (user.verified) {
            return errorResponse(ErrorCode.USER_EMAIL_ALREADY_VERIFIED);
        }
        if (!emailVerificationService.verifyCode(user, request.code())) {
            return errorResponse(ErrorCode.USER_EMAIL_VERIFICATION_CODE_INVALID_OR_EXPIRED);
        }

        return Response.ok(new MessageResponse("Email verificado com sucesso")).build();
    }

    @POST
    @Path("/resend-email-verification")
    @PermitAll
    @Transactional
    public Response resendEmailVerification(ResendEmailVerificationRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            return errorResponse(ErrorCode.VALIDATION_LOGIN_REQUIRED);
        }

        User user = servicesUser.findByEmail(request.email());
        if (user == null) {
            return errorResponse(ErrorCode.USER_NOT_FOUND);
        }
        if (user.verified) {
            return errorResponse(ErrorCode.USER_EMAIL_ALREADY_VERIFIED);
        }

        VerificationCodeDispatchResponse verificationResponse = emailVerificationService.issueCode(user);
        return Response.accepted(verificationResponse).build();
    }

    @POST
    @Path("/forgot-password")
    @PermitAll
    @Transactional
    public Response forgotPassword(ForgotPasswordRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            return errorResponse(ErrorCode.VALIDATION_LOGIN_REQUIRED);
        }

        passwordResetService.issueResetLink(request.email());
        return Response.accepted(new MessageResponse(PASSWORD_RESET_REQUEST_MESSAGE)).build();
    }

    @POST
    @Path("/reset-password")
    @PermitAll
    @Transactional
    public Response resetPassword(ResetPasswordRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            return errorResponse(ErrorCode.VALIDATION_PASSWORD_RESET_TOKEN_REQUIRED);
        }
        if (!PasswordValidator.hasMinimumLength(request.password())) {
            return errorResponse(ErrorCode.VALIDATION_PASSWORD_TOO_SHORT);
        }
        if (!PasswordValidator.isValid(request.password())) {
            return errorResponse(ErrorCode.VALIDATION_INVALID_FORMAT, PasswordValidator.COMPLEXITY_REQUIREMENTS_MESSAGE);
        }
        if (!passwordResetService.resetPassword(request.token(), request.password())) {
            return errorResponse(ErrorCode.USER_PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED);
        }

        return Response.ok(new MessageResponse("Senha redefinida com sucesso")).build();
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
            return errorResponse(ErrorCode.TOKEN_REFRESH_INVALID_OR_EXPIRED);
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

    private Response errorResponse(ErrorCode errorCode) {
        return Response.status(errorCode.getHttpStatus())
                .entity(ErrorResponse.of(errorCode))
                .build();
    }

    private Response errorResponse(ErrorCode errorCode, String details) {
        return Response.status(errorCode.getHttpStatus())
                .entity(ErrorResponse.of(errorCode, details))
                .build();
    }

    private boolean isAdult(LocalDate birthdate) {
        return !birthdate.isAfter(LocalDate.now().minusYears(MINIMUM_SIGNUP_AGE));
    }
}
