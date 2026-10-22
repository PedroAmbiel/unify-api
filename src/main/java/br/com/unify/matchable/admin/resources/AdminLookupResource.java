package br.com.unify.matchable.admin.resources;

import java.util.NoSuchElementException;

import br.com.unify.matchable.admin.dto.AdminLookupActiveRequest;
import br.com.unify.matchable.admin.dto.AdminLookupItemRequest;
import br.com.unify.matchable.admin.enums.LookupType;
import br.com.unify.matchable.admin.services.AdminLookupService;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Backoffice — catálogos de seleção de perfil (semana 06). Um resource para os
 * 12 catálogos: {@code lookupType} é o código público de {@link LookupType}
 * (genders, pronouns, disabilities, accessibility-needs, autonomy-levels,
 * communication-forms, lifestyle-types, love-languages, energy-levels,
 * interest-types, connection-types, community-categories).
 *
 * Ativar/desativar é {@code PUT .../active} (não PATCH): o CORS e os clientes
 * HTTP do projeto só falam GET/POST/PUT/DELETE. Erros de regra saem pelo
 * GlobalExceptionMapper (400/404/409).
 */
@Path("/admin/lookups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminLookupResource extends AuthenticatedResource {

    @Inject
    AdminLookupService adminLookupService;

    @GET
    public Response listCatalogs() {
        return Response.ok(adminLookupService.listCatalogs()).build();
    }

    @GET
    @Path("/{lookupType}")
    public Response listItems(@PathParam("lookupType") String lookupType,
                              @QueryParam("includeInactive") @DefaultValue("true") boolean includeInactive) {
        return Response.ok(adminLookupService.listItems(requireType(lookupType), includeInactive)).build();
    }

    @POST
    @Path("/{lookupType}")
    @Transactional
    public Response create(@PathParam("lookupType") String lookupType, AdminLookupItemRequest request) {
        LookupType type = requireType(lookupType);
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        return Response.status(Response.Status.CREATED)
                .entity(adminLookupService.create(admin, type, request))
                .build();
    }

    @PUT
    @Path("/{lookupType}/{id}")
    @Transactional
    public Response update(@PathParam("lookupType") String lookupType,
                           @PathParam("id") Integer id,
                           AdminLookupItemRequest request) {
        LookupType type = requireType(lookupType);
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        return Response.ok(adminLookupService.update(admin, type, id, request)).build();
    }

    @PUT
    @Path("/{lookupType}/{id}/active")
    @Transactional
    public Response setActive(@PathParam("lookupType") String lookupType,
                              @PathParam("id") Integer id,
                              AdminLookupActiveRequest request) {
        LookupType type = requireType(lookupType);
        if (request == null || request.active() == null) {
            throw new IllegalArgumentException("Informe o campo 'active' (true ou false)");
        }
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        return Response.ok(adminLookupService.setActive(admin, type, id, request.active())).build();
    }

    @DELETE
    @Path("/{lookupType}/{id}")
    @Transactional
    public Response delete(@PathParam("lookupType") String lookupType, @PathParam("id") Integer id) {
        LookupType type = requireType(lookupType);
        User admin = findCurrentUser();
        if (admin == null) {
            return userNotFoundResponse();
        }
        adminLookupService.delete(admin, type, id);
        return Response.noContent().build();
    }

    private static LookupType requireType(String lookupType) {
        return LookupType.fromCode(lookupType)
                .orElseThrow(() -> new NoSuchElementException("Catálogo '" + lookupType + "' não existe"));
    }
}
