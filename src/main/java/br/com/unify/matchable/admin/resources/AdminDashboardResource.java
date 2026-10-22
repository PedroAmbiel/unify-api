package br.com.unify.matchable.admin.resources;

import br.com.unify.matchable.admin.services.AdminDashboardService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Backoffice — dashboard de métricas (semana 07). Somente leitura; parâmetro
 * inválido sai como 400 pelo GlobalExceptionMapper. Sem {@code @Transactional}
 * aqui: a transação fica no serviço, atrás do cache de 60 s.
 */
@Path("/admin/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminDashboardResource {

    @Inject
    AdminDashboardService dashboardService;

    @GET
    @Path("/summary")
    public Response getSummary() {
        return Response.ok(dashboardService.getSummary()).build();
    }

    /** {@code metric}: new_users, new_mutual_matches, messages_sent, posts_created ou reports_opened. */
    @GET
    @Path("/timeseries")
    public Response getTimeSeries(@QueryParam("metric") String metric, @QueryParam("days") Integer days) {
        return Response.ok(dashboardService.getTimeSeries(metric, days)).build();
    }

    @GET
    @Path("/top-communities")
    public Response getTopCommunities(@QueryParam("limit") Integer limit) {
        return Response.ok(dashboardService.getTopCommunities(limit)).build();
    }

    // ---------------------------------------------------- painéis por dimensão

    @GET
    @Path("/growth")
    public Response getGrowth(@QueryParam("days") Integer days,
                              @QueryParam("granularity") String granularity,
                              @QueryParam("inactivityDays") Integer inactivityDays,
                              @QueryParam("genderId") Integer genderId,
                              @QueryParam("disabilityId") Integer disabilityId) {
        return Response.ok(dashboardService.getGrowth(days, granularity, inactivityDays, genderId, disabilityId)).build();
    }

    @GET
    @Path("/matches")
    public Response getMatches(@QueryParam("days") Integer days,
                               @QueryParam("granularity") String granularity,
                               @QueryParam("connectionTypeId") Integer connectionTypeId) {
        return Response.ok(dashboardService.getMatches(days, granularity, connectionTypeId)).build();
    }

    @GET
    @Path("/posts")
    public Response getPosts(@QueryParam("days") Integer days,
                             @QueryParam("granularity") String granularity,
                             @QueryParam("origin") String origin,
                             @QueryParam("categoryId") Integer categoryId) {
        return Response.ok(dashboardService.getPosts(days, granularity, origin, categoryId)).build();
    }

    @GET
    @Path("/communities")
    public Response getCommunities(@QueryParam("days") Integer days,
                                   @QueryParam("granularity") String granularity,
                                   @QueryParam("categoryId") Integer categoryId,
                                   @QueryParam("privacy") String privacy) {
        return Response.ok(dashboardService.getCommunities(days, granularity, categoryId, privacy)).build();
    }
}
