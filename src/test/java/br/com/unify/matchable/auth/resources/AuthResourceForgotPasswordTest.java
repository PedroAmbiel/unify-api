package br.com.unify.matchable.auth.resources;

import br.com.unify.matchable.auth.dto.ForgotPasswordRequest;
import br.com.unify.matchable.auth.dto.ResetPasswordRequest;
import br.com.unify.matchable.auth.dto.SignUpRequest;
import br.com.unify.matchable.auth.dto.VerificationCodeDispatchResponse;
import br.com.unify.matchable.auth.services.EmailVerificationService;
import br.com.unify.matchable.auth.services.PasswordResetService;
import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.dto.MessageResponse;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.ServicesUser;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuthResourceForgotPasswordTest {

    @Test
    void signUpAcceptsAdultUserAndForwardsBirthdate() {
        AuthResource resource = new AuthResource();
        StubServicesUser servicesUser = new StubServicesUser();
        StubEmailVerificationService emailVerificationService = new StubEmailVerificationService();
        resource.servicesUser = servicesUser;
        resource.emailVerificationService = emailVerificationService;

        LocalDate birthdate = LocalDate.now().minusYears(18);
        Response response = resource.signUp(new SignUpRequest(
                "Pedro",
                "Ambiel",
                "pedro@example.com",
                "Senha@123",
                birthdate
        ));

        assertEquals(202, response.getStatus());
        assertEquals(birthdate, servicesUser.capturedRequest.birthdate());
        VerificationCodeDispatchResponse body = assertInstanceOf(VerificationCodeDispatchResponse.class, response.getEntity());
        assertEquals("pedro@example.com", body.email());
    }

    @Test
    void signUpRejectsUnderageUser() {
        AuthResource resource = new AuthResource();

        Response response = resource.signUp(new SignUpRequest(
                "Pedro",
                "Ambiel",
                "pedro@example.com",
                "Senha@123",
                LocalDate.now().minusYears(17)
        ));

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_UNDERAGE_USER", body.error());
    }

    @Test
    void forgotPasswordReturnsGenericAcceptedMessage() {
        AuthResource resource = new AuthResource();
        StubPasswordResetService passwordResetService = new StubPasswordResetService();
        resource.passwordResetService = passwordResetService;

        Response response = resource.forgotPassword(new ForgotPasswordRequest("pedro@example.com"));

        assertEquals(202, response.getStatus());
        assertEquals("pedro@example.com", passwordResetService.issuedEmail);
        MessageResponse body = assertInstanceOf(MessageResponse.class, response.getEntity());
        assertEquals(
            "Caso o email esteja cadastrado, será enviado um email com um link de redefinição de senha",
                body.message()
        );
    }

    @Test
    void forgotPasswordRequiresEmail() {
        AuthResource resource = new AuthResource();

        Response response = resource.forgotPassword(new ForgotPasswordRequest("   "));

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_LOGIN_REQUIRED", body.error());
    }

    @Test
    void resetPasswordReturnsSuccessMessageWhenTokenIsValid() {
        AuthResource resource = new AuthResource();
        StubPasswordResetService passwordResetService = new StubPasswordResetService();
        passwordResetService.resetResult = true;
        resource.passwordResetService = passwordResetService;

        Response response = resource.resetPassword(new ResetPasswordRequest("valid-token", "NovaSenha@123"));

        assertEquals(200, response.getStatus());
        assertEquals("valid-token", passwordResetService.resetToken);
        assertEquals("NovaSenha@123", passwordResetService.resetPassword);
        MessageResponse body = assertInstanceOf(MessageResponse.class, response.getEntity());
        assertEquals("Senha redefinida com sucesso", body.message());
    }

    @Test
    void resetPasswordReturnsInvalidTokenErrorWhenServiceRejectsToken() {
        AuthResource resource = new AuthResource();
        StubPasswordResetService passwordResetService = new StubPasswordResetService();
        passwordResetService.resetResult = false;
        resource.passwordResetService = passwordResetService;

        Response response = resource.resetPassword(new ResetPasswordRequest("expired-token", "NovaSenha@123"));

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("USER_PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED", body.error());
    }

    @Test
    void resetPasswordRequiresToken() {
        AuthResource resource = new AuthResource();

        Response response = resource.resetPassword(new ResetPasswordRequest(" ", "NovaSenha@123"));

        assertEquals(400, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_PASSWORD_RESET_TOKEN_REQUIRED", body.error());
    }

    private static final class StubPasswordResetService extends PasswordResetService {
        private String issuedEmail;
        private String resetToken;
        private String resetPassword;
        private boolean resetResult;

        @Override
        public void issueResetLink(String email) {
            this.issuedEmail = email;
        }

        @Override
        public boolean resetPassword(String rawToken, String newPassword) {
            this.resetToken = rawToken;
            this.resetPassword = newPassword;
            return resetResult;
        }
    }

    private static final class StubServicesUser implements ServicesUser {
        private SignUpRequest capturedRequest;

        @Override
        public User createUser(SignUpRequest request) {
            this.capturedRequest = request;

            User user = new User();
            user.id = UUID.randomUUID();
            user.email = request.email();
            user.birthdate = request.birthdate();
            user.lastUpdatedAt = Instant.now();
            return user;
        }

        @Override
        public User findByEmail(String email) {
            return null;
        }

        @Override
        public void updatePassword(User user, String password, Instant updatedAt) {
        }
    }

    private static final class StubEmailVerificationService extends EmailVerificationService {
        @Override
        public VerificationCodeDispatchResponse issueCode(User user) {
            return new VerificationCodeDispatchResponse(
                    user.email,
                    10800,
                    "Codigo de verificacao enviado para o email informado"
            );
        }
    }
}