package br.com.unify.matchable.common.exceptions;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * ExceptionMapper Global para tratamento centralizado de exceções.
 * 
 * Responsabilidades:
 * - Capturar exceções não tratadas
 * - Converter em respostas de erro padronizadas
 * - Registrar em logs
 * - Retornar resposta JSON com ErrorResponse
 * 
 * Este mapper será aplicado automaticamente pelo Quarkus/JAX-RS
 * em todas as exceções não mapeadas.
 * 
 * Uso:
 * - Exceções são capturadas automaticamente
 * - Nenhuma configuração adicional necessária
 * - Funciona globalmente em toda a aplicação
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Exceção não tratada: ", exception);

        // Trata ValidationException customizada
        if (exception instanceof ValidationException validationEx) {
            return handleValidationException(validationEx);
        }

        // Trata IllegalArgumentException (erros de negócio)
        if (exception instanceof IllegalArgumentException illegalArgEx) {
            return handleIllegalArgumentException(illegalArgEx);
        }

        // Trata exceções genéricas
        ErrorResponse error = ErrorResponse.of(
                ErrorCode.SYSTEM_INTERNAL_ERROR,
                "Erro interno do servidor"
        );
        return Response
                .status(error.code())
                .entity(error)
                .build();
    }

    /**
     * Trata ValidationException
     */
    private Response handleValidationException(ValidationException ex) {
        ErrorResponse error = new ErrorResponse(
                ex.getCode(),
                ex.getErrorName(),
                ex.getMessage(),
                System.currentTimeMillis()
        );
        return Response
                .status(400)
                .entity(error)
                .build();
    }

    /**
     * Trata IllegalArgumentException (erros de negócio)
     * 
     * Exemplo: "Usuário já existe!"
     */
    private Response handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse error = ErrorResponse.of(
                ErrorCode.USER_ALREADY_EXISTS,
                ex.getMessage()
        );
        return Response
                .status(error.code())
                .entity(error)
                .build();
    }
}

