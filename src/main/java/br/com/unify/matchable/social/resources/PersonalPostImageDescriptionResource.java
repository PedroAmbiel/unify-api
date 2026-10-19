package br.com.unify.matchable.social.resources;

import java.util.UUID;
import br.com.unify.matchable.common.filters.RateLimited;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.community.resources.ImageDescriptionResponses;
import br.com.unify.matchable.community.services.ImageDescriptionGenerationService;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Personal-post twin of CommunityPostImageDescriptionResource; visibility follows getPostMedia. */
@Path("/users/posts/{postId}/image-description")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class PersonalPostImageDescriptionResource extends AuthenticatedResource {
    @Inject ImageDescriptionGenerationService descriptions;

    @POST
    @RateLimited(limit = 6, windowSeconds = 60)
    public Response describe(@PathParam("postId") UUID postId) {
        User user = findCurrentUser();
        if (user == null) return userNotFoundResponse();
        return ImageDescriptionResponses.respond(() -> descriptions.describePersonalPost(user, postId));
    }
}
