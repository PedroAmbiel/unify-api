package br.com.unify.matchable.community.resources;

import java.util.NoSuchElementException;
import java.util.UUID;
import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.filters.RateLimited;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.community.services.CommunityService;
import br.com.unify.matchable.community.services.ImageVisionService;
import br.com.unify.matchable.community.services.ImageVisionService.VisionException;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/communities/posts/{postId}/comments/{commentId}/image-description")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class CommentImageDescriptionResource extends AuthenticatedResource {
    @Inject CommunityService communityService;
    @Inject ImageVisionService vision;

    @POST
    @RateLimited(limit = 6, windowSeconds = 60)
    public Response describe(@PathParam("postId") UUID postId, @PathParam("commentId") UUID commentId) {
        User user = findCurrentUser();
        if (user == null) return userNotFoundResponse();
        try {
            // Authorization and Blob loading finish before the outbound network call.
            byte[] image = loadImage(user, postId, commentId);
            return Response.ok(new DescriptionResponse(vision.describe(image), "AI"))
                .header("Cache-Control", "no-store").build();
        } catch (NoSuchElementException e) {
            return error(404, "Imagem não encontrada ou acesso indisponível.");
        } catch (VisionException e) {
            return error(e.status(), e.getMessage());
        }
    }

    protected byte[] loadImage(User user, UUID postId, UUID commentId) {
        return QuarkusTransaction.requiringNew().call(() -> communityService.getCommentMedia(user, postId, commentId));
    }

    private Response error(int status, String message) {
        return Response.status(status).entity(ErrorResponse.of(status, "IMAGE_DESCRIPTION_UNAVAILABLE", message))
            .header("Cache-Control", "no-store").build();
    }
    public record DescriptionResponse(String description, String source) {}
}
