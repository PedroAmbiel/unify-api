package br.com.unify.matchable.chat.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import br.com.unify.matchable.chat.dto.ChatMediaPayload;
import br.com.unify.matchable.chat.dto.ChatMessageCreateRequest;
import br.com.unify.matchable.chat.services.ChatMediaValidator;
import br.com.unify.matchable.chat.services.ChatService;
import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.user.entity.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/chats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class ChatResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    ChatService chatService;

    @Inject
    ChatMediaValidator chatMediaValidator;

    // GET /chats
    @GET
    @Transactional
    public Response listConversations() {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(chatService.listConversations(user)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        }
    }

    // POST /chats?matchId=...
    // Sem corpo: @Consumes(WILDCARD) evita 415 quando o cliente não manda Content-Type.
    @POST
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response openConversation(@QueryParam("matchId") UUID matchId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(chatService.openConversation(user, matchId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (IllegalStateException exception) {
            return conflictResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    // GET /chats/{conversationId}/messages?page=&size=&since=
    @GET
    @Path("/{conversationId}/messages")
    @Transactional
    public Response getMessages(
            @PathParam("conversationId") UUID conversationId,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @QueryParam("since") String since
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(
                    chatService.getMessages(user, conversationId, page, size, parseInstant(since))
            ).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    // POST /chats/{conversationId}/messages  (texto)
    @POST
    @Path("/{conversationId}/messages")
    @Transactional
    public Response sendTextMessage(
            @PathParam("conversationId") UUID conversationId,
            ChatMessageCreateRequest request
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.status(Response.Status.CREATED)
                    .entity(chatService.sendTextMessage(
                            user, conversationId, request == null ? null : request.body()))
                    .build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    // POST /chats/{conversationId}/messages/media  (multipart)
    @POST
    @Path("/{conversationId}/messages/media")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response sendMediaMessage(
            @PathParam("conversationId") UUID conversationId,
            @RestForm("type") String type,
            @RestForm("file") FileUpload file,
            @RestForm("caption") String caption,
            @RestForm("durationSeconds") Integer durationSeconds
    ) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            // Valida ANTES de tocar no banco.
            ChatMediaPayload payload = chatMediaValidator.validate(
                    type,
                    file == null ? null : file.contentType(),
                    readUploadedBytes(file),
                    durationSeconds
            );

            return Response.status(Response.Status.CREATED)
                    .entity(chatService.sendMediaMessage(user, conversationId, payload, caption))
                    .build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    // GET /chats/messages/{messageId}/media
    @GET
    @Path("/messages/{messageId}/media")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public Response getMessageMedia(@PathParam("messageId") UUID messageId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            ChatMediaPayload payload = chatService.getMessageMedia(user, messageId);
            return Response.ok(payload.bytes())
                    .type(payload.contentType() == null
                            ? MediaType.APPLICATION_OCTET_STREAM
                            : payload.contentType())
                    .header("Content-Length", payload.bytes().length)
                    // Mídia de chat é imutável: cache agressivo no cliente, privado.
                    .header("Cache-Control", "private, max-age=31536000, immutable")
                    .build();
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        }
    }

    // PUT /chats/{conversationId}/read
    // Sem corpo: @Consumes(WILDCARD) evita 415 quando o cliente não manda Content-Type.
    @PUT
    @Path("/{conversationId}/read")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response markAsRead(@PathParam("conversationId") UUID conversationId) {
        User user = findCurrentUser();
        if (user == null) {
            return userNotFoundResponse();
        }

        try {
            return Response.ok(chatService.markConversationAsRead(user, conversationId)).build();
        } catch (IllegalArgumentException exception) {
            return validationErrorResponse(exception.getMessage());
        } catch (SecurityException exception) {
            return forbiddenResponse(exception.getMessage());
        } catch (NoSuchElementException exception) {
            return resourceNotFoundResponse(exception.getMessage());
        }
    }

    // ---- apoio ----------------------------------------------------------

    protected User findCurrentUser() {
        return User.findById(UUID.fromString(jwt.getSubject()));
    }

    protected Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "O parâmetro 'since' deve estar no formato ISO-8601 (ex.: 2025-09-02T14:31:02.104Z)",
                    exception);
        }
    }

    protected byte[] readUploadedBytes(FileUpload file) {
        if (file == null || file.uploadedFile() == null) {
            return null;
        }

        try {
            return Files.readAllBytes(file.uploadedFile());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível ler o arquivo enviado", exception);
        }
    }

    private Response userNotFoundResponse() {
        return errorResponse(Response.Status.NOT_FOUND, ErrorResponse.of(ErrorCode.USER_NOT_FOUND));
    }

    private Response validationErrorResponse(String details) {
        return errorResponse(
                Response.Status.BAD_REQUEST,
                ErrorResponse.of(ErrorCode.VALIDATION_INVALID_FORMAT, details));
    }

    private Response forbiddenResponse(String details) {
        return errorResponse(
                Response.Status.FORBIDDEN,
                ErrorResponse.of(ErrorCode.AUTH_FORBIDDEN, details));
    }

    private Response conflictResponse(String details) {
        return errorResponse(
                Response.Status.CONFLICT,
                ErrorResponse.of(ErrorCode.RESOURCE_CONFLICT, details));
    }

    private Response resourceNotFoundResponse(String details) {
        return errorResponse(
                Response.Status.NOT_FOUND,
                ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, details));
    }

    /**
     * O corpo de erro é sempre JSON, inclusive nos endpoints que declaram
     * {@code @Produces(APPLICATION_OCTET_STREAM)} — sem o {@code type()} explícito o
     * cliente receberia um ErrorResponse rotulado como octet-stream.
     */
    private Response errorResponse(Response.Status status, ErrorResponse body) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
