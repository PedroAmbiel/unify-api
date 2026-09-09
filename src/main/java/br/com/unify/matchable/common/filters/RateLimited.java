package br.com.unify.matchable.common.filters;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Marca um endpoint para ser limitado por taxa pelo {@link RateLimitFilter}.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ TYPE, METHOD })
public @interface RateLimited {

    /**
     * Numero maximo de requisicoes permitidas dentro da janela. Valor <= 0
     * (o default) significa "usa o default global" (unify.rate-limit.max-requests).
     */
    int limit() default -1;

    /**
     * Tamanho da janela deslizante, em segundos. Valor <= 0 (o default)
     * significa "usa o default global" (unify.rate-limit.window-seconds).
     */
    int windowSeconds() default -1;
}
