package br.com.unify.matchable.common.exceptions;

/**
 * Acesso negado a um recurso de negócio (403). Tipo próprio, e não
 * {@code SecurityException} diretamente, para que o
 * {@code GlobalExceptionMapper} não capture uma {@code SecurityException}
 * incidental vinda de biblioteca — só o que os serviços lançam
 * deliberadamente como recusa de autorização.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
