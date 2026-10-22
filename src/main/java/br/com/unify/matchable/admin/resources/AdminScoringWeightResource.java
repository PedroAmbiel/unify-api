package br.com.unify.matchable.admin.resources;

import java.util.NoSuchElementException;

import br.com.unify.matchable.admin.dto.ScoringWeightsUpdateRequest;
import br.com.unify.matchable.admin.services.AdminScoringWeightService;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.scoring.enums.ScoringGroup;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Backoffice — pesos de pontuação: Encontros ({@code match}), feed da aba
 * Início ({@code feed}) e descoberta de comunidades
 * ({@code community-discovery}). O PUT vale na hora para as próximas buscas:
 * grava no banco e troca a memória da API depois do commit.
 */
@Path("/admin/scoring-weights")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminScoringWeightResource extends AuthenticatedResource {

    @Inject
    AdminScoringWeightService adminScoringWeightService;

    @GET
    public Response listGroups() {
        return Response.ok(adminScoringWeightService.listGroups()).build();
    }

    @GET
    @Path("/{group}")
    public Response getGroup(@PathParam("group") String group) {
        return Response.ok(adminScoringWeightService.getGroup(requireGroup(group))).build();
    }

    @PUT
    @Path("/{group}")
    @Transactional
    public Response update(@PathParam("group") String group, ScoringWeightsUpdateRequest request) {
        ScoringGroup scoringGroup = requireGroup(group);
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        return Response.ok(adminScoringWeightService.update(admin, scoringGroup, request)).build();
    }

    private static ScoringGroup requireGroup(String group) {
        return ScoringGroup.fromCode(group)
                .orElseThrow(() -> new NoSuchElementException("Grupo de pesos '" + group + "' não existe"));
    }
}
