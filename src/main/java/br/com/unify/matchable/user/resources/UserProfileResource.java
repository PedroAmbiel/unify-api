package br.com.unify.matchable.user.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.common.image.ImageResponses;
import br.com.unify.matchable.common.exceptions.PayloadTooLargeException;
import br.com.unify.matchable.common.media.AudioContent;
import br.com.unify.matchable.common.resources.AuthenticatedResource;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

/**
 * NOTA: os try/catch de NoSuchElementException/IllegalStateException/
 * PayloadTooLargeException abaixo são redundantes com
 * {@code GlobalExceptionMapper} para uma requisição HTTP real, mas foram
 * mantidos porque UserProfileResourceTest chama os métodos deste resource
 * diretamente em Java (TestableUserProfileResource), sem passar pelo
 * pipeline de exception mappers do JAX-RS.
 */
@Path("/users/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserProfileResource extends AuthenticatedResource {

    @Inject
    UserProfileService userProfileService;

    @Context
    Request request;

    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "unify.upload.image.max-bytes",
            defaultValue = "5242880"
    )
    long maxImageBytes;

    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "unify.profile.audio.max-size-bytes",
            defaultValue = "3145728"
    )
    long maxPresentationAudioBytes;

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
    @Path("/profile/public")
    @Transactional
    public Response getPublicProfile(@QueryParam("userProfileId") UUID userProfileId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userProfileService.getPublicProfile(user, userProfileId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/profile/public/images")
    @Transactional
    public Response getPublicGalleryImages(@QueryParam("userProfileId") UUID userProfileId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(userProfileService.getPublicGalleryImages(user, userProfileId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    @GET
    @Path("/profile/public/images/{imageId}")
    @Produces("image/jpeg")
    @Transactional
    public Response getPublicGalleryImage(
            @QueryParam("userProfileId") UUID userProfileId,
            @PathParam("imageId") UUID imageId
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return ImageResponses.jpeg(userProfileService.getPublicGalleryImageContent(user, userProfileId, imageId), request);
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
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
        } catch (PayloadTooLargeException exception) {
            return payloadTooLargeResponse(exception.getMessage());
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
        } catch (PayloadTooLargeException exception) {
            return payloadTooLargeResponse(exception.getMessage());
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
            return ImageResponses.jpeg(userProfileService.getImageContent(user, imageId), request);
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    // ------------------------------------------- áudio de apresentação (frente D)

    @GET
    @Path("/profile/audio")
    @Transactional
    public Response getPresentationAudio() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }
        return Response.ok(userProfileService.getPresentationAudio(user)).build();
    }

    /** Multipart: {@code audio} (arquivo) + {@code durationSeconds} (1..60). Substitui o anterior. */
    @PUT
    @Path("/profile/audio")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response uploadPresentationAudio(
            @RestForm("audio") FileUpload audio,
            @RestForm("durationSeconds") Integer durationSeconds
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            byte[] bytes = readUploadedAudioBytes(audio);
            String contentType = audio == null ? null : audio.contentType();
            return Response.ok(userProfileService.uploadPresentationAudio(
                    user, contentType, bytes, durationSeconds)).build();
        } catch (PayloadTooLargeException exception) {
            return payloadTooLargeResponse(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        }
    }

    @DELETE
    @Path("/profile/audio")
    @Transactional
    public Response deletePresentationAudio() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            userProfileService.deletePresentationAudio(user);
            return Response.noContent().build();
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    /** Bytes do MEU áudio. {@code @Produces} curinga + {@code type} explícito (ver ImageResponses.binary). */
    @GET
    @Path("/profile/audio/{audioId}")
    @Produces("*/*")
    @Transactional
    public Response getPresentationAudioContent(@PathParam("audioId") UUID audioId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            AudioContent content = userProfileService.getPresentationAudioContent(user, audioId);
            return ImageResponses.binary(content.bytes(), content.contentType(), request);
        } catch (NoSuchElementException exception) {
            return jsonError(resourceNotFoundResponse(exception.getMessage()));
        }
    }

    /** Bytes do áudio de OUTRO perfil — mesma guarda do perfil público (verificado, bloqueio). */
    @GET
    @Path("/profile/public/audio/{audioId}")
    @Produces("*/*")
    @Transactional
    public Response getPublicPresentationAudioContent(
            @QueryParam("userProfileId") UUID userProfileId,
            @PathParam("audioId") UUID audioId
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            AudioContent content = userProfileService.getPublicPresentationAudioContent(user, userProfileId, audioId);
            return ImageResponses.binary(content.bytes(), content.contentType(), request);
        } catch (IllegalArgumentException exception) {
            return jsonError(validationErrorResponse(exception.getMessage()));
        } catch (NoSuchElementException exception) {
            return jsonError(resourceNotFoundResponse(exception.getMessage()));
        }
    }

    /** Erro de endpoint binário: sem o tipo explícito a negociação herdaria o curinga do método. */
    private Response jsonError(Response response) {
        return Response.fromResponse(response).type(MediaType.APPLICATION_JSON).build();
    }

    protected byte[] readUploadedAudioBytes(FileUpload audio) {
        if (audio == null || audio.uploadedFile() == null) {
            throw new IllegalArgumentException("Nenhum áudio foi enviado no campo 'audio'");
        }

        if (audio.size() > maxPresentationAudioBytes) {
            throw new PayloadTooLargeException(
                    "O áudio enviado tem " + audio.size()
                            + " bytes e o limite é de " + maxPresentationAudioBytes + " bytes"
            );
        }

        try {
            byte[] bytes = Files.readAllBytes(audio.uploadedFile());
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Nenhum áudio foi enviado no campo 'audio'");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível ler o áudio enviado", exception);
        }
    }

    protected byte[] readUploadedBytes(FileUpload image) {
        if (image == null || image.uploadedFile() == null) {
            throw new IllegalArgumentException("Nenhuma imagem foi enviada no campo 'image'");
        }

        if (image.size() > maxImageBytes) {
            throw new PayloadTooLargeException(
                    "A imagem enviada tem " + image.size()
                            + " bytes e o limite e de " + maxImageBytes + " bytes"
            );
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

    private Response payloadTooLargeResponse(String details) {
        return Response.status(ErrorCode.VALIDATION_FILE_TOO_LARGE.getHttpStatus())
                .entity(ErrorResponse.of(ErrorCode.VALIDATION_FILE_TOO_LARGE, details))
                .build();
    }
}
