package br.com.unify.matchable.common.enums;

public enum ErrorCode {

    AUTH_INVALID_CREDENTIALS(
            1001,
            401,
            "Credenciais inválidas"
    ),

    AUTH_UNAUTHORIZED(
            1002,
            401,
            "Não autorizado"
    ),

    AUTH_FORBIDDEN(
            1003,
            403,
            "Acesso negado"
    ),

    TOKEN_REFRESH_INVALID_OR_EXPIRED(
            2001,
            401,
            "Token de refresh inválido ou expirado"
    ),

    TOKEN_ACCESS_INVALID_OR_EXPIRED(
            2002,
            401,
            "Token de acesso inválido ou expirado"
    ),

    TOKEN_MALFORMED(
            2003,
            401,
            "Token malformado ou inválido"
    ),

    VALIDATION_LOGIN_REQUIRED(
            3001,
            400,
            "É necessário fornecer um email"
    ),

    VALIDATION_PASSWORD_TOO_SHORT(
            3002,
            400,
            "Senha deve ter pelo menos 8 caracteres"
    ),

    VALIDATION_REQUIRED_FIELD_MISSING(
            3004,
            400,
            "Campo obrigatório não fornecido"
    ),

    VALIDATION_INVALID_FORMAT(
            3005,
            400,
            "Formato de entrada inválido"
    ),

    VALIDATION_VERIFICATION_CODE_REQUIRED(
            3006,
            400,
            "Codigo de verificacao e obrigatorio"
    ),

    VALIDATION_PASSWORD_RESET_TOKEN_REQUIRED(
            3007,
            400,
            "Token de redefinicao de senha e obrigatorio"
    ),

    USER_NOT_FOUND(
            4001,
            404,
            "Usuário não encontrado"
    ),

    USER_ALREADY_EXISTS(
            4002,
            409,
            "Usuário já existe"
    ),

    USER_DISABLED(
            4003,
            403,
            "Usuário desativado ou bloqueado"
    ),

    USER_EMAIL_NOT_VERIFIED(
            4004,
            403,
            "Conta com verificacao de email pendente"
    ),

    USER_EMAIL_ALREADY_VERIFIED(
            4005,
            409,
            "Conta ja verificada"
    ),

    USER_EMAIL_VERIFICATION_CODE_INVALID_OR_EXPIRED(
            4006,
            400,
            "Codigo de verificacao invalido ou expirado"
    ),

    USER_PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED(
            4007,
            400,
            "Token de redefinicao de senha invalido ou expirado"
    ),

    RESOURCE_NOT_FOUND(
            5001,
            404,
            "Recurso não encontrado"
    ),

    RESOURCE_CONFLICT(
            5002,
            409,
            "Conflito ao processar recurso"
    ),

    SYSTEM_INTERNAL_ERROR(
            9001,
            500,
            "Erro interno do servidor"
    ),

    SYSTEM_OPERATION_NOT_SUPPORTED(
            9002,
            501,
            "Operação não suportada"
    ),

    SYSTEM_SERVICE_UNAVAILABLE(
            9003,
            503,
            "Serviço temporariamente indisponível"
    );


    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public String getMessage(String details) {
        if (details == null || details.isBlank()) {
            return defaultMessage;
        }
        return defaultMessage + ": " + details;
    }
}

