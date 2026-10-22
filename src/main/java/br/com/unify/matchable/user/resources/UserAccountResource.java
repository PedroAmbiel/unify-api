package br.com.unify.matchable.user.resources;

import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.auth.services.TokenService;
import br.com.unify.matchable.common.filters.RateLimited;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.user.dto.UserAccountUpdateRequest;
import br.com.unify.matchable.user.dto.UserPasswordChangeRequest;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.UserAccountService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Dados da conta e troca de senha do usuário autenticado (tela "Minha conta"
 * do backoffice). Classe própria com o caminho completo pela regra de
 * roteamento do projeto: {@code UserProfileResource @Path("/users/me")}
 * sombrearia {@code /users/me/...} declarado em outra classe
 * {@code @Path("/users")}. A leitura continua em {@code GET /users/me}.
 */
@Path("/users/me/account")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserAccountResource extends AuthenticatedResource {

    @Inject
    UserAccountService userAccountService;

    @Inject
    TokenService tokenService;

    @PUT
    @Transactional
    public Response updateAccount(UserAccountUpdateRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userAccountService.updateAccount(user, request)).build();
    }

    /**
     * Troca a senha conferindo a atual. Todas as sessões da conta (inclusive o
     * app no celular) são encerradas, como na redefinição por e-mail; quem
     * trocou recebe tokens novos e continua logado. O access token não carrega
     * o id da sessão, então não há como poupar só a sessão corrente.
     * Rate limit curto: o endpoint confere senha e não pode virar força bruta.
     */
    @PUT
    @Path("/password")
    @RateLimited(limit = 5, windowSeconds = 60)
    @Transactional
    public Response changePassword(UserPasswordChangeRequest request,
                                   @HeaderParam("User-Agent") String userAgent,
                                   @HeaderParam("X-Forwarded-For") String forwardedFor) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        userAccountService.changePassword(user, request);
        tokenService.revokeAllConnections(user);
        TokenResponse tokens = tokenService.generateTokens(user, userAgent, forwardedFor);
        return Response.ok(tokens).build();
    }
}
