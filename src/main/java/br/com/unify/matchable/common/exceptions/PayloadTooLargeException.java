package br.com.unify.matchable.common.exceptions;

/**
 * Lancada quando um arquivo enviado excede o limite configurado
 * em `unify.upload.image.max-bytes`.
 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
