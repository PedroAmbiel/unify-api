package br.com.unify.matchable.common.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regra de áudio compartilhada (chat + apresentação do perfil). Os casos do
 * chat continuam em ChatMediaValidatorTest, que é o teste de regressão da
 * extração; aqui ficam os parâmetros novos (limites por chamada e duração
 * obrigatória).
 */
class AudioMediaValidatorTest {

    private static final long ONE_MB = 1_048_576L;

    private final AudioMediaValidator validator = new AudioMediaValidator();

    @Test
    void acceptsAllowedTypeAndNormalizesContentTypeParameters() {
        byte[] bytes = new byte[] { 1, 2, 3 };

        AudioMediaValidator.ValidatedAudio audio =
                validator.validate("Audio/MP4; codecs=mp4a.40.2", bytes, 30, 3 * ONE_MB, 60, true);

        assertEquals("audio/mp4", audio.contentType());
        assertArrayEquals(bytes, audio.bytes());
        assertEquals(30, audio.durationSeconds());
    }

    @Test
    void durationIsRequiredOnlyWhenTheCallerSaysSo() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("audio/m4a", new byte[] { 1 }, null, ONE_MB, 60, true)
        );
        assertEquals("Informe a duração do áudio em segundos", exception.getMessage());

        assertNull(validator.validate("audio/m4a", new byte[] { 1 }, null, ONE_MB, 60, false).durationSeconds());
    }

    @Test
    void respectsTheLimitsPassedByTheCaller() {
        IllegalArgumentException tooLong = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("audio/m4a", new byte[] { 1 }, 61, ONE_MB, 60, true)
        );
        assertEquals("O áudio excede a duração máxima de 60 segundos", tooLong.getMessage());

        IllegalArgumentException tooBig = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("audio/m4a", new byte[(int) ONE_MB + 1], 10, ONE_MB, 60, true)
        );
        assertEquals("O áudio excede o limite de 1 MB", tooBig.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("audio/m4a", new byte[] { 1 }, 0, ONE_MB, 60, true));
    }

    @Test
    void rejectsUnsupportedTypesAndEmptyContent() {
        IllegalArgumentException ogg = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("audio/ogg", new byte[] { 1 }, 10, ONE_MB, 60, true)
        );
        assertTrue(ogg.getMessage().startsWith("Formato de áudio não suportado"));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(null, new byte[] { 1 }, 10, ONE_MB, 60, true));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("audio/m4a", new byte[0], 10, ONE_MB, 60, true));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("audio/m4a", null, 10, ONE_MB, 60, true));
    }
}
