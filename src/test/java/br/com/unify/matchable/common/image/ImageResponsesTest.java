package br.com.unify.matchable.common.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Variant;

class ImageResponsesTest {

    @Test
    void jpegReturnsEtagAndPrivateCacheControl() {
        Response response = ImageResponses.jpeg(new byte[] { 1, 2, 3 }, new StubRequest(null));

        assertEquals(200, response.getStatus());
        assertEquals("image/jpeg", response.getMediaType().toString());
        assertNotNull(response.getEntityTag());
        assertEquals(32, response.getEntityTag().getValue().length());
        String cacheControl = response.getHeaderString("Cache-Control");
        assertTrue(cacheControl.contains("private"), cacheControl);
        assertTrue(cacheControl.contains("max-age=86400"), cacheControl);
    }

    @Test
    void jpegReturnsNotModifiedWhenClientAlreadyHasTheSameEtag() {
        byte[] content = new byte[] { 4, 5, 6 };
        EntityTag currentTag = ImageResponses.jpeg(content, new StubRequest(null)).getEntityTag();

        Response response = ImageResponses.jpeg(content, new StubRequest(currentTag));

        assertEquals(304, response.getStatus());
        assertNull(response.getEntity());
        assertEquals(currentTag, response.getEntityTag());
        assertTrue(response.getHeaderString("Cache-Control").contains("max-age=86400"));
    }

    @Test
    void jpegReturnsNotFoundWhenContentIsMissing() {
        assertEquals(404, ImageResponses.jpeg(null, new StubRequest(null)).getStatus());
        assertEquals(404, ImageResponses.jpeg(new byte[0], new StubRequest(null)).getStatus());
    }

    @Test
    void jpegProducesDifferentEtagsForDifferentContent() {
        EntityTag first = ImageResponses.jpeg(new byte[] { 1 }, new StubRequest(null)).getEntityTag();
        EntityTag second = ImageResponses.jpeg(new byte[] { 2 }, new StubRequest(null)).getEntityTag();

        assertTrue(!first.equals(second));
    }

    /** Request minimo que simula o header {@code If-None-Match}. */
    private static final class StubRequest implements Request {

        private final EntityTag clientTag;

        private StubRequest(EntityTag clientTag) {
            this.clientTag = clientTag;
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public Variant selectVariant(List<Variant> variants) {
            return null;
        }

        @Override
        public Response.ResponseBuilder evaluatePreconditions(EntityTag eTag) {
            if (clientTag != null && clientTag.equals(eTag)) {
                return Response.notModified(eTag);
            }
            return null;
        }

        @Override
        public Response.ResponseBuilder evaluatePreconditions(Date lastModified) {
            return null;
        }

        @Override
        public Response.ResponseBuilder evaluatePreconditions(Date lastModified, EntityTag eTag) {
            return evaluatePreconditions(eTag);
        }

        @Override
        public Response.ResponseBuilder evaluatePreconditions() {
            return null;
        }
    }
}
