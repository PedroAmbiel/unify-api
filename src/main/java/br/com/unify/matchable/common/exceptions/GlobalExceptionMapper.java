package br.com.unify.matchable.common.exceptions;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.NoSuchElementException;

/**
 * ExceptionMapper global.
 *
 * Regras:
 * - WebApplicationException (404, 405, 406...) passa intacta: e o JAX-RS
 *   falando, nao um erro de negocio.
 * - ValidationException      -> 400 com o codigo de negocio da propria excecao.
 * - PayloadTooLargeException -> 413 VALIDATION_FILE_TOO_LARGE.
 * - IllegalArgumentException -> 400 VALIDATION_INVALID_ARGUMENT (generico).
 *   NUNCA 409/USER_ALREADY_EXISTS: conflito de usuario e responsabilidade do
 *   AuthResource, que ja trata esse caso explicitamente no /auth/signup.
 * - NoSuchElementException   -> 404 RESOURCE_NOT_FOUND.
 * - IllegalStateException    -> 409 RESOURCE_CONFLICT.
 * - Qualquer outra           -> 500 SYSTEM_INTERNAL_ERROR, sem vazar detalhes.
 *
 * O status HTTP vem SEMPRE de ErrorCode.getHttpStatus(). O campo `code` do
 * corpo e o codigo de negocio e nao deve ser usado como status.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        // 1. Deixa o JAX-RS resolver o que e dele (404, 405, 415, redirects...).
        if (exception instanceof WebApplicationException webApplicationException) {
            LOG.debugf("WebApplicationException repassada: %s", webApplicationException.getMessage());
            return webApplicationException.getResponse();
        }

        // 2. Validacao de dominio (400) - ja carrega codigo e nome proprios.
        if (exception instanceof ValidationException validationException) {
            LOG.debugf("ValidationException: %s", validationException.getMessage());
            return build(
                    400,
                    new ErrorResponse(
                            validationException.getCode(),
                            validationException.getErrorName(),
                            validationException.getMessage(),
                            System.currentTimeMillis()
                    )
            );
        }

        // 3. Upload acima do limite (413).
        if (exception instanceof PayloadTooLargeException payloadTooLargeException) {
            LOG.debugf("PayloadTooLargeException: %s", payloadTooLargeException.getMessage());
            return build(ErrorCode.VALIDATION_FILE_TOO_LARGE, payloadTooLargeException.getMessage());
        }

        // 4. Argumento invalido de negocio (400) - generico e honesto.
        if (exception instanceof IllegalArgumentException illegalArgumentException) {
            LOG.debugf("IllegalArgumentException: %s", illegalArgumentException.getMessage());
            return build(ErrorCode.VALIDATION_INVALID_ARGUMENT, illegalArgumentException.getMessage());
        }

        // 5. Recurso inexistente (404).
        if (exception instanceof NoSuchElementException noSuchElementException) {
            LOG.debugf("NoSuchElementException: %s", noSuchElementException.getMessage());
            return build(ErrorCode.RESOURCE_NOT_FOUND, noSuchElementException.getMessage());
        }

        // 6. Estado conflitante (409).
        if (exception instanceof IllegalStateException illegalStateException) {
            LOG.debugf("IllegalStateException: %s", illegalStateException.getMessage());
            return build(ErrorCode.RESOURCE_CONFLICT, illegalStateException.getMessage());
        }

        // 7. Falha inesperada (500). So aqui logamos como ERROR, com stack trace.
        LOG.error("Excecao nao tratada", exception);
        return build(ErrorCode.SYSTEM_INTERNAL_ERROR, null);
    }

    private Response build(ErrorCode errorCode, String details) {
        ErrorResponse body = (details == null || details.isBlank())
                ? ErrorResponse.of(errorCode)
                : ErrorResponse.of(errorCode, details);
        return build(errorCode.getHttpStatus(), body);
    }

    private Response build(int httpStatus, ErrorResponse body) {
        return Response
                .status(httpStatus)
                .entity(body)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
