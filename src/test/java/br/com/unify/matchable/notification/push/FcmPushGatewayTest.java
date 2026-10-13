package br.com.unify.matchable.notification.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * FCM sem rede real: um {@link HttpServer} do JDK faz o papel do endpoint de
 * token OAuth2 do Google e do {@code messages:send}. Cobre o formato da
 * mensagem (data-only que o expo-notifications entende), a assinatura do JWT,
 * o cache/renovação do access token e a classificação de token morto.
 */
class FcmPushGatewayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private HttpServer server;
    private KeyPair keyPair;
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private final List<String> sentBodies = new CopyOnWriteArrayList<>();
    private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
    /** Resposta do messages:send por token (default 200). */
    private final Map<String, Integer> statusByToken = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> bodyByToken = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String lastAssertion;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", this::handleToken);
        server.createContext("/v1/projects/unify-test/messages:send", this::handleSend);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void requestBodyIsDataOnlyHighPriorityWithExpoKeys() throws Exception {
        PushMessage message = new PushMessage(
                "device-token",
                "Novo match!",
                "Ana também curtiu você.",
                Map.of("type", "NEW_MATCH", "referenceId", "abc"),
                "notification-1"
        );

        JsonNode root = MAPPER.readTree(FcmPushGateway.buildRequestBody(message, MAPPER)).path("message");

        assertEquals("device-token", root.path("token").asText());
        assertTrue(root.path("notification").isMissingNode(), "sem bloco notification: o app desenha o aviso");
        assertEquals("HIGH", root.path("android").path("priority").asText());
        assertEquals("notification-1", root.path("android").path("collapse_key").asText());

        JsonNode data = root.path("data");
        assertEquals("Novo match!", data.path("title").asText());
        assertEquals("Ana também curtiu você.", data.path("message").asText());
        assertEquals("default", data.path("channelId").asText());
        assertEquals("notification-1", data.path("tag").asText());
        // `body` é JSON em string: vira content.data no expo-notifications.
        JsonNode destination = MAPPER.readTree(data.path("body").asText());
        assertEquals("NEW_MATCH", destination.path("type").asText());
        assertEquals("abc", destination.path("referenceId").asText());
    }

    @Test
    void classifyRecognizesDeadTokens() {
        String unregistered = """
                {"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND",
                "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}
                """;
        String malformed = """
                {"error":{"code":400,"message":"The registration token is not a valid FCM registration token","status":"INVALID_ARGUMENT"}}
                """;
        String otherProject = """
                {"error":{"code":403,"status":"PERMISSION_DENIED","details":[{"errorCode":"SENDER_ID_MISMATCH"}]}}
                """;
        String quota = """
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[{"errorCode":"QUOTA_EXCEEDED"}]}}
                """;

        assertEquals(PushDeliveryResult.Status.SENT, FcmPushGateway.classify("t", 200, "{}", MAPPER).status());
        assertEquals(PushDeliveryResult.Status.INVALID_TOKEN, FcmPushGateway.classify("t", 404, unregistered, MAPPER).status());
        assertEquals(PushDeliveryResult.Status.INVALID_TOKEN, FcmPushGateway.classify("t", 400, malformed, MAPPER).status());
        assertEquals(PushDeliveryResult.Status.INVALID_TOKEN, FcmPushGateway.classify("t", 403, otherProject, MAPPER).status());
        assertEquals(PushDeliveryResult.Status.FAILED, FcmPushGateway.classify("t", 429, quota, MAPPER).status());
        assertEquals(PushDeliveryResult.Status.FAILED, FcmPushGateway.classify("t", 502, "<html>bad gateway</html>", MAPPER).status());
    }

    @Test
    void sendsThroughFakeFcmReusesAccessTokenAndReportsDeadToken() throws Exception {
        statusByToken.put("dead-token", 404);
        bodyByToken.put("dead-token", """
                {"error":{"code":404,"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}
                """);
        FcmPushGateway gateway = gateway(writeServiceAccount());

        assertTrue(gateway.isEnabled());
        List<PushDeliveryResult> results = gateway.send(List.of(
                new PushMessage("live-token", "Título", "Texto", Map.of("type", "NEW_FOLLOWER"), "n-1"),
                new PushMessage("dead-token", "Título", "Texto", Map.of("type", "NEW_FOLLOWER"), "n-1")
        ));

        assertEquals(2, results.size());
        assertEquals(PushDeliveryResult.Status.SENT, results.get(0).status());
        assertEquals(PushDeliveryResult.Status.INVALID_TOKEN, results.get(1).status());
        assertEquals("dead-token", results.get(1).token());

        assertEquals(1, tokenRequests.get(), "access token em cache entre as entregas");
        assertEquals(2, sentBodies.size());
        assertTrue(authorizationHeaders.stream().allMatch("Bearer access-1"::equals));
        assertValidAssertion(lastAssertion);
    }

    @Test
    void expiredAccessTokenIsRenewedOnceOn401() throws Exception {
        // Primeira chamada com o token 1 dá 401; a retentativa usa o token 2.
        statusByToken.put("live-token", 401);
        FcmPushGateway gateway = gateway(writeServiceAccount());

        List<PushDeliveryResult> results = gateway.send(List.of(
                new PushMessage("live-token", "T", "B", Map.of(), null)
        ));

        assertEquals(PushDeliveryResult.Status.SENT, results.get(0).status());
        assertEquals(2, tokenRequests.get());
        assertEquals(List.of("Bearer access-1", "Bearer access-2"), authorizationHeaders);
    }

    @Test
    void missingServiceAccountDisablesPushWithoutThrowing() {
        FcmPushGateway gateway = gateway(tempDir.resolve("nao-existe.json"));

        assertFalse(gateway.isEnabled());
        List<PushDeliveryResult> results = gateway.send(List.of(new PushMessage("t", "T", "B", Map.of(), null)));
        assertEquals(PushDeliveryResult.Status.FAILED, results.get(0).status());
        assertEquals(0, tokenRequests.get());
    }

    @Test
    void appConfigFileIsRejectedWithAHelpfulMessage() {
        // Erro comum: colocar o google-services.json do app no lugar da conta de serviço.
        String googleServices = """
                {"project_info":{"project_id":"unify-test"},"client":[]}
                """;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FcmServiceAccount.fromJson(googleServices, MAPPER));
        assertTrue(error.getMessage().contains("Contas de serviço"));
    }

    // ------------------------------------------------------------ apoio

    private FcmPushGateway gateway(Path serviceAccount) {
        FcmPushGateway gateway = new FcmPushGateway();
        gateway.pushEnabled = true;
        gateway.serviceAccountFile = Optional.of(serviceAccount.toString());
        gateway.sendEndpointTemplate = baseUrl() + "/v1/projects/{projectId}/messages:send";
        gateway.tokenEndpointOverride = Optional.empty();
        gateway.timeoutSeconds = 5;
        gateway.objectMapper = MAPPER;
        return gateway;
    }

    private Path writeServiceAccount() throws IOException {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        Map<String, String> json = Map.of(
                "type", "service_account",
                "project_id", "unify-test",
                "private_key_id", "kid-123",
                "private_key", pem,
                "client_email", "push@unify-test.iam.gserviceaccount.com",
                "token_uri", baseUrl() + "/token"
        );
        Path file = tempDir.resolve("firebase-service-account.json");
        Files.writeString(file, MAPPER.writeValueAsString(json));
        return file;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        for (String pair : form.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue[0].equals("assertion")) {
                lastAssertion = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }
        int number = tokenRequests.incrementAndGet();
        respond(exchange, 200, "{\"access_token\":\"access-" + number + "\",\"expires_in\":3599,\"token_type\":\"Bearer\"}");
    }

    private void handleSend(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizationHeaders.add(authorization);
        sentBodies.add(body);

        String token = MAPPER.readTree(body).path("message").path("token").asText();
        Integer status = statusByToken.get(token);
        if (status != null && status == 401) {
            // Só o primeiro access token é "expirado".
            if ("Bearer access-1".equals(authorization)) {
                respond(exchange, 401, "{\"error\":{\"code\":401,\"status\":\"UNAUTHENTICATED\"}}");
                return;
            }
            respond(exchange, 200, "{\"name\":\"projects/unify-test/messages/1\"}");
            return;
        }
        if (status != null) {
            respond(exchange, status, bodyByToken.getOrDefault(token, "{}"));
            return;
        }
        respond(exchange, 200, "{\"name\":\"projects/unify-test/messages/1\"}");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void assertValidAssertion(String assertion) throws Exception {
        assertNotNull(assertion);
        String[] parts = assertion.split("\\.");
        assertEquals(3, parts.length);

        Base64.Decoder decoder = Base64.getUrlDecoder();
        JsonNode header = MAPPER.readTree(decoder.decode(parts[0]));
        JsonNode claims = MAPPER.readTree(decoder.decode(parts[1]));

        assertEquals("RS256", header.path("alg").asText());
        assertEquals("kid-123", header.path("kid").asText());
        assertEquals("push@unify-test.iam.gserviceaccount.com", claims.path("iss").asText());
        assertEquals(FcmAccessTokenProvider.SCOPE, claims.path("scope").asText());
        assertEquals(baseUrl() + "/token", claims.path("aud").asText());
        long now = Instant.now().getEpochSecond();
        assertTrue(Math.abs(claims.path("iat").asLong() - now) < 60);
        assertEquals(3600, claims.path("exp").asLong() - claims.path("iat").asLong());

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(decoder.decode(parts[2])), "assinado com a chave da conta de serviço");
    }
}
