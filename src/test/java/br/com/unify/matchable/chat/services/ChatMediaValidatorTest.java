package br.com.unify.matchable.chat.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.chat.dto.ChatMediaPayload;
import br.com.unify.matchable.chat.enums.ChatMessageType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * {@code @QuarkusTest} porque {@link ChatMediaValidator} injeta o
 * {@code OidImageService} e lê os limites de {@code unify.chat.*} via
 * {@code @ConfigProperty}.
 */
@QuarkusTest
class ChatMediaValidatorTest {

    @Inject
    ChatMediaValidator validator;

    @Test
    void validAudioKeepsBytesAndContentType() {
        byte[] audio = randomBytes(1_048_576);

        ChatMediaPayload payload = validator.validate("AUDIO", "audio/m4a", audio, 12);

        assertEquals(ChatMessageType.AUDIO, payload.type());
        assertArrayEquals(audio, payload.bytes());
        assertEquals("audio/m4a", payload.contentType());
        assertEquals(12, payload.durationSeconds());
    }

    @Test
    void audioWithUnsupportedContentTypeIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AUDIO", "audio/wav", randomBytes(1024), 10)
        );

        assertTrue(exception.getMessage().startsWith("Formato de áudio não suportado"));
    }

    @Test
    void audioAboveSizeLimitIsRejected() {
        byte[] oversized = new byte[6 * 1_048_576];

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AUDIO", "audio/m4a", oversized, 10)
        );

        assertEquals("O áudio excede o limite de 5 MB", exception.getMessage());
    }

    @Test
    void audioAboveDurationLimitIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AUDIO", "audio/m4a", randomBytes(1024), 180)
        );

        assertEquals("O áudio excede a duração máxima de 120 segundos", exception.getMessage());
    }

    @Test
    void audioTooShortIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AUDIO", "audio/m4a", randomBytes(1024), 0)
        );

        assertEquals("O áudio é muito curto para ser enviado", exception.getMessage());
    }

    @Test
    void contentTypeWithParametersIsAccepted() {
        byte[] audio = randomBytes(2048);

        ChatMediaPayload payload = validator.validate(
                "AUDIO", "audio/m4a; codecs=mp4a.40.2", audio, 30);

        assertEquals(ChatMessageType.AUDIO, payload.type());
        assertEquals("audio/m4a", payload.contentType());
        assertArrayEquals(audio, payload.bytes());
    }

    @Test
    void validPngIsCompressedToJpeg() {
        byte[] png = buildPng(64, 64);

        ChatMediaPayload payload = validator.validate("IMAGE", "image/png", png, null);

        assertEquals(ChatMessageType.IMAGE, payload.type());
        assertEquals("image/jpeg", payload.contentType());
        assertNull(payload.durationSeconds());
        assertNotNull(payload.bytes());
        assertTrue(payload.bytes().length > 0);
        assertFalse(Arrays.equals(png, payload.bytes()), "A imagem deveria ter sido recomprimida em JPEG");
    }

    @Test
    void fakeImageBytesAreRejected() {
        byte[] notAnImage = "isto aqui e um arquivo de texto, nao uma imagem".getBytes(StandardCharsets.UTF_8);

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("IMAGE", "image/jpeg", notAnImage, null)
        );
    }

    @Test
    void videoUploadIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("VIDEO", "video/mp4", randomBytes(1024), null)
        );

        assertTrue(exception.getMessage().startsWith("O envio de vídeo ainda não está disponível"));
    }

    @Test
    void blankTypeIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("   ", "audio/m4a", randomBytes(1024), 10)
        );

        assertEquals("Informe o tipo da mídia no campo 'type' (IMAGE ou AUDIO)", exception.getMessage());
    }

    @Test
    void unknownTypeIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("DOCUMENT", "application/pdf", randomBytes(1024), null)
        );

        assertEquals("Tipo de mídia inválido: use IMAGE ou AUDIO", exception.getMessage());
    }

    @Test
    void emptyBytesAreRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AUDIO", "audio/m4a", new byte[0], 10)
        );

        assertEquals("Nenhum arquivo foi enviado no campo 'file'", exception.getMessage());
    }

    @Test
    void nullBytesAreRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AUDIO", "audio/m4a", null, 10)
        );

        assertEquals("Nenhum arquivo foi enviado no campo 'file'", exception.getMessage());
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        for (int index = 0; index < length; index++) {
            bytes[index] = (byte) (index % 251);
        }
        return bytes;
    }

    static byte[] buildPng(int width, int height) {
        return buildImage(width, height, "png");
    }

    static byte[] buildImage(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.MAGENTA);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLUE);
        graphics.fillOval(4, 4, width - 8, height - 8);
        graphics.dispose();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
