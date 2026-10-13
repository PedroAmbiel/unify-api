package br.com.unify.matchable.notification.push;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Access token OAuth2 para o FCM HTTP v1 pelo fluxo "JWT bearer" de conta de
 * serviço (RFC 7523), só com JDK: assina um JWT RS256 com a chave privada da
 * conta e troca por um access token em {@code token_uri}.
 *
 * <p>Por que não o smallrye-jwt do projeto: o builder dele aplica a
 * configuração global de emissão de tokens do app ({@code smallrye.jwt.*},
 * issuer da Unify), e o Google rejeita claims extras de issuer/audience.
 *
 * <p>O token vale ~1 h; fica em cache até {@link #REFRESH_MARGIN} antes de
 * expirar. Thread-safe (o push sai em várias virtual threads).
 */
final class FcmAccessTokenProvider {

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    static final Duration ASSERTION_TTL = Duration.ofHours(1);
    static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    private final FcmServiceAccount account;
    private final URI tokenEndpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedExpiry = Instant.EPOCH;

    FcmAccessTokenProvider(
            FcmServiceAccount account,
            URI tokenEndpoint,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Duration timeout,
            Clock clock) {
        this.account = account;
        this.tokenEndpoint = tokenEndpoint;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
        this.clock = clock;
    }

    synchronized String accessToken() throws Exception {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(cachedExpiry.minus(REFRESH_MARGIN))) {
            return cachedToken;
        }

        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(buildAssertion(now), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(tokenEndpoint)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Falha ao obter access token do Google (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonNode body = objectMapper.readTree(response.body());
        String token = body.path("access_token").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("Resposta do Google sem access_token");
        }

        long expiresIn = body.path("expires_in").asLong(3600);
        cachedToken = token;
        cachedExpiry = now.plusSeconds(expiresIn);
        return cachedToken;
    }

    /** Descarta o token em cache (ex.: FCM respondeu 401). */
    synchronized void invalidate() {
        cachedToken = null;
        cachedExpiry = Instant.EPOCH;
    }

    /** JWT assinado com a chave da conta de serviço. Sem I/O: testável. */
    String buildAssertion(Instant now) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        if (account.privateKeyId() != null && !account.privateKeyId().isBlank()) {
            header.put("kid", account.privateKeyId());
        }

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", account.clientEmail());
        claims.put("scope", SCOPE);
        claims.put("aud", account.tokenUri());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(ASSERTION_TTL).getEpochSecond());

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String signingInput = encoder.encodeToString(objectMapper.writeValueAsBytes(header))
                + "." + encoder.encodeToString(objectMapper.writeValueAsBytes(claims));

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(account.privateKey());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));

        return signingInput + "." + encoder.encodeToString(signature.sign());
    }
}
