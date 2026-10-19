package br.com.unify.matchable.community.resources;

import java.util.NoSuchElementException;
import java.util.function.Supplier;
import br.com.unify.matchable.common.dto.ErrorResponse;
import br.com.unify.matchable.community.services.ImageDescriptionGenerationService.Result;
import br.com.unify.matchable.community.services.ImageVisionService.VisionException;
import jakarta.ws.rs.core.Response;

/** One response shape for every image-description endpoint: sanitized errors and no caching. */
public final class ImageDescriptionResponses {
    private ImageDescriptionResponses() {}

    /** {@code source}: "AI" when generated now, "SAVED" when a stored description was reused. */
    public record DescriptionResponse(String description, String source) {}

    public static Response respond(Supplier<Result> work) {
        try {
            Result result = work.get();
            return Response.ok(new DescriptionResponse(result.description(), result.generated() ? "AI" : "SAVED"))
                .header("Cache-Control", "no-store").build();
        } catch (NoSuchElementException e) {
            return error(404, "Imagem não encontrada ou acesso indisponível.");
        } catch (VisionException e) {
            return error(e.status(), e.getMessage());
        }
    }

    private static Response error(int status, String message) {
        return Response.status(status).entity(ErrorResponse.of(status, "IMAGE_DESCRIPTION_UNAVAILABLE", message))
            .header("Cache-Control", "no-store").build();
    }
}
