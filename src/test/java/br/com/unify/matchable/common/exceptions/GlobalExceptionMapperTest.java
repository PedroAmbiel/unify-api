package br.com.unify.matchable.common.exceptions;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressao do bug N1/N2: o mapper usava o codigo de negocio (9001, 4002) como
 * status HTTP e sequestrava as excecoes do JAX-RS.
 */
@DisplayName("GlobalExceptionMapper Tests")
class GlobalExceptionMapperTest {

    private final GlobalExceptionMapper mapper = new GlobalExceptionMapper();

    @Test
    @DisplayName("IllegalArgumentException deve virar 400 VALIDATION_INVALID_ARGUMENT")
    void illegalArgumentBecomes400InvalidArgument() {
        Response response = mapper.toResponse(new IllegalArgumentException("campo invalido"));

        assertEquals(400, response.getStatus());

        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_INVALID_ARGUMENT", body.error());
        assertEquals(3010, body.code());
        assertTrue(body.message().contains("campo invalido"));
    }

    @Test
    @DisplayName("IllegalArgumentException NAO deve mais virar 409 USER_ALREADY_EXISTS")
    void illegalArgumentIsNoLongerUserAlreadyExists() {
        Response response = mapper.toResponse(new IllegalArgumentException("qualquer coisa"));

        assertNotEquals(409, response.getStatus());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertNotEquals("USER_ALREADY_EXISTS", body.error());
    }

    @Test
    @DisplayName("Exception generica deve virar 500 SYSTEM_INTERNAL_ERROR")
    void genericExceptionBecomes500() {
        Response response = mapper.toResponse(new Exception("boom"));

        assertEquals(500, response.getStatus());

        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("SYSTEM_INTERNAL_ERROR", body.error());
        assertEquals(9001, body.code());
        // Nao deve vazar o detalhe interno da excecao.
        assertEquals(ErrorCode.SYSTEM_INTERNAL_ERROR.getDefaultMessage(), body.message());
    }

    @Test
    @DisplayName("NotFoundException do JAX-RS deve passar intacta como 404")
    void notFoundExceptionPassesThroughAs404() {
        Response response = mapper.toResponse(new NotFoundException());

        assertEquals(404, response.getStatus());
    }

    @Test
    @DisplayName("NotAllowedException do JAX-RS deve passar intacta como 405")
    void notAllowedExceptionPassesThroughAs405() {
        Response response = mapper.toResponse(new NotAllowedException("GET"));

        assertEquals(405, response.getStatus());
    }

    @Test
    @DisplayName("PayloadTooLargeException deve virar 413 VALIDATION_FILE_TOO_LARGE")
    void payloadTooLargeBecomes413() {
        Response response = mapper.toResponse(new PayloadTooLargeException("arquivo de 8 MB"));

        assertEquals(413, response.getStatus());

        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("VALIDATION_FILE_TOO_LARGE", body.error());
        assertEquals(3009, body.code());
    }

    @Test
    @DisplayName("NoSuchElementException deve virar 404 RESOURCE_NOT_FOUND")
    void noSuchElementBecomes404() {
        Response response = mapper.toResponse(new NoSuchElementException("comunidade"));

        assertEquals(404, response.getStatus());

        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_NOT_FOUND", body.error());
    }

    @Test
    @DisplayName("IllegalStateException deve virar 409 RESOURCE_CONFLICT")
    void illegalStateBecomes409() {
        Response response = mapper.toResponse(new IllegalStateException("ja existe"));

        assertEquals(409, response.getStatus());

        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("RESOURCE_CONFLICT", body.error());
    }

    @Test
    @DisplayName("ValidationException deve virar 400 preservando codigo e nome proprios")
    void validationExceptionKeepsOwnCode() {
        Response response = mapper.toResponse(
                new ValidationException(3002, "VALIDATION_PASSWORD_TOO_SHORT", "Senha curta"));

        assertEquals(400, response.getStatus());

        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals(3002, body.code());
        assertEquals("VALIDATION_PASSWORD_TOO_SHORT", body.error());
        assertEquals("Senha curta", body.message());
    }

    @Test
    @DisplayName("Nenhuma excecao pode produzir status fora da faixa 100-599")
    void noResponseCarriesStatusOutsideHttpRange() {
        List<Exception> exceptions = List.of(
                new IllegalArgumentException("x"),
                new IllegalStateException("x"),
                new NoSuchElementException("x"),
                new PayloadTooLargeException("x"),
                new ValidationException(3005, "VALIDATION_INVALID_FORMAT", "x"),
                new NotFoundException(),
                new NotAllowedException("GET"),
                new RuntimeException("x"),
                new Exception("x")
        );

        for (Exception exception : exceptions) {
            int status = mapper.toResponse(exception).getStatus();
            assertTrue(
                    status >= 100 && status <= 599,
                    exception.getClass().getSimpleName() + " produziu status HTTP invalido: " + status);
        }
    }

    @Test
    @DisplayName("Todo ErrorCode deve declarar um status HTTP valido")
    void everyErrorCodeHasValidHttpStatus() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertTrue(
                    errorCode.getHttpStatus() >= 100 && errorCode.getHttpStatus() <= 599,
                    errorCode.name() + " tem status HTTP invalido: " + errorCode.getHttpStatus());
        }
    }
}
