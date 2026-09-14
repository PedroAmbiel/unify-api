package br.com.unify.matchable.social.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.common.exceptions.ForbiddenException;
import br.com.unify.matchable.common.exceptions.PayloadTooLargeException;
import br.com.unify.matchable.common.image.ImageResponses;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
import br.com.unify.matchable.social.services.SocialService;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

/**
 * Seguir/deixar de seguir, listas de seguidores e feed pessoal.
 *
 * ROTEAMENTO (fato testado, ver RoutingContractTest): RESTEasy Reactive escolhe a
 * classe pelo prefixo literal mais longo e não faz backtracking. Por isso as
 * rotas pessoais são {@code /users/feed|posts|following|followers} e NUNCA
 * {@code /users/me/...} — qualquer {@code /users/me/x} cai em
 * UserProfileResource ({@code @Path("/users/me")}) e devolve 404.
 * Regra: nunca criar outra classe cujo {@code @Path} seja prefixo-mais-longo
 * de rotas servidas por esta.
 *
 * Os try/catch locais existem porque SocialResourceTest chama os métodos
 * direto (sem GlobalExceptionMapper), igual ao CommunityResource.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class SocialResource extends AuthenticatedResource {

    @Inject
    SocialService socialService;

    @Context
    Request request;

    @ConfigProperty(name = "unify.upload.image.max-bytes", defaultValue = "5242880")
    long maxImageBytes;

    // ------------------------------------------------------------- follow

    @POST
    @Path("/{userProfileId}/follow")
    @Transactional
    public Response follow(@PathParam("userProfileId") UUID userProfileId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(socialService.follow(user, userProfileId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @DELETE
    @Path("/{userProfileId}/follow")
    @Transactional
    public Response unfollow(@PathParam("userProfileId") UUID userProfileId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(socialService.unfollow(user, userProfileId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/following")
    @Transactional
    public Response listFollowing(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(socialService.listFollowing(user, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/followers")
    @Transactional
    public Response listFollowers(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(socialService.listFollowers(user, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/{userProfileId}/follow-stats")
    @Transactional
    public Response getFollowStats(@PathParam("userProfileId") UUID userProfileId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(socialService.getFollowStats(user, userProfileId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    // -------------------------------------------------------------- posts

    @POST
    @Path("/posts")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response createPost(@RestForm("body") String body, @RestForm("image") FileUpload image) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.status(Response.Status.CREATED)
                    .entity(socialService.createPost(user, body, readOptionalUploadedBytes(image)))
                    .build();
        } catch (PayloadTooLargeException exception) {
            return payloadTooLargeResponse(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        }
    }

    @DELETE
    @Path("/posts/{postId}")
    @Transactional
    public Response deletePost(@PathParam("postId") UUID postId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            socialService.deletePost(user, postId);
            return Response.noContent().build();
        } catch (ForbiddenException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/feed")
    @Transactional
    public Response getFeed(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(socialService.getFeed(user, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/posts/{postId}/media")
    @Produces("image/jpeg")
    @Transactional
    public Response getPostMedia(@PathParam("postId") UUID postId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return ImageResponses.jpeg(socialService.getPostMedia(user, postId), request);
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/{userProfileId}/posts")
    @Transactional
    public Response listPostsByProfile(
            @PathParam("userProfileId") UUID userProfileId,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(socialService.listPostsByProfile(user, userProfileId, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    // ------------------------------------------------------------ helpers

    protected byte[] readOptionalUploadedBytes(FileUpload image) {
        if (image == null || image.uploadedFile() == null) {
            return null;
        }

        if (image.size() > maxImageBytes) {
            throw new PayloadTooLargeException(
                    "A imagem enviada tem " + image.size()
                            + " bytes e o limite e de " + maxImageBytes + " bytes"
            );
        }

        try {
            byte[] bytes = Files.readAllBytes(image.uploadedFile());
            return bytes.length == 0 ? null : bytes;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível ler a imagem enviada", exception);
        }
    }

    private Response validationErrorResponse(String details) {
        return errorResponse(Response.Status.BAD_REQUEST, ErrorResponse.of(ErrorCode.VALIDATION_INVALID_FORMAT, details));
    }

    private Response conflictResponse(String details) {
        return errorResponse(Response.Status.CONFLICT, ErrorResponse.of(ErrorCode.RESOURCE_CONFLICT, details));
    }

    private Response payloadTooLargeResponse(String details) {
        return Response.status(ErrorCode.VALIDATION_FILE_TOO_LARGE.getHttpStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of(ErrorCode.VALIDATION_FILE_TOO_LARGE, details))
                .build();
    }

    private Response forbiddenResponse(String details) {
        return errorResponse(Response.Status.FORBIDDEN, ErrorResponse.of(ErrorCode.AUTH_FORBIDDEN, details));
    }

    private Response resourceNotFoundResponse(String details) {
        return errorResponse(Response.Status.NOT_FOUND, ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, details));
    }

    /** {@code type(JSON)} explícito porque getPostMedia declara {@code @Produces("image/jpeg")}. */
    private Response errorResponse(Response.Status status, ErrorResponse body) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
