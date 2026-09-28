package br.com.unify.matchable.common.media;

import java.util.Locale;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Regra de áudio compartilhada entre o chat (ChatMediaValidator) e o áudio de
 * apresentação do perfil. Os limites vêm de quem chama, cada um com as suas
 * propriedades ({@code unify.chat.audio.*} / {@code unify.profile.audio.*}).
 * Sem transcodificação: o áudio é gravado como veio.
 */
@ApplicationScoped
public class AudioMediaValidator {

    public static final Set<String> ALLOWED_AUDIO_CONTENT_TYPES = Set.of(
            "audio/m4a",
            "audio/x-m4a",
            "audio/mp4",
            "audio/mpeg",
            "audio/aac",
            "audio/aacp"
    );

    public record ValidatedAudio(byte[] bytes, String contentType, Integer durationSeconds) {
    }

    /**
     * @param durationRequired no áudio de apresentação a duração é obrigatória
     *                         (o check do banco exige); no chat é opcional.
     * @throws IllegalArgumentException com mensagem em pt-BR (400)
     */
    public ValidatedAudio validate(
            String contentType,
            byte[] bytes,
            Integer durationSeconds,
            long maxSizeBytes,
            int maxDurationSeconds,
            boolean durationRequired
    ) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Nenhum áudio foi enviado");
        }

        String normalizedContentType = normalizeContentType(contentType);
        if (!ALLOWED_AUDIO_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new IllegalArgumentException(
                    "Formato de áudio não suportado. Envie m4a, mp4, aac ou mpeg.");
        }
        if (bytes.length > maxSizeBytes) {
            throw new IllegalArgumentException(
                    "O áudio excede o limite de " + (maxSizeBytes / 1_048_576) + " MB");
        }
        if (durationSeconds == null && durationRequired) {
            throw new IllegalArgumentException("Informe a duração do áudio em segundos");
        }
        if (durationSeconds != null && durationSeconds > maxDurationSeconds) {
            throw new IllegalArgumentException(
                    "O áudio excede a duração máxima de " + maxDurationSeconds + " segundos");
        }
        if (durationSeconds != null && durationSeconds < 1) {
            throw new IllegalArgumentException("O áudio é muito curto para ser enviado");
        }

        return new ValidatedAudio(bytes, normalizedContentType, durationSeconds);
    }

    public static String normalizeContentType(String contentType) {
        return contentType == null
                ? ""
                : contentType.trim().toLowerCase(Locale.ROOT).split(";")[0].trim();
    }
}
