package br.com.unify.matchable.common.filters;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting simples por (IP + rota), com janela deslizante em memoria.
 *
 * Escolha deliberada: sem dependencia externa (nada de Bucket4j/Redis).
 * Adequado a deploy de instancia unica, que e o cenario atual do projeto.
 * Para multi-instancia, trocar o `buckets` por um store distribuido -
 * o resto do filtro permanece identico.
 *
 * Aplique com @RateLimited no metodo ou na classe do recurso.
 */
@Provider
@RateLimited
@ApplicationScoped
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);
    private static final int MAX_TRACKED_KEYS = 10_000;

    @ConfigProperty(name = "unify.rate-limit.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "unify.rate-limit.max-requests", defaultValue = "10")
    int maxRequests;

    @ConfigProperty(name = "unify.rate-limit.window-seconds", defaultValue = "60")
    long windowSeconds;

    @ConfigProperty(name = "quarkus.http.proxy.proxy-address-forwarding", defaultValue = "false")
    boolean proxyAddressForwardingEnabled;

    @Context
    HttpServerRequest httpServerRequest;

    @Context
    ResourceInfo resourceInfo;

    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!enabled) {
            return;
        }

        int effectiveMaxRequests = maxRequests;
        long effectiveWindowSeconds = windowSeconds;
        RateLimited annotation = resolveAnnotation();
        if (annotation != null) {
            if (annotation.limit() > 0) {
                effectiveMaxRequests = annotation.limit();
            }
            if (annotation.windowSeconds() > 0) {
                effectiveWindowSeconds = annotation.windowSeconds();
            }
        }

        String key = resolveClientIp() + "|" + requestContext.getUriInfo().getPath();
        Instant now = Instant.now();
        Instant windowStart = now.minus(Duration.ofSeconds(effectiveWindowSeconds));

        // Guarda simples contra crescimento ilimitado do mapa.
        if (buckets.size() > MAX_TRACKED_KEYS) {
            buckets.entrySet().removeIf(entry -> {
                Deque<Instant> hits = entry.getValue();
                synchronized (hits) {
                    return hits.isEmpty() || hits.peekLast().isBefore(windowStart);
                }
            });
        }

        Deque<Instant> hits = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst().isBefore(windowStart)) {
                hits.pollFirst();
            }

            if (hits.size() >= effectiveMaxRequests) {
                long retryAfter = Duration.between(hits.peekFirst(), windowStart.plusSeconds(effectiveWindowSeconds))
                        .getSeconds();
                LOG.warnf("Rate limit atingido para %s (%d req / %ds)", key, hits.size(), effectiveWindowSeconds);

                requestContext.abortWith(
                        Response.status(429)
                                .header("Retry-After", Math.max(1, retryAfter))
                                .entity(ErrorResponse.of(
                                        ErrorCode.TOO_MANY_REQUESTS,
                                        "Tente novamente em " + Math.max(1, retryAfter) + " segundos"))
                                .type(MediaType.APPLICATION_JSON)
                                .build());
                return;
            }

            hits.addLast(now);
        }
    }

    /**
     * Resolve a anotacao {@link RateLimited} efetiva do endpoint invocado,
     * priorizando a do metodo sobre a da classe (mesma precedencia do JAX-RS
     * para outras anotacoes de metodo/classe).
     */
    private RateLimited resolveAnnotation() {
        if (resourceInfo == null) {
            return null;
        }
        Method method = resourceInfo.getResourceMethod();
        if (method != null && method.isAnnotationPresent(RateLimited.class)) {
            return method.getAnnotation(RateLimited.class);
        }
        Class<?> resourceClass = resourceInfo.getResourceClass();
        if (resourceClass != null && resourceClass.isAnnotationPresent(RateLimited.class)) {
            return resourceClass.getAnnotation(RateLimited.class);
        }
        return null;
    }

    /**
     * Resolve o IP do cliente. X-Forwarded-For so e honrado quando a aplicacao
     * esta configurada para confiar em um proxy reverso
     * (`quarkus.http.proxy.proxy-address-forwarding=true`); caso contrario o
     * header e ignorado (poderia ser forjado pelo cliente) e usamos o IP
     * remoto real da conexao.
     */
    private String resolveClientIp() {
        String forwardedFor = proxyAddressForwardingEnabled
                ? httpServerRequest.getHeader("X-Forwarded-For")
                : null;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            return (comma > 0 ? forwardedFor.substring(0, comma) : forwardedFor).trim();
        }
        return httpServerRequest.remoteAddress() != null
                ? httpServerRequest.remoteAddress().hostAddress()
                : "desconhecido";
    }
}
