package io.github.ldev22live.gdxunity.engine.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Reads and writes RIFF/WAVE files: 8, 16, 24 and 32 bit PCM and 32 bit float for reading, 16 bit PCM for writing.
 * Files with more than two channels keep the first two. Other formats (ogg, mp3, compressed wav) are rejected.
 */
public final class WavIO {
    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_FLOAT = 3;
    private static final int FORMAT_EXTENSIBLE = 0xFFFE;
    private static final int HEADER_SIZE = 44;
    private static final float INT16_SCALE = 32768f;
    private static final float INT24_SCALE = 8388608f;
    private static final float INT32_SCALE = 2147483648f;

    private WavIO() { }

    /** Decodes a WAVE file. */
    public static SampleData read(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 12 || !tag(buffer, 0).equals("RIFF") || !tag(buffer, 8).equals("WAVE")) {
            throw new IOException("Not a RIFF/WAVE file");
        }
        int format = -1;
        int channels = 0;
        int sampleRate = 0;
        int bits = 0;
        int dataStart = -1;
        int dataLength = 0;
        int position = 12;
        while (position + 8 <= bytes.length) {
            String id = tag(buffer, position);
            long size = buffer.getInt(position + 4) & 0xFFFFFFFFL;
            int body = position + 8;
            if (id.equals("fmt ")) {
                if (size < 16 || body + 16 > bytes.length) {
                    throw new IOException("Damaged fmt chunk");
                }
                format = buffer.getShort(body) & 0xFFFF;
                channels = buffer.getShort(body + 2) & 0xFFFF;
                sampleRate = buffer.getInt(body + 4);
                bits = buffer.getShort(body + 14) & 0xFFFF;
                if (format == FORMAT_EXTENSIBLE && size >= 26) {
                    format = buffer.getShort(body + 24) & 0xFFFF;
                }
            } else if (id.equals("data")) {
                dataStart = body;
                dataLength = (int) Math.min(size, bytes.length - body);
                break;
            }
            position = (int) Math.min((long) body + size + (size & 1), Integer.MAX_VALUE);
        }
        if (format < 0 || dataStart < 0) {
            throw new IOException("The file has no fmt or data chunk");
        }
        if (format != FORMAT_PCM && format != FORMAT_FLOAT) {
            throw new IOException("Unsupported wav format " + format + " (only PCM and float are supported)");
        }
        if (channels < 1 || sampleRate <= 0) {
            throw new IOException("Invalid wav header: " + channels + " channels at " + sampleRate + " Hz");
        }
        if (!(bits == 8 || bits == 16 || bits == 24 || bits == 32) || (format == FORMAT_FLOAT && bits != 32)) {
            throw new IOException("Unsupported sample size: " + bits + " bit " + (format == FORMAT_FLOAT ? "float" : "pcm"));
        }
        int bytesPerSample = bits / 8;
        int frames = dataLength / (bytesPerSample * channels);
        float[] left = new float[frames];
        float[] right = channels > 1 ? new float[frames] : left;
        for (int frame = 0; frame < frames; frame++) {
            int at = dataStart + frame * bytesPerSample * channels;
            left[frame] = decode(buffer, at, bits, format == FORMAT_FLOAT);
            if (channels > 1) {
                right[frame] = decode(buffer, at + bytesPerSample, bits, format == FORMAT_FLOAT);
            }
        }
        return new SampleData(left, right, sampleRate);
    }

    private static String tag(ByteBuffer buffer, int at) {
        byte[] id = new byte[4];
        buffer.get(at, id);
        return new String(id, StandardCharsets.US_ASCII);
    }

    private static float decode(ByteBuffer buffer, int at, int bits, boolean isFloat) {
        if (isFloat) {
            return buffer.getFloat(at);
        }
        return switch (bits) {
            case 8 -> ((buffer.get(at) & 0xFF) - 128) / 128f;
            case 16 -> buffer.getShort(at) / INT16_SCALE;
            case 24 -> {
                int value = (buffer.get(at) & 0xFF) | ((buffer.get(at + 1) & 0xFF) << 8) | (buffer.get(at + 2) << 16);
                yield value / INT24_SCALE;
            }
            default -> buffer.getInt(at) / INT32_SCALE;
        };
    }

    /** Encodes 16 bit PCM. Mono audio is written as one channel. Values outside -1..1 are clipped. */
    public static byte[] write(SampleData sample) {
        Objects.requireNonNull(sample, "sample cannot be null");
        int channels = sample.isMono() ? 1 : 2;
        int frames = sample.getLength();
        int dataLength = frames * channels * 2;
        ByteBuffer out = ByteBuffer.allocate(HEADER_SIZE + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.putInt(36 + dataLength);
        out.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        out.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        out.putInt(16);
        out.putShort((short) FORMAT_PCM);
        out.putShort((short) channels);
        out.putInt(sample.getSampleRate());
        out.putInt(sample.getSampleRate() * channels * 2);
        out.putShort((short) (channels * 2));
        out.putShort((short) 16);
        out.put("data".getBytes(StandardCharsets.US_ASCII));
        out.putInt(dataLength);
        for (int i = 0; i < frames; i++) {
            out.putShort(toInt16(sample.getLeft()[i]));
            if (channels == 2) {
                out.putShort(toInt16(sample.getRight()[i]));
            }
        }
        return out.array();
    }

    private static short toInt16(float value) {
        float clipped = Math.min(Math.max(value, -1f), 1f);
        return (short) Math.round(clipped * (INT16_SCALE - 1f));
    }
}
