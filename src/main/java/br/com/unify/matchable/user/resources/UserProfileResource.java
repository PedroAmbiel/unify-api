package br.com.unify.matchable.user.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.user.dto.UserMatchPreferencesUpsertRequest;
import br.com.unify.matchable.user.dto.UserProfileUpsertRequest;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.UserProfileService;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/users/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserProfileResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    UserProfileService userProfileService;

    @GET
    @Path("/profile")
    @Transactional
    public Response getProfile() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userProfileService.getProfile(user)).build();
    }

    @PUT
    @Path("/profile")
    @Transactional
    public Response saveProfile(UserProfileUpsertRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userProfileService.saveProfile(user, request)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/match-preferences")
    @Transactional
    public Response getMatchPreferences() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userProfileService.getMatchPreferences(user)).build();
    }

    @PUT
    @Path("/match-preferences")
    @Transactional
    public Response saveMatchPreferences(UserMatchPreferencesUpsertRequest request) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userProfileService.saveMatchPreferences(user, request)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/profile/completion")
    @Transactional
    public Response getCompletionStatus() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userProfileService.getCompletionStatus(user)).build();
    }

    @GET
    @Path("/profile/options")
    @Transactional
    public Response getProfileOptions() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userProfileService.getProfileOptions()).build();
    }

    @GET
    @Path("/profile/images")
    @Transactional
    public Response getProfileImages() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userProfileService.getActiveImages(user)).build();
    }

    @POST
    @Path("/profile/images/profile-picture")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response uploadProfilePicture(@RestForm("image") FileUpload image) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userProfileService.uploadProfilePicture(user, readUploadedBytes(image))).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        }
    }

    @POST
    @Path("/profile/images/gallery")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response uploadGalleryImage(@RestForm("image") FileUpload image) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userProfileService.uploadGalleryImage(user, readUploadedBytes(image))).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        }
    }

    @DELETE
    @Path("/profile/images/{imageId}")
    @Transactional
    public Response deactivateImage(@PathParam("imageId") UUID imageId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userProfileService.deactivateImage(user, imageId)).build();
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/profile/images/{imageId}")
    @Produces("image/jpeg")
    @Transactional
    public Response getProfileImage(@PathParam("imageId") UUID imageId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userProfileService.getImageContent(user, imageId)).type("image/jpeg").build();
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    protected User findCurrentUser() {
        return User.findById(UUID.fromString(jwt.getSubject()));
    }

    protected byte[] readUploadedBytes(FileUpload image) {
        if (image == null || image.uploadedFile() == null) {
            throw new IllegalArgumentException("Nenhuma imagem foi enviada no campo 'image'");
        }

        try {
            byte[] bytes = Files.readAllBytes(image.uploadedFile());
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Nenhuma imagem foi enviada no campo 'image'");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível ler a imagem enviada", exception);
        }
    }

    private Response userNotFoundResponse() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(ErrorCode.USER_NOT_FOUND))
                .build();
    }

    private Response resourceNotFoundResponse(String details) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, details))
                .build();
    }

    private Response conflictResponse(String details) {
        return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.of(ErrorCode.RESOURCE_CONFLICT, details))
                .build();
    }

    private Response validationErrorResponse(String details) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(ErrorCode.VALIDATION_INVALID_FORMAT, details))
                .build();
    }
}