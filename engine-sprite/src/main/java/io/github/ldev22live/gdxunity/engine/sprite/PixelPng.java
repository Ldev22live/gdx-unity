package io.github.ldev22live.gdxunity.engine.sprite;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/** PNG encoding and decoding of {@code 0xRRGGBBAA} pixel arrays (through javax.imageio; no display needed). */
public final class PixelPng {
    private PixelPng() { }

    /** A decoded image. */
    public record Image(int width, int height, int[] pixels) { }

    /** Encodes pixels (row by row, {@code 0xRRGGBBAA}) as a PNG. */
    public static byte[] encode(int width, int height, int[] pixels) throws IOException {
        Objects.requireNonNull(pixels, "pixels cannot be null");
        if (width < 1 || height < 1 || pixels.length != width * height) {
            throw new IllegalArgumentException("pixels do not fit " + width + "x" + height + ": " + pixels.length);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            image.setRGB(i % width, i / width, (p << 24) | (p >>> 8));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("no PNG writer available");
        }
        return out.toByteArray();
    }

    /** Decodes a PNG (or any format ImageIO reads) to {@code 0xRRGGBBAA} pixels. */
    public static Image decode(byte[] data) throws IOException {
        Objects.requireNonNull(data, "data cannot be null");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IOException("not an image ImageIO can read");
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                out[y * w + x] = (argb << 8) | (argb >>> 24);
            }
        }
        return new Image(w, h, out);
    }
}
