package br.com.unify.matchable.common.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Blob;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class OidImageServiceTest {

    @Test
    void compressToJpegConvertsTransparentPngIntoOpaqueJpegBytes() throws Exception {
        OidImageService service = new OidImageService();

        BufferedImage source = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(new Color(0, 0, 0, 0));
        graphics.fillRect(0, 0, 12, 12);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(2, 2, 8, 8);
        graphics.dispose();

        ByteArrayOutputStream pngOutput = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "png", pngOutput));

        byte[] compressed = service.compressToJpeg(pngOutput.toByteArray());

        assertTrue(compressed.length > 0);
        BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(compressed));
        assertNotNull(normalized);
        assertFalse(normalized.getColorModel().hasAlpha());
    }

    @Test
    void toOidBlobRoundTripsStoredBytes() {
        OidImageService service = new OidImageService();
        byte[] original = new byte[] { 4, 5, 6, 7 };

        Blob blob = service.toOidBlob(original);

        assertArrayEquals(original, service.readOidBlob(blob));
    }
}