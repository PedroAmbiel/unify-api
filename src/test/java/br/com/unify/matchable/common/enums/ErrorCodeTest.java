package br.com.unify.matchable.common.enums;

import br.com.unify.matchable.common.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para validar o comportamento do enum ErrorCode
 * e a geração de respostas de erro.
 */
@DisplayName("ErrorCode Enum Tests")
class ErrorCodeTest {

    @Test
    @DisplayName("Deve retornar código correto para AUTH_INVALID_CREDENTIALS")
    void testAuthInvalidCredentialsCode() {
        ErrorCode errorCode = ErrorCode.AUTH_INVALID_CREDENTIALS;
        assertEquals(1001, errorCode.getCode());
        assertEquals(401, errorCode.getHttpStatus());
    }

    @Test
    @DisplayName("Deve retornar mensagem padrão em português")
    void testDefaultMessageInPortuguese() {
        ErrorCode errorCode = ErrorCode.VALIDATION_PASSWORD_TOO_SHORT;
        assertEquals("Senha deve ter pelo menos 8 caracteres", errorCode.getDefaultMessage());
    }

    @Test
    @DisplayName("Deve adicionar detalhes à mensagem corretamente")
    void testMessageWithDetails() {
        ErrorCode errorCode = ErrorCode.USER_ALREADY_EXISTS;
        String message = errorCode.getMessage("Email já cadastrado");
        assertEquals("Usuário já existe: Email já cadastrado", message);
    }

    @Test
    @DisplayName("Deve retornar mensagem padrão quando detalhes são nulos")
    void testMessageWithNullDetails() {
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND;
        String message = errorCode.getMessage(null);
        assertEquals("Usuário não encontrado", message);
    }

    @Test
    @DisplayName("Deve retornar mensagem padrão quando detalhes estão em branco")
    void testMessageWithBlankDetails() {
        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED;
        String message = errorCode.getMessage("   ");
        assertEquals("Não autorizado", message);
    }

    @Test
    @DisplayName("ErrorResponse.of() deve criar resposta com ErrorCode")
    void testErrorResponseFromErrorCode() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.TOKEN_REFRESH_INVALID_OR_EXPIRED);
        
        assertEquals(2001, response.code());
        assertEquals("TOKEN_REFRESH_INVALID_OR_EXPIRED", response.error());
        assertEquals("Token de refresh inválido ou expirado", response.message());
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("ErrorResponse.of() deve incluir detalhes na mensagem")
    void testErrorResponseWithDetails() {
        String details = "Token expirado em 2026-03-23";
        ErrorResponse response = ErrorResponse.of(ErrorCode.TOKEN_REFRESH_INVALID_OR_EXPIRED, details);
        
        assertEquals(2001, response.code());
        assertEquals("TOKEN_REFRESH_INVALID_OR_EXPIRED", response.error());
        assertTrue(response.message().contains(details));
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("ErrorResponse.of() customizado deve aceitar valores diretos")
    void testCustomErrorResponse() {
        ErrorResponse response = ErrorResponse.of(9999, "CUSTOM_ERROR", "Erro customizado");
        
        assertEquals(9999, response.code());
        assertEquals("CUSTOM_ERROR", response.error());
        assertEquals("Erro customizado", response.message());
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("Todos os ErrorCodes devem ter códigos únicos")
    void testUniqueErrorCodes() {
        ErrorCode[] codes = ErrorCode.values();
        int[] codeValues = new int[codes.length];
        
        for (int i = 0; i < codes.length; i++) {
            codeValues[i] = codes[i].getCode();
        }
        
        // Verifica se há duplicatas
        for (int i = 0; i < codeValues.length; i++) {
            for (int j = i + 1; j < codeValues.length; j++) {
                assertNotEquals(codeValues[i], codeValues[j], 
                    "Código " + codeValues[i] + " aparece mais de uma vez");
            }
        }
    }

    @Test
    @DisplayName("AUTH_* codes devem estar na faixa 1000-1999")
    void testAuthCodesInRange() {
        assertEquals(1001, ErrorCode.AUTH_INVALID_CREDENTIALS.getCode());
        assertEquals(1002, ErrorCode.AUTH_UNAUTHORIZED.getCode());
        assertEquals(1003, ErrorCode.AUTH_FORBIDDEN.getCode());
        
        for (ErrorCode code : ErrorCode.values()) {
            if (code.name().startsWith("AUTH_")) {
                assertTrue(code.getCode() >= 1000 && code.getCode() < 2000,
                    code.name() + " deve estar na faixa 1000-1999");
            }
        }
    }

    @Test
    @DisplayName("VALIDATION_* codes devem estar na faixa 3000-3999")
    void testValidationCodesInRange() {
        for (ErrorCode code : ErrorCode.values()) {
            if (code.name().startsWith("VALIDATION_")) {
                assertTrue(code.getCode() >= 3000 && code.getCode() < 4000,
                    code.name() + " deve estar na faixa 3000-3999");
            }
        }
    }

    @Test
    @DisplayName("USER_* codes devem estar na faixa 4000-4999")
    void testUserCodesInRange() {
        for (ErrorCode code : ErrorCode.values()) {
            if (code.name().startsWith("USER_")) {
                assertTrue(code.getCode() >= 4000 && code.getCode() < 5000,
                    code.name() + " deve estar na faixa 4000-4999");
            }
        }
    }

    @Test
    @DisplayName("VALIDATION_LOGIN_REQUIRED deve retornar 400")
    void testValidationErrorsReturn400() {
        assertEquals(400, ErrorCode.VALIDATION_LOGIN_REQUIRED.getHttpStatus());
        assertEquals(400, ErrorCode.VALIDATION_PASSWORD_TOO_SHORT.getHttpStatus());
        assertEquals(400, ErrorCode.VALIDATION_VERIFICATION_CODE_REQUIRED.getHttpStatus());
    }

    @Test
    @DisplayName("AUTH_* errors devem retornar 401 ou 403")
    void testAuthErrorsReturnCorrectStatus() {
        assertEquals(401, ErrorCode.AUTH_INVALID_CREDENTIALS.getHttpStatus());
        assertEquals(401, ErrorCode.AUTH_UNAUTHORIZED.getHttpStatus());
        assertEquals(403, ErrorCode.AUTH_FORBIDDEN.getHttpStatus());
    }

    @Test
    @DisplayName("USER_ALREADY_EXISTS deve retornar 409 Conflict")
    void testUserAlreadyExistsReturns409() {
        assertEquals(409, ErrorCode.USER_ALREADY_EXISTS.getHttpStatus());
    }

    @Test
    @DisplayName("USER_EMAIL_NOT_VERIFIED deve retornar 403 Forbidden")
    void testUserEmailNotVerifiedReturns403() {
        assertEquals(403, ErrorCode.USER_EMAIL_NOT_VERIFIED.getHttpStatus());
    }

    @Test
    @DisplayName("USER_EMAIL_VERIFICATION_CODE_INVALID_OR_EXPIRED deve retornar 400 Bad Request")
    void testVerificationCodeInvalidReturns400() {
        assertEquals(400, ErrorCode.USER_EMAIL_VERIFICATION_CODE_INVALID_OR_EXPIRED.getHttpStatus());
    }

    @Test
    @DisplayName("USER_NOT_FOUND deve retornar 404 Not Found")
    void testUserNotFoundReturns404() {
        assertEquals(404, ErrorCode.USER_NOT_FOUND.getHttpStatus());
    }

    @Test
    @DisplayName("SYSTEM_INTERNAL_ERROR deve retornar 500")
    void testSystemErrorReturns500() {
        assertEquals(500, ErrorCode.SYSTEM_INTERNAL_ERROR.getHttpStatus());
    }
}

