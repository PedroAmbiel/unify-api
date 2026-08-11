package br.com.unify.matchable.community.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.common.exceptions.PayloadTooLargeException;
import br.com.unify.matchable.common.image.ImageResponses;
import br.com.unify.matchable.community.dto.CommunityCommentCreateRequest;
import br.com.unify.matchable.community.dto.CommunityMemberRoleUpdateRequest;
import br.com.unify.matchable.community.services.CommunityService;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

@Path("/communities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class CommunityResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    CommunityService communityService;

    @Context
    Request request;

    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "unify.upload.image.max-bytes",
            defaultValue = "5242880"
    )
    long maxImageBytes;

    @GET
    @Transactional
    public Response listCommunities(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.listCommunities(user, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/search")
    @Transactional
    public Response searchCommunities(
            @QueryParam("query") String query,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.searchCommunities(user, query, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response createCommunity(
            @RestForm("name") String name,
            @RestForm("description") String description,
            @RestForm("icon") FileUpload icon
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.status(Response.Status.CREATED)
                    .entity(communityService.createCommunity(user, name, description, readOptionalUploadedBytes(icon)))
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
    @Path("/{communityId}")
    @Transactional
    public Response deleteCommunity(@PathParam("communityId") UUID communityId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            communityService.deleteCommunity(user, communityId);
            return Response.noContent().build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/feed")
    @Transactional
    public Response getFeed(
            @QueryParam("communityId") UUID communityId,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.getFeed(user, communityId, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @POST
    @Path("/membership")
    @Transactional
    public Response joinCommunity(@QueryParam("communityId") UUID communityId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.joinCommunity(user, communityId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @DELETE
    @Path("/membership")
    @Transactional
    public Response leaveCommunity(@QueryParam("communityId") UUID communityId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.leaveCommunity(user, communityId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/{communityId}/members")
    @Transactional
    public Response listMembers(
            @PathParam("communityId") UUID communityId,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.listMembers(user, communityId, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @PUT
        @Path("/{communityId}/members/{userProfileId}/role")
    @Transactional
    public Response updateMemberRole(
            @PathParam("communityId") UUID communityId,
            @PathParam("userProfileId") UUID userProfileId,
            CommunityMemberRoleUpdateRequest request
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.updateMemberRole(
                    user,
                    communityId,
                    userProfileId,
                    request == null ? null : request.role()
            )).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @POST
    @Path("/posts")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response createPost(
            @QueryParam("communityId") UUID communityId,
            @RestForm("body") String body,
            @RestForm("image") FileUpload image
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.status(Response.Status.CREATED)
                    .entity(communityService.createPost(user, communityId, body, readOptionalUploadedBytes(image)))
                    .build();
        } catch (PayloadTooLargeException exception) {
            return payloadTooLargeResponse(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
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
            communityService.deletePost(user, postId);
            return Response.noContent().build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @POST
    @Path("/posts/{postId}/likes")
    @Transactional
    public Response likePost(@PathParam("postId") UUID postId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.likePost(user, postId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @DELETE
    @Path("/posts/{postId}/likes")
    @Transactional
    public Response unlikePost(@PathParam("postId") UUID postId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.unlikePost(user, postId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @DELETE
    @Path("/posts/{postId}/likes/{userId}")
    @Transactional
    public Response deleteLike(@PathParam("postId") UUID postId, @PathParam("userId") UUID targetUserId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.deleteLike(user, postId, targetUserId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/posts/{postId}/comments")
    @Transactional
    public Response getComments(
            @PathParam("postId") UUID postId,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(communityService.getComments(user, postId, page, size)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @POST
    @Path("/posts/{postId}/comments")
    @Transactional
    public Response createComment(@PathParam("postId") UUID postId, CommunityCommentCreateRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            String body = request == null ? null : request.body();
            return Response.status(Response.Status.CREATED)
                    .entity(communityService.createComment(user, postId, body))
                    .build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @DELETE
    @Path("/posts/{postId}/comments/{commentId}")
    @Transactional
    public Response deleteComment(@PathParam("postId") UUID postId, @PathParam("commentId") UUID commentId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            communityService.deleteComment(user, postId, commentId);
            return Response.noContent().build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/{communityId}/icon")
    @Produces("image/jpeg")
    @Transactional
    public Response getCommunityIcon(@PathParam("communityId") UUID communityId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return ImageResponses.jpeg(communityService.getCommunityIcon(communityId), request);
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
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
            return ImageResponses.jpeg(communityService.getPostMedia(postId), request);
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/users/{userId}/avatar")
    @Produces("image/jpeg")
    @Transactional
    public Response getAuthorAvatar(@PathParam("userId") UUID userId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return ImageResponses.jpeg(communityService.getAuthorAvatar(userId), request);
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    protected User findCurrentUser() {
        return User.findById(UUID.fromString(jwt.getSubject()));
    }

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

    private Response userNotFoundResponse() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(ErrorCode.USER_NOT_FOUND))
                .build();
    }

    private Response validationErrorResponse(String details) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(ErrorCode.VALIDATION_INVALID_FORMAT, details))
                .build();
    }

    private Response conflictResponse(String details) {
        return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.of(ErrorCode.RESOURCE_CONFLICT, details))
                .build();
    }

    private Response payloadTooLargeResponse(String details) {
        return Response.status(ErrorCode.VALIDATION_FILE_TOO_LARGE.getHttpStatus())
                .entity(ErrorResponse.of(ErrorCode.VALIDATION_FILE_TOO_LARGE, details))
                .build();
    }

    private Response forbiddenResponse(String details) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(ErrorResponse.of(ErrorCode.AUTH_FORBIDDEN, details))
                .build();
    }

    private Response resourceNotFoundResponse(String details) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, details))
                .build();
    }
}