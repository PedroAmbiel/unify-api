package br.com.unify.matchable.common.filters;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.user.services.UserActivityTracker;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

/**
 * Semana 06 (B): o access token vive até 60 minutos e o banimento só revoga os
 * refresh tokens. Sem este filtro, um usuário banido continuaria navegando com
 * o token emitido antes do ban até ele expirar.
 *
 * Roda depois da autenticação, em toda requisição com JWT válido: uma consulta
 * por projeção (não carrega a entidade nem o perfil) que só devolve linha para
 * conta banida. Se pesar, trocar por cache curto invalidado no ban.
 *
 * Semana 07: é também o ponto único que marca o dia de uso da pessoa para os
 * indicadores de atividade do dashboard (no máximo uma escrita por dia).
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 10)
public class BannedUserFilter implements ContainerRequestFilter {

    @Inject
    EntityManager entityManager;

    @Inject
    UserActivityTracker activityTracker;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        SecurityContext securityContext = requestContext.getSecurityContext();
        if (securityContext == null || !(securityContext.getUserPrincipal() instanceof JsonWebToken token)) {
            return; // rota pública ou requisição sem token: nada a validar
        }

        UUID userId = parseSubject(token.getSubject());
        if (userId == null) {
            return; // subject malformado fica a cargo da camada de segurança
        }

        List<String> banReasons = entityManager
                .createQuery("select u.banReason from User u where u.id = :id and u.banned = true", String.class)
                .setParameter("id", userId)
                .getResultList();

        if (!banReasons.isEmpty()) {
            requestContext.abortWith(Response.status(ErrorCode.USER_DISABLED.getHttpStatus())
                    .entity(ErrorResponse.of(ErrorCode.USER_DISABLED, banReasons.get(0)))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
            return;
        }

        activityTracker.recordActivity(userId);
    }

    private static UUID parseSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
