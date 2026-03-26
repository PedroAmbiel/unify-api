package br.com.unify.matchable.user.resources;

import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;
import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/me")
    public Response getCurrentUser() {
        String userId = jwt.getSubject();
        User user = User.findById(UUID.fromString(userId));
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(Map.of(
                "id", user.id.toString(),
                "name", user.name,
                "lastName", user.lastName,
                "email", user.email != null ? user.email : "",
                "cellphone", user.cellphone != null ? user.cellphone : "",
                "subscriptionMethod", user.subscriptionMethod.name()
        )).build();
    }
}
