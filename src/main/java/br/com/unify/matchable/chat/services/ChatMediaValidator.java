package br.com.unify.matchable.chat.services;

import java.util.Locale;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.unify.matchable.chat.dto.ChatMediaPayload;
import br.com.unify.matchable.chat.enums.ChatMessageType;
import br.com.unify.matchable.common.image.OidImageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ChatMediaValidator {

    private static final Set<String> ALLOWED_AUDIO_CONTENT_TYPES = Set.of(
            "audio/m4a",
            "audio/x-m4a",
            "audio/mp4",
            "audio/mpeg",
            "audio/aac",
            "audio/aacp"
    );

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    @ConfigProperty(name = "unify.chat.audio.max-size-bytes", defaultValue = "5242880") // 5 MB
    long maxAudioSizeBytes;

    @ConfigProperty(name = "unify.chat.audio.max-duration-seconds", defaultValue = "120")
    int maxAudioDurationSeconds;

    @ConfigProperty(name = "unify.chat.image.max-size-bytes", defaultValue = "8388608") // 8 MB de entrada
    long maxImageSizeBytes;

    @Inject
    OidImageService oidImageService;

    /**
     * Valida e normaliza a mídia recebida no multipart.
     *
     * @param declaredType    valor do campo "type" do formulário (IMAGE ou AUDIO)
     * @param contentType     content-type informado pelo cliente
     * @param bytes           conteúdo do arquivo
     * @param durationSeconds duração informada pelo cliente (apenas áudio; pode ser nula)
     * @throws IllegalArgumentException com mensagem em pt-BR para qualquer violação
     */
    public ChatMediaPayload validate(
            String declaredType,
            String contentType,
            byte[] bytes,
            Integer durationSeconds
    ) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Nenhum arquivo foi enviado no campo 'file'");
        }

        ChatMessageType type = parseType(declaredType);

        if (type == ChatMessageType.VIDEO) {
            throw new IllegalArgumentException(
                    "O envio de vídeo ainda não está disponível. Envie uma imagem ou um áudio.");
        }

        String normalizedContentType = contentType == null
                ? ""
                : contentType.trim().toLowerCase(Locale.ROOT).split(";")[0].trim();

        if (type == ChatMessageType.IMAGE) {
            return validateImage(normalizedContentType, bytes);
        }
        return validateAudio(normalizedContentType, bytes, durationSeconds);
    }

    private ChatMessageType parseType(String declaredType) {
        if (declaredType == null || declaredType.isBlank()) {
            throw new IllegalArgumentException(
                    "Informe o tipo da mídia no campo 'type' (IMAGE ou AUDIO)");
        }
        try {
            return ChatMessageType.valueOf(declaredType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Tipo de mídia inválido: use IMAGE ou AUDIO", exception);
        }
    }

    private ChatMediaPayload validateImage(String contentType, byte[] bytes) {
        if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Formato de imagem não suportado. Envie JPEG, PNG ou WebP.");
        }
        if (bytes.length > maxImageSizeBytes) {
            throw new IllegalArgumentException(
                    "A imagem excede o limite de " + (maxImageSizeBytes / 1_048_576) + " MB");
        }

        // Reaproveita o pipeline já usado em perfis e posts de comunidade:
        // valida com ImageIO, remove alpha e comprime em JPEG 0.75.
        byte[] compressed = oidImageService.compressToJpeg(bytes);

        return new ChatMediaPayload(ChatMessageType.IMAGE, compressed, "image/jpeg", null);
    }

    private ChatMediaPayload validateAudio(String contentType, byte[] bytes, Integer durationSeconds) {
        if (!ALLOWED_AUDIO_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Formato de áudio não suportado. Envie m4a, mp4, aac ou mpeg.");
        }
        if (bytes.length > maxAudioSizeBytes) {
            throw new IllegalArgumentException(
                    "O áudio excede o limite de " + (maxAudioSizeBytes / 1_048_576) + " MB");
        }
        if (durationSeconds != null && durationSeconds > maxAudioDurationSeconds) {
            throw new IllegalArgumentException(
                    "O áudio excede a duração máxima de " + maxAudioDurationSeconds + " segundos");
        }
        if (durationSeconds != null && durationSeconds < 1) {
            throw new IllegalArgumentException("O áudio é muito curto para ser enviado");
        }

        // Áudio é gravado como veio: sem transcodificação, sem reencode.
        return new ChatMediaPayload(ChatMessageType.AUDIO, bytes, contentType, durationSeconds);
    }
}
