package br.com.unify.matchable.common.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.SQLException;

import javax.imageio.ImageIO;
import javax.sql.rowset.serial.SerialBlob;

import jakarta.enterprise.context.ApplicationScoped;
import net.coobird.thumbnailator.Thumbnails;

@ApplicationScoped
public class OidImageService {

    private static final double IMAGE_OUTPUT_QUALITY = 0.75d;

    public byte[] compressToJpeg(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Nenhuma imagem foi enviada no campo 'image'");
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BufferedImage sourceImage = ImageIO.read(inputStream);
            if (sourceImage == null) {
                throw new IllegalArgumentException("O arquivo enviado não é uma imagem JPEG ou PNG válida");
            }

            Thumbnails.of(normalizeImage(sourceImage))
                    .scale(1.0d)
                    .outputFormat("jpg")
                    .outputQuality(IMAGE_OUTPUT_QUALITY)
                    .toOutputStream(outputStream);

            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível processar a imagem enviada", exception);
        }
    }

    public Blob toOidBlob(byte[] imageBytes) {
        try {
            return new SerialBlob(imageBytes);
        } catch (SQLException exception) {
            throw new IllegalStateException("Não foi possível preparar a imagem para armazenamento", exception);
        }
    }

    public byte[] readOidBlob(Blob oid) {
        if (oid == null) {
            return null;
        }

        try {
            return oid.getBytes(1, (int) oid.length());
        } catch (SQLException exception) {
            throw new IllegalStateException("Não foi possível ler a imagem armazenada", exception);
        }
    }

    private BufferedImage normalizeImage(BufferedImage sourceImage) {
        if (!sourceImage.getColorModel().hasAlpha()) {
            return sourceImage;
        }

        BufferedImage normalized = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = normalized.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
        graphics.drawImage(sourceImage, 0, 0, null);
        graphics.dispose();
        return normalized;
    }
}