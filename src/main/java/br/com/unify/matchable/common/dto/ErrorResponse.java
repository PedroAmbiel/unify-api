package br.com.unify.matchable.common.dto;

import br.com.unify.matchable.common.enums.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO padrão para respostas de erro da API.
 * 
 * Exemplo de resposta:
 * {
 *   "code": 3002,
 *   "error": "VALIDATION_PASSWORD_TOO_SHORT",
 *   "message": "Senha deve ter pelo menos 8 caracteres",
 *   "timestamp": "2026-03-23T20:51:06Z"
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int code,
        String error,
        String message,
        Long timestamp
) {

    /**
     * Cria uma resposta de erro a partir de um ErrorCode
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.name(),
                errorCode.getDefaultMessage(),
                System.currentTimeMillis()
        );
    }

    /**
     * Cria uma resposta de erro a partir de um ErrorCode com detalhes adicionais
     */
    public static ErrorResponse of(ErrorCode errorCode, String details) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.name(),
                errorCode.getMessage(details),
                System.currentTimeMillis()
        );
    }

    /**
     * Cria uma resposta de erro customizada
     */
    public static ErrorResponse of(int code, String error, String message) {
        return new ErrorResponse(
                code,
                error,
                message,
                System.currentTimeMillis()
        );
    }
}

