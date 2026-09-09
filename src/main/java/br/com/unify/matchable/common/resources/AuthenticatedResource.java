package br.com.unify.matchable.common.resources;

import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.user.entity.User;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/**
 * Base para resources autenticados: unifica a leitura do usuário corrente a
 * partir do JWT e a resposta padrão de "usuário não encontrado", hoje
 * replicadas em ChatResource, CommunityResource,
 * UserAccessibilitySettingsResource, UserMatchResource e UserProfileResource.
 *
 * As cinco implementações de {@code findCurrentUser()} eram idênticas
 * ({@code User.findById(UUID.fromString(jwt.getSubject()))}) — sem
 * divergência de semântica a preservar.
 *
 * {@code findCurrentUser()} e {@code userNotFoundResponse()} são
 * {@code protected} e não-final DE PROPÓSITO: vários testes de resource
 * sobrescrevem {@code findCurrentUser()} via subclasse anônima para injetar
 * um usuário de teste sem passar por JWT real (ex.:
 * UserMatchResourceTest:190-197, CommunityResourceTest, UserProfileResourceTest).
 *
 * {@code userNotFoundResponse()} é byte-idêntico ao que
 * CommunityResource, UserAccessibilitySettingsResource, UserMatchResource e
 * UserProfileResource já faziam (404 + {@code ErrorCode.USER_NOT_FOUND},
 * sem {@code Content-Type} explícito). Esse é o contrato usado pelo
 * frontend ({@code interceptors.ts:17-40}) para deslogar automaticamente
 * quando o usuário autenticado foi removido — NÃO alterar status nem code
 * sem atualizar esse contrato.
 *
 * ChatResource é a única exceção: ela define o {@code Content-Type} da
 * resposta de erro explicitamente como JSON (necessário porque um de seus
 * endpoints declara {@code @Produces(APPLICATION_OCTET_STREAM)} e, sem essa
 * marcação explícita, a negociação de conteúdo do JAX-RS herdaria o tipo do
 * método). Por isso ChatResource sobrescreve
 * {@code userNotFoundResponse()} localmente em vez de usar esta versão base.
 */
public abstract class AuthenticatedResource {

    @Inject
    protected JsonWebToken jwt;

    protected User findCurrentUser() {
        return User.findById(UUID.fromString(jwt.getSubject()));
    }

    protected Response userNotFoundResponse() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(ErrorCode.USER_NOT_FOUND))
                .build();
    }
}
