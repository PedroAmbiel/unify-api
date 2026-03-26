package br.com.unify.matchable.common.exceptions;

/**
 * Exception customizada para erros de validação de entrada.
 * 
 * Exemplo de uso:
 * throw new ValidationException(ErrorCode.VALIDATION_PASSWORD_TOO_SHORT);
 * throw new ValidationException(ErrorCode.USER_ALREADY_EXISTS, "Email já existe");
 */
public class ValidationException extends RuntimeException {

    private final int code;
    private final String errorName;
    private final String details;

    public ValidationException(int code, String errorName, String message) {
        super(message);
        this.code = code;
        this.errorName = errorName;
        this.details = null;
    }

    public ValidationException(int code, String errorName, String message, String details) {
        super(message);
        this.code = code;
        this.errorName = errorName;
        this.details = details;
    }

    public int getCode() {
        return code;
    }

    public String getErrorName() {
        return errorName;
    }

    public String getDetails() {
        return details;
    }
}

