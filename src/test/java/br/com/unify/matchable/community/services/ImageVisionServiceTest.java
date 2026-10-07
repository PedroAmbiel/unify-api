package br.com.unify.matchable.community.services;
import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import br.com.unify.matchable.community.services.ImageVisionService.VisionException;
class ImageVisionServiceTest {
    private static final String OUTPUT = "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Uma árvore.\"}]}]}";
    static class Fake extends ImageVisionService {
        int status = 200; String body = OUTPUT; int calls; HttpRequest request; StringBuilder payload = new StringBuilder();
        Fake() { mapper = new ObjectMapper(); apiKey = Optional.of("test-key"); model = "gpt-4.1-mini";
            provider = "openai"; geminiApiKey = Optional.of("gemini-test-key"); geminiModel = "gemini-3.1-flash-lite"; }
        @Override protected HttpResponse<String> send(HttpRequest request) {
            this.request = request; calls++;
            request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(java.nio.ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()]; item.get(bytes);
                    payload.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
                public void onError(Throwable error) { fail(error); }
                public void onComplete() {}
            });
            return new HttpResponse<String>() {
                public int statusCode() { return status; }
                public HttpRequest request() { return request; }
                public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
                public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(java.util.Map.of(), (a,b) -> true); }
                public String body() { return body; }
                public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
                public java.net.URI uri() { return request.uri(); }
                public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
            };
        }
    }
    @Test void absentKeyDisablesOnlyFeatureWithoutNetwork() {
        Fake service = new Fake(); service.apiKey = Optional.empty();
        assertEquals(503, assertThrows(VisionException.class, () -> service.describe(new byte[]{1})).status());
        assertEquals(0, service.calls);
    }
    @Test void sendsAuthenticatedVisionRequestAndReadsOutput() {
        Fake service = new Fake();
        assertEquals("Uma árvore.", service.describe(new byte[]{1,2,3}));
        assertEquals("https://api.openai.com/v1/responses", service.request.uri().toString());
        assertEquals("Bearer test-key", service.request.headers().firstValue("Authorization").orElseThrow());
        assertTrue(service.request.bodyPublisher().isPresent());
        assertTrue(service.payload.toString().contains("data:image/jpeg;base64,AQID"));
        assertTrue(service.payload.toString().contains("input_image"));
        assertTrue(service.payload.toString().contains("\"store\":false"));
        assertFalse(service.payload.toString().contains("test-key"));
    }
    @Test void upstreamFailuresAreSanitizedAndCanBeRetried() {
        Fake service = new Fake(); service.status = 401; service.body = "secret upstream details";
        VisionException error = assertThrows(VisionException.class, () -> service.describe(new byte[]{1}));
        assertEquals(502, error.status()); assertFalse(error.getMessage().contains("secret"));
        service.status = 200; service.body = OUTPUT;
        assertEquals("Uma árvore.", service.describe(new byte[]{1}));
    }
    @Test void rateLimitIsPreserved() {
        Fake service = new Fake(); service.status = 429;
        assertEquals(429, assertThrows(VisionException.class, () -> service.describe(new byte[]{1})).status());
    }
    @Test void refusesIncompleteEmptyOrRefusalOutput() {
        Fake service = new Fake();
        for (String body : new String[]{"{\"status\":\"incomplete\",\"output\":[]}", "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"No\"}]}]}"}) {
            service.body = body;
            assertEquals(502, assertThrows(VisionException.class, () -> service.describe(new byte[]{1})).status());
        }
    }

    private static final String GEMINI_OUTPUT = "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"Raciocínio interno\"},{\"text\":\"Uma árvore.\"}]}}]}";

    @Test void sendsGeminiImageAndReadsOnlyDescription() throws Exception {
        Fake service = new Fake(); service.provider = "gemini"; service.body = GEMINI_OUTPUT;
        assertEquals("Uma árvore.", service.describe(new byte[]{1,2,3}));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent",
            service.request.uri().toString());
        assertEquals("gemini-test-key", service.request.headers().firstValue("x-goog-api-key").orElseThrow());
        assertTrue(service.request.headers().firstValue("Authorization").isEmpty());
        var payload = service.mapper.readTree(service.payload.toString());
        var image = payload.path("contents").path(0).path("parts").path(1).path("inlineData");
        assertEquals("image/jpeg", image.path("mimeType").asText());
        assertEquals("AQID", image.path("data").asText());
        assertEquals(600, payload.path("generationConfig").path("maxOutputTokens").asInt());
        assertFalse(service.payload.toString().contains("gemini-test-key"));
        assertFalse(service.request.uri().toString().contains("gemini-test-key"));
    }

    @Test void geminiRequiresItsOwnKeyWithoutFallingBackToOpenAI() {
        Fake service = new Fake(); service.provider = "gemini"; service.geminiApiKey = Optional.empty();
        assertEquals(503, assertThrows(VisionException.class, () -> service.describe(new byte[]{1})).status());
        assertEquals(0, service.calls);
    }

    @Test void geminiRejectsBlockedTruncatedEmptyAndThoughtOnlyResponses() {
        Fake service = new Fake(); service.provider = "gemini";
        for (String body : new String[]{"{}", "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
                GEMINI_OUTPUT.replace("STOP", "MAX_TOKENS"), GEMINI_OUTPUT.replace("STOP", "SAFETY"),
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"Privado\"}]}}]}",
                "not-json"}) {
            service.body = body;
            assertEquals(502, assertThrows(VisionException.class, () -> service.describe(new byte[]{1})).status());
        }
    }

    @Test void geminiSanitizesErrorsAndAllowsRetryAfterQuotaLimit() {
        Fake service = new Fake(); service.provider = "gemini"; service.status = 429;
        assertEquals(429, assertThrows(VisionException.class, () -> service.describe(new byte[]{1})).status());
        service.status = 403; service.body = "secret upstream details";
        var error = assertThrows(VisionException.class, () -> service.describe(new byte[]{1}));
        assertEquals(502, error.status()); assertFalse(error.getMessage().contains("secret"));
        service.status = 200; service.body = GEMINI_OUTPUT;
        assertEquals("Uma árvore.", service.describe(new byte[]{1}));
    }

    @Test void invalidProviderOrModelMakesNoNetworkRequest() {
        Fake service = new Fake(); service.provider = "unknown";
        assertEquals(503, assertThrows(VisionException.class, () -> service.describe(new byte[]{1})).status());
        service.provider = "gemini"; service.geminiModel = "model?key=invalid";
        assertEquals(503, assertThrows(VisionException.class, () -> service.describe(new byte[]{1})).status());
        assertEquals(0, service.calls);
    }
}
