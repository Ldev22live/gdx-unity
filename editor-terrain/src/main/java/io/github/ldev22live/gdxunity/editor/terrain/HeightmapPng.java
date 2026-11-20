package io.github.ldev22live.gdxunity.editor.terrain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A heightmap as a PNG image (roadmap M3.5): written as 16-bit grayscale, one pixel per grid point, so heights keep 65,536 steps
 * between the lowest and the highest. The writer stores that range in a text chunk ({@value #RANGE_KEY}), so a Forgeroom heightmap
 * comes back at the heights it left with. The reader takes the grayscale or colour images other tools write, 8 or 16 bits per channel
 * (the red channel of a colour image; alpha is ignored), every PNG row filter, and the range chunk when there is one; it refuses
 * interlaced and palette images with the reason. Written here rather than with {@code javax.imageio}, which has no place in a portable
 * engine and offers no 16-bit text-chunk round trip.
 */
public final class HeightmapPng {
    /** The text chunk keyword that holds "lowest highest" as written. */
    public static final String RANGE_KEY = "forgeroom.heightRange";
    /** Largest 16-bit sample. */
    public static final int MAX_SAMPLE = 65_535;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int COLOR_GRAY = 0;
    private static final int COLOR_RGB = 2;
    private static final int COLOR_PALETTE = 3;
    private static final int COLOR_GRAY_ALPHA = 4;
    private static final int COLOR_RGBA = 6;

    /**
     * A decoded heightmap.
     *
     * @param width   pixels per row
     * @param height  rows
     * @param samples one value per pixel, row by row, 0 to {@link #MAX_SAMPLE} (an 8-bit image is scaled up)
     * @param lowest  the height sample 0 stands for, or NaN when the image does not say
     * @param highest the height {@link #MAX_SAMPLE} stands for, or NaN when the image does not say
     */
    public record Heightmap(int width, int height, int[] samples, float lowest, float highest) {
        public Heightmap {
            Objects.requireNonNull(samples, "samples cannot be null");
            if (samples.length != width * height) {
                throw new IllegalArgumentException("Expected " + width * height + " samples, got " + samples.length);
            }
        }

        /** Whether the image says which heights its samples stand for. */
        public boolean hasRange() {
            return !Float.isNaN(lowest) && !Float.isNaN(highest);
        }
    }

    private HeightmapPng() { }

    /**
     * Writes samples as a 16-bit grayscale PNG with the height range they stand for.
     *
     * @param samples one per pixel, row by row, each 0 to {@link #MAX_SAMPLE}
     */
    public static byte[] write(int width, int height, int[] samples, float lowest, float highest) {
        if (width < 1 || height < 1 || samples.length != width * height) {
            throw new IllegalArgumentException("A " + width + " x " + height + " image needs " + width * height + " samples, got "
                + samples.length);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE);
        ByteBuffer header = ByteBuffer.allocate(13);
        header.putInt(width).putInt(height).put((byte) 16).put((byte) COLOR_GRAY).put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(out, "IHDR", header.array());
        String range = String.format(Locale.ROOT, "%s %s", lowest, highest);
        chunk(out, "tEXt", (RANGE_KEY + '\0' + range).getBytes(StandardCharsets.ISO_8859_1));
        byte[] raw = new byte[height * (1 + width * 2)];
        int at = 0;
        for (int y = 0; y < height; y++) {
            raw[at++] = 0; // filter: none
            for (int x = 0; x < width; x++) {
                int sample = samples[y * width + x];
                if (sample < 0 || sample > MAX_SAMPLE) {
                    throw new IllegalArgumentException("A 16-bit sample is 0 to " + MAX_SAMPLE + ": " + sample);
                }
                raw[at++] = (byte) (sample >> 8);
                raw[at++] = (byte) sample;
            }
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        chunk(out, "IDAT", compressed.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /**
     * Reads a PNG heightmap.
     *
     * @throws IOException if it is not a PNG, is interlaced or a palette image, or is damaged; the message says which
     */
    public static Heightmap read(byte[] png) throws IOException {
        Objects.requireNonNull(png, "png cannot be null");
        if (png.length < SIGNATURE.length || !Arrays.equals(Arrays.copyOf(png, SIGNATURE.length), SIGNATURE)) {
            throw new IOException("Not a PNG image");
        }
        ByteBuffer in = ByteBuffer.wrap(png, SIGNATURE.length, png.length - SIGNATURE.length);
        int width = 0;
        int height = 0;
        int depth = 0;
        int color = -1;
        float lowest = Float.NaN;
        float highest = Float.NaN;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        boolean ended = false;
        while (!ended) {
            if (in.remaining() < 12) {
                throw new IOException("The PNG ends before its last chunk");
            }
            int length = in.getInt();
            byte[] type = new byte[4];
            in.get(type);
            if (length < 0 || length > in.remaining() - 4) {
                throw new IOException("A PNG chunk is longer than the file");
            }
            byte[] body = new byte[length];
            in.get(body);
            in.getInt(); // CRC: a damaged body shows up when it is unpacked
            String name = new String(type, StandardCharsets.ISO_8859_1);
            switch (name) {
                case "IHDR" -> {
                    ByteBuffer h = ByteBuffer.wrap(body);
                    width = h.getInt();
                    height = h.getInt();
                    depth = h.get() & 0xFF;
                    color = h.get() & 0xFF;
                    h.get();
                    h.get();
                    if ((h.get() & 0xFF) != 0) {
                        throw new IOException("Interlaced PNG heightmaps are not supported; save it without interlacing");
                    }
                    if (color == COLOR_PALETTE) {
                        throw new IOException("A palette PNG is not a heightmap; save it as grayscale");
                    }
                    if (depth != 8 && depth != 16) {
                        throw new IOException("Heightmaps must have 8 or 16 bits per channel, this one has " + depth);
                    }
                    if (color != COLOR_GRAY && color != COLOR_RGB && color != COLOR_GRAY_ALPHA && color != COLOR_RGBA) {
                        throw new IOException("Unknown PNG colour type " + color);
                    }
                }
                case "tEXt" -> {
                    String text = new String(body, StandardCharsets.ISO_8859_1);
                    int zero = text.indexOf('\0');
                    if (zero > 0 && text.substring(0, zero).equals(RANGE_KEY)) {
                        String[] parts = text.substring(zero + 1).trim().split("\\s+");
                        if (parts.length == 2) {
                            try {
                                lowest = Float.parseFloat(parts[0]);
                                highest = Float.parseFloat(parts[1]);
                            } catch (NumberFormatException e) {
                                throw new IOException("The heightmap's range is not two numbers: " + text.substring(zero + 1), e);
                            }
                        }
                    }
                }
                case "IDAT" -> data.writeBytes(body);
                case "IEND" -> ended = true;
                default -> { } // other chunks (gamma, colour profile, time) do not change the samples
            }
        }
        if (width < 1 || height < 1 || color < 0) {
            throw new IOException("The PNG has no image header");
        }
        int channels = switch (color) {
            case COLOR_GRAY -> 1;
            case COLOR_GRAY_ALPHA -> 2;
            case COLOR_RGB -> 3;
            default -> 4;
        };
        int bytesPerPixel = channels * depth / 8;
        int stride = width * bytesPerPixel;
        byte[] raw = inflate(data.toByteArray(), height * (stride + 1));
        int[] samples = new int[width * height];
        byte[] previous = new byte[stride];
        byte[] row = new byte[stride];
        for (int y = 0; y < height; y++) {
            int filter = raw[y * (stride + 1)] & 0xFF;
            System.arraycopy(raw, y * (stride + 1) + 1, row, 0, stride);
            unfilter(filter, row, previous, bytesPerPixel);
            for (int x = 0; x < width; x++) {
                int at = x * bytesPerPixel; // the first channel: gray, or red
                samples[y * width + x] = depth == 16 ? ((row[at] & 0xFF) << 8) | (row[at + 1] & 0xFF) : (row[at] & 0xFF) * 257;
            }
            byte[] swap = previous;
            previous = row;
            row = swap;
        }
        return new Heightmap(width, height, samples, lowest, highest);
    }

    private static byte[] inflate(byte[] compressed, int expected) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] raw = new byte[expected];
        try {
            int filled = 0;
            while (filled < expected && !inflater.finished()) {
                int read = inflater.inflate(raw, filled, expected - filled);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                filled += read;
            }
            if (filled != expected) {
                throw new IOException("The PNG's image data is " + filled + " bytes, not the " + expected + " its size needs");
            }
        } catch (DataFormatException e) {
            throw new IOException("The PNG's image data is damaged: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
        return raw;
    }

    /** Undoes a PNG row filter in place (types 0 to 4 of the PNG specification). */
    private static void unfilter(int filter, byte[] row, byte[] previous, int bpp) throws IOException {
        for (int i = 0; i < row.length; i++) {
            int left = i >= bpp ? row[i - bpp] & 0xFF : 0;
            int up = previous[i] & 0xFF;
            int upLeft = i >= bpp ? previous[i - bpp] & 0xFF : 0;
            int value = row[i] & 0xFF;
            switch (filter) {
                case 0 -> { }
                case 1 -> value += left;
                case 2 -> value += up;
                case 3 -> value += (left + up) / 2;
                case 4 -> value += paeth(left, up, upLeft);
                default -> throw new IOException("Unknown PNG row filter " + filter);
            }
            row[i] = (byte) value;
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] body) {
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        out.writeBytes(ByteBuffer.allocate(4).putInt(body.length).array());
        out.writeBytes(name);
        out.writeBytes(body);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(body);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
