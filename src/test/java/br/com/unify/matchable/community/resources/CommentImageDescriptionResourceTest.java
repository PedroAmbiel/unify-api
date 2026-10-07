package br.com.unify.matchable.community.resources;
import static org.junit.jupiter.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import br.com.unify.matchable.community.services.ImageVisionService;
import br.com.unify.matchable.community.services.ImageVisionService.VisionException;
import br.com.unify.matchable.user.entity.User;
class CommentImageDescriptionResourceTest {
    static class VisionStub extends ImageVisionService {
        int calls; byte[] received; VisionException failure;
        @Override public String describe(byte[] image) {
            calls++; received = image;
            if (failure != null) throw failure;
            return "Uma bicicleta azul.";
        }
    }
    static class Fake extends CommentImageDescriptionResource {
        User user = new User(); boolean denied; byte[] image = {1,2};
        UUID receivedPost; UUID receivedComment;
        @Override protected User findCurrentUser() { return user; }
        @Override protected byte[] loadImage(User user, UUID postId, UUID commentId) {
            receivedPost = postId; receivedComment = commentId;
            if (denied) throw new NoSuchElementException(); return image;
        }
        VisionStub stub = new VisionStub();
        Fake() { vision = stub; }
    }
    @Test void unauthorizedOrMissingImageNeverCallsVision() {
        Fake resource = new Fake(); resource.denied = true;
        assertEquals(404, resource.describe(UUID.randomUUID(), UUID.randomUUID()).getStatus());
        assertEquals(0, resource.stub.calls);
        resource.user = null;
        assertEquals(404, resource.describe(UUID.randomUUID(), UUID.randomUUID()).getStatus());
        assertEquals(0, resource.stub.calls);
    }
    @Test void usesAuthorizedStoredImageAndReturnsGeneratedDescription() {
        Fake resource = new Fake(); UUID post = UUID.randomUUID(); UUID comment = UUID.randomUUID();

        var response = resource.describe(post, comment);
        assertEquals(200, response.getStatus());
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        var body = (CommentImageDescriptionResource.DescriptionResponse) response.getEntity();
        assertEquals("Uma bicicleta azul.", body.description()); assertEquals("AI", body.source());
        assertSame(resource.image, resource.stub.received);
        assertEquals(post, resource.receivedPost); assertEquals(comment, resource.receivedComment);
    }
    @Test void featureUnconfiguredReturns503() {
        Fake resource = new Fake();
        resource.stub.failure = new VisionException(503, "IA não configurada");
        assertEquals(503, resource.describe(UUID.randomUUID(), UUID.randomUUID()).getStatus());
    }
}
