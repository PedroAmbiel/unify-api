package br.com.unify.matchable.common.image;

import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Monta respostas de imagem com ETag e Cache-Control.
 *
 * As imagens sao imutaveis: uma nova foto gera um novo id. Por isso o
 * max-age e longo e o cache e `private` (o conteudo e protegido por JWT
 * e nao deve ser guardado por proxies compartilhados).
 */
public final class ImageResponses {

    private static final int MAX_AGE_SECONDS = 86_400; // 24h

    private ImageResponses() {
    }

    public static Response jpeg(byte[] content, Request request) {
        if (content == null || content.length == 0) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        EntityTag entityTag = new EntityTag(sha256Hex(content));

        // Se o cliente ja tem esta versao, responde 304 sem corpo.
        Response.ResponseBuilder notModified = request == null
                ? null
                : request.evaluatePreconditions(entityTag);
        if (notModified != null) {
            return notModified.cacheControl(cacheControl()).tag(entityTag).build();
        }

        return Response.ok(content)
                .type("image/jpeg")
                .tag(entityTag)
                .cacheControl(cacheControl())
                .build();
    }

    private static CacheControl cacheControl() {
        CacheControl cacheControl = new CacheControl();
        cacheControl.setPrivate(true);
        cacheControl.setMaxAge(MAX_AGE_SECONDS);
        cacheControl.setNoTransform(true);
        return cacheControl;
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(java.util.Arrays.hashCode(content));
        }
    }
}
