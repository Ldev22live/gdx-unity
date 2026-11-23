package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.backends.lwjgl3.audio.OggInputStream;
import io.github.ldev22live.gdxunity.engine.audio.SampleData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Reads Ogg Vorbis clips (Phase 20-C) with the decoder libGDX's desktop backend already carries ({@link OggInputStream}, on
 * {@code jorbis}), so no dependency was added. Only decoding exists: nothing in the project can write Ogg Vorbis.
 */
public final class OggClips {
    private static final int BYTES_PER_SAMPLE = 2;
    private static final float INT16_SCALE = 32768f;
    private static final int READ_BLOCK = 16 * 1024;
    private static final int MAX_CHANNELS = 2;

    private OggClips() {
    }

    /**
     * Decodes a whole Ogg Vorbis file.
     *
     * @return the clip, mono or stereo, at the file's sample rate
     * @throws IOException if the bytes are not Ogg Vorbis, hold more than two channels, or hold no sound
     */
    public static SampleData read(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        byte[] pcm;
        int channels;
        int sampleRate;
        try (OggInputStream stream = new OggInputStream(new ByteArrayInputStream(bytes))) {
            channels = stream.getChannels();
            sampleRate = stream.getSampleRate();
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            byte[] block = new byte[READ_BLOCK];
            for (int read = stream.read(block); read > 0; read = stream.read(block)) {
                decoded.write(block, 0, read);
            }
            pcm = decoded.toByteArray();
        } catch (RuntimeException e) {
            throw new IOException("Not an Ogg Vorbis file: " + e.getMessage(), e);
        }
        if (channels < 1) {
            // The decoder does not fail on bytes that are not Ogg: it simply finds no stream
            throw new IOException("Not an Ogg Vorbis file: no audio stream found");
        }
        if (channels > MAX_CHANNELS) {
            throw new IOException("Ogg clips must be mono or stereo, this one has " + channels + " channels");
        }
        int frames = pcm.length / (BYTES_PER_SAMPLE * channels);
        if (frames == 0) {
            throw new IOException("The Ogg file holds no sound");
        }
        float[] left = new float[frames];
        float[] right = channels == 2 ? new float[frames] : null;
        for (int frame = 0; frame < frames; frame++) {
            int at = frame * channels * BYTES_PER_SAMPLE;
            left[frame] = sample(pcm, at);
            if (right != null) {
                right[frame] = sample(pcm, at + BYTES_PER_SAMPLE);
            }
        }
        return right == null ? new SampleData(left, sampleRate) : new SampleData(left, right, sampleRate);
    }

    /** One little-endian signed 16 bit sample as -1 to 1. */
    private static float sample(byte[] pcm, int at) {
        return (short) ((pcm[at] & 0xFF) | (pcm[at + 1] << 8)) / INT16_SCALE;
    }
}
