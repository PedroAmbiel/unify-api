package br.com.unify.matchable.notification.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Entrega via Firebase Cloud Messaging HTTP v1
 * ({@code POST https://fcm.googleapis.com/v1/projects/{projectId}/messages:send}),
 * com {@code java.net.http} e sem SDK do Firebase.
 *
 * <p><b>Formato da mensagem: data-only, alta prioridade.</b> É o mesmo formato
 * que o serviço de push do Expo usa por baixo, e o único que o
 * {@code expo-notifications} trata igual com o app aberto, em segundo plano ou
 * fechado: o {@code ExpoFirebaseMessagingService} recebe, monta a notificação
 * com {@code title}/{@code message}, entrega {@code body} (JSON) como
 * {@code content.data} no JS e o toque cai no
 * {@code addNotificationResponseReceivedListener}. Uma mensagem com bloco
 * {@code notification} seria desenhada pelo próprio Android em segundo plano,
 * fora do controle do app. Chaves lidas pelo Expo:
 * {@code NotificationData.kt}/{@code FirebaseNotificationTrigger.kt} (SDK 54).
 *
 * <p>Sem arquivo de conta de serviço o gateway fica desligado (um aviso no
 * log) e o app segue só com as notificações in-app.
 */
@ApplicationScoped
public class FcmPushGateway implements PushGateway {

    private static final Logger LOG = Logger.getLogger(FcmPushGateway.class);

    static final String ANDROID_CHANNEL_ID = "default";
    static final String MESSAGE_TTL = "86400s";

    @ConfigProperty(name = "unify.push.enabled", defaultValue = "true")
    boolean pushEnabled;

    @ConfigProperty(name = "unify.push.fcm.service-account-file")
    Optional<String> serviceAccountFile;

    @ConfigProperty(name = "unify.push.fcm.send-endpoint",
            defaultValue = "https://fcm.googleapis.com/v1/projects/{projectId}/messages:send")
    String sendEndpointTemplate;

    /** Sobrescreve o {@code token_uri} da conta de serviço (testes com servidor fake). */
    @ConfigProperty(name = "unify.push.fcm.token-endpoint")
    Optional<String> tokenEndpointOverride;

    @ConfigProperty(name = "unify.push.fcm.timeout-seconds", defaultValue = "10")
    int timeoutSeconds;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile boolean initialized;
    private volatile FcmServiceAccount account;
    private volatile FcmAccessTokenProvider tokenProvider;
    private volatile URI sendEndpoint;

    @Override
    public boolean isEnabled() {
        ensureInitialized();
        return account != null;
    }

    @Override
    public List<PushDeliveryResult> send(List<PushMessage> messages) {
        List<PushDeliveryResult> results = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return results;
        }
        if (!isEnabled()) {
            for (PushMessage message : messages) {
                results.add(PushDeliveryResult.failed(message.token(), "push desabilitado"));
            }
            return results;
        }

        // O v1 não tem mais envio em lote: uma requisição por aparelho. Para o
        // volume do MVP (poucos aparelhos por pessoa) é o caminho simples.
        for (PushMessage message : messages) {
            results.add(sendOne(message));
        }
        return results;
    }

    private PushDeliveryResult sendOne(PushMessage message) {
        try {
            HttpResponse<String> response = post(message);
            if (response.statusCode() == 401) {
                // Access token expirado/revogado antes da hora: um novo e uma retentativa.
                tokenProvider.invalidate();
                response = post(message);
            }
            PushDeliveryResult result = classify(message.token(), response.statusCode(), response.body(), objectMapper);
            if (result.status() != PushDeliveryResult.Status.SENT) {
                LOG.debugf("FCM recusou entrega (%s): %s", result.status(), result.detail());
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PushDeliveryResult.failed(message.token(), "interrompido");
        } catch (Exception exception) {
            LOG.warn("Falha ao enviar push via FCM", exception);
            return PushDeliveryResult.failed(message.token(), exception.getMessage());
        }
    }

    private HttpResponse<String> post(PushMessage message) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(sendEndpoint)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + tokenProvider.accessToken())
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(message, objectMapper)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!pushEnabled) {
            LOG.info("Push desligado (unify.push.enabled=false): só notificações in-app.");
            return;
        }

        Path path = serviceAccountFile.map(String::trim).filter(value -> !value.isEmpty()).map(Path::of).orElse(null);
        if (path == null || !Files.isRegularFile(path)) {
            LOG.warnf(
                    "Push desligado: conta de serviço do Firebase não encontrada em '%s'. "
                            + "Defina FIREBASE_SERVICE_ACCOUNT_FILE (ver README). As notificações in-app continuam funcionando.",
                    path == null ? "(não configurado)" : path.toAbsolutePath());
            return;
        }

        try {
            FcmServiceAccount loaded = FcmServiceAccount.fromFile(path, objectMapper);
            URI tokenEndpoint = URI.create(tokenEndpointOverride.filter(value -> !value.isBlank()).orElse(loaded.tokenUri()));
            sendEndpoint = URI.create(sendEndpointTemplate.replace("{projectId}", loaded.projectId()));
            tokenProvider = new FcmAccessTokenProvider(
                    loaded, tokenEndpoint, httpClient, objectMapper, Duration.ofSeconds(timeoutSeconds), Clock.systemUTC());
            account = loaded;
            LOG.infof("Push FCM ligado para o projeto Firebase '%s'.", loaded.projectId());
        } catch (Exception exception) {
            LOG.error("Push desligado: conta de serviço do Firebase inválida em " + path.toAbsolutePath(), exception);
        }
    }

    /**
     * Corpo do {@code messages:send}. Sem I/O: testável.
     *
     * <ul>
     *   <li>{@code data.title}/{@code data.message}: título e texto exibidos.</li>
     *   <li>{@code data.body}: JSON com o destino (tipo, ids) — vira
     *       {@code notification.request.content.data} no app.</li>
     *   <li>{@code data.channelId}: canal Android criado pelo app.</li>
     *   <li>{@code data.tag}: mesma tag substitui o aviso anterior na bandeja.</li>
     * </ul>
     */
    static String buildRequestBody(PushMessage message, ObjectMapper objectMapper) throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", message.title());
        data.put("message", message.body());
        data.put("body", objectMapper.writeValueAsString(message.data() == null ? Map.of() : message.data()));
        data.put("channelId", ANDROID_CHANNEL_ID);
        if (message.tag() != null && !message.tag().isBlank()) {
            data.put("tag", message.tag());
        }

        Map<String, Object> android = new LinkedHashMap<>();
        android.put("priority", "HIGH");
        android.put("ttl", MESSAGE_TTL);
        if (message.tag() != null && !message.tag().isBlank()) {
            android.put("collapse_key", message.tag());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", message.token());
        payload.put("data", data);
        payload.put("android", android);

        return objectMapper.writeValueAsString(Map.of("message", payload));
    }

    /**
     * Interpreta a resposta do v1. Token morto: {@code UNREGISTERED} (app
     * desinstalado/token expirado), {@code SENDER_ID_MISMATCH} (token de outro
     * projeto Firebase) ou {@code INVALID_ARGUMENT} sobre o token.
     */
    static PushDeliveryResult classify(String token, int statusCode, String responseBody, ObjectMapper objectMapper) {
        if (statusCode >= 200 && statusCode < 300) {
            return PushDeliveryResult.sent(token);
        }

        String status = "";
        String message = "";
        List<String> errorCodes = new ArrayList<>();
        try {
            JsonNode error = objectMapper.readTree(responseBody == null ? "{}" : responseBody).path("error");
            status = error.path("status").asText("");
            message = error.path("message").asText("");
            for (JsonNode detail : error.path("details")) {
                String errorCode = detail.path("errorCode").asText("");
                if (!errorCode.isEmpty()) {
                    errorCodes.add(errorCode);
                }
            }
        } catch (Exception ignored) {
            // Corpo não-JSON (proxy, HTML de erro): cai em FAILED abaixo.
        }

        String detail = "HTTP " + statusCode + " " + status + " " + errorCodes + " " + message;
        boolean deadToken = errorCodes.contains("UNREGISTERED")
                || errorCodes.contains("SENDER_ID_MISMATCH")
                || (statusCode == 404 && "NOT_FOUND".equals(status))
                || ("INVALID_ARGUMENT".equals(status) && message.toLowerCase().contains("registration token"));

        return deadToken
                ? PushDeliveryResult.invalidToken(token, detail.trim())
                : PushDeliveryResult.failed(token, detail.trim());
    }
}
