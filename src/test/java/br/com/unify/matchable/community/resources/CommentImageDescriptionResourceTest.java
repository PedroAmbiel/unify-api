package br.com.unify.matchable.community.resources;
import static org.junit.jupiter.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import br.com.unify.matchable.community.services.ImageDescriptionGenerationService;
import br.com.unify.matchable.community.services.ImageDescriptionGenerationService.Result;
import br.com.unify.matchable.community.services.ImageVisionService.VisionException;
import br.com.unify.matchable.user.entity.User;
class CommentImageDescriptionResourceTest {
    static class DescriptionsStub extends ImageDescriptionGenerationService {
        int calls; boolean denied; Result result = new Result("Uma bicicleta azul.", true); VisionException failure;
        UUID receivedPost; UUID receivedComment;
        @Override public Result describeComment(User user, UUID postId, UUID commentId) {
            calls++; receivedPost = postId; receivedComment = commentId;
            if (denied) throw new NoSuchElementException();
            if (failure != null) throw failure;
            return result;
        }
    }
    static class Fake extends CommentImageDescriptionResource {
        User user = new User();
        DescriptionsStub stub = new DescriptionsStub();
        Fake() { descriptions = stub; }
        @Override protected User findCurrentUser() { return user; }
    }
    @Test void unauthorizedOrMissingImageReturns404() {
        Fake resource = new Fake(); resource.stub.denied = true;
        assertEquals(404, resource.describe(UUID.randomUUID(), UUID.randomUUID()).getStatus());
        resource.user = null;
        resource.stub.calls = 0;
        assertEquals(404, resource.describe(UUID.randomUUID(), UUID.randomUUID()).getStatus());
        assertEquals(0, resource.stub.calls);
    }
    @Test void returnsGeneratedDescriptionWithoutCaching() {
        Fake resource = new Fake(); UUID post = UUID.randomUUID(); UUID comment = UUID.randomUUID();

        var response = resource.describe(post, comment);
        assertEquals(200, response.getStatus());
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        var body = (ImageDescriptionResponses.DescriptionResponse) response.getEntity();
        assertEquals("Uma bicicleta azul.", body.description()); assertEquals("AI", body.source());
        assertEquals(post, resource.stub.receivedPost); assertEquals(comment, resource.stub.receivedComment);
    }
    @Test void reusedDescriptionIsMarkedSaved() {
        Fake resource = new Fake(); resource.stub.result = new Result("Descrição do autor.", false);
        var body = (ImageDescriptionResponses.DescriptionResponse) resource.describe(UUID.randomUUID(), UUID.randomUUID()).getEntity();
        assertEquals("SAVED", body.source());
    }
    @Test void featureUnconfiguredReturns503() {
        Fake resource = new Fake();
        resource.stub.failure = new VisionException(503, "IA não configurada");
        assertEquals(503, resource.describe(UUID.randomUUID(), UUID.randomUUID()).getStatus());
    }
}
