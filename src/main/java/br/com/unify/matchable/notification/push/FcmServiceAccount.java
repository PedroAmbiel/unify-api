package br.com.unify.matchable.notification.push;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Conta de serviço do Firebase (JSON baixado em Configurações do projeto →
 * Contas de serviço → Gerar nova chave privada). Só os campos usados para
 * pedir o access token OAuth2 do FCM.
 *
 * <p>O arquivo é SEGREDO: fica em {@code secrets/} (fora do git) e o caminho
 * vem de {@code FIREBASE_SERVICE_ACCOUNT_FILE}.
 */
public record FcmServiceAccount(
        String projectId,
        String clientEmail,
        String privateKeyId,
        PrivateKey privateKey,
        String tokenUri
) {

    static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    public static FcmServiceAccount fromFile(Path path, ObjectMapper objectMapper) throws Exception {
        return fromJson(Files.readString(path, StandardCharsets.UTF_8), objectMapper);
    }

    public static FcmServiceAccount fromJson(String json, ObjectMapper objectMapper) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        String type = root.path("type").asText("");
        if (!"service_account".equals(type)) {
            throw new IllegalArgumentException(
                    "O JSON não é de conta de serviço (type=" + type + "). Use a chave gerada em "
                            + "Configurações do projeto → Contas de serviço, não o google-services.json do app");
        }

        String projectId = required(root, "project_id");
        String clientEmail = required(root, "client_email");
        String privateKeyPem = required(root, "private_key");
        String privateKeyId = root.path("private_key_id").asText(null);
        String tokenUri = root.path("token_uri").asText(DEFAULT_TOKEN_URI);

        return new FcmServiceAccount(projectId, clientEmail, privateKeyId, parsePrivateKey(privateKeyPem), tokenUri);
    }

    /** PEM PKCS#8 ("-----BEGIN PRIVATE KEY-----"), formato das chaves do Google. */
    static PrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String required(JsonNode root, String field) {
        String value = root.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente na conta de serviço: " + field);
        }
        return value;
    }
}
