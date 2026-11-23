package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WavIOTest {
    /** Builds a wav file by hand, so the reader is checked against bytes it did not write. */
    private static byte[] wav(int format, int channels, int rate, int bits, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(44 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + data.length).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) format).putShort((short) channels)
            .putInt(rate).putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8)).putShort((short) bits);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data);
        return b.array();
    }

    @Test
    void shouldRoundTripMono16BitWithinQuantisationError() throws IOException {
        float[] source = AudioTestSupport.sine(440.0, 2000);
        SampleData read = WavIO.read(WavIO.write(new SampleData(source, 44100)));
        assertTrue(read.isMono());
        assertEquals(44100, read.getSampleRate());
        assertArrayEquals(source, read.getLeft(), 1e-3f);
    }

    @Test
    void shouldRoundTripStereoKeepingSidesApart() throws IOException {
        float[] left = AudioTestSupport.sine(440.0, 500);
        float[] right = AudioTestSupport.sine(880.0, 500);
        SampleData read = WavIO.read(WavIO.write(new SampleData(left, right, 22050)));
        assertFalse(read.isMono());
        assertEquals(22050, read.getSampleRate());
        assertArrayEquals(left, read.getLeft(), 1e-3f);
        assertArrayEquals(right, read.getRight(), 1e-3f);
    }

    @Test
    void shouldClipValuesOutsideFullScaleWhenWriting() throws IOException {
        SampleData read = WavIO.read(WavIO.write(new SampleData(new float[] {3f, -3f}, 8000)));
        assertEquals(1f, read.getLeft()[0], 1e-3f);
        assertEquals(-1f, read.getLeft()[1], 1e-3f);
    }

    @Test
    void shouldReadEightBitUnsignedPcm() throws IOException {
        SampleData read = WavIO.read(wav(1, 1, 8000, 8, new byte[] {(byte) 128, (byte) 255, 0}));
        assertEquals(0f, read.getLeft()[0], 1e-6f);
        assertEquals(127f / 128f, read.getLeft()[1], 1e-6f);
        assertEquals(-1f, read.getLeft()[2], 1e-6f);
    }

    @Test
    void shouldReadTwentyFourBitPcm() throws IOException {
        // +0.5 is 0x400000, -0.5 is 0xC00000
        byte[] data = {0x00, 0x00, 0x40, 0x00, 0x00, (byte) 0xC0};
        SampleData read = WavIO.read(wav(1, 1, 8000, 24, data));
        assertEquals(0.5f, read.getLeft()[0], 1e-6f);
        assertEquals(-0.5f, read.getLeft()[1], 1e-6f);
    }

    @Test
    void shouldReadThirtyTwoBitFloat() throws IOException {
        ByteBuffer data = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.25f).putFloat(-0.75f);
        SampleData read = WavIO.read(wav(3, 1, 8000, 32, data.array()));
        assertEquals(0.25f, read.getLeft()[0]);
        assertEquals(-0.75f, read.getLeft()[1]);
    }

    @Test
    void shouldKeepTheFirstTwoChannelsOfSurroundFiles() throws IOException {
        ByteBuffer data = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        data.putShort((short) 16384).putShort((short) -16384).putShort((short) 1);
        data.putShort((short) 8192).putShort((short) -8192).putShort((short) 2);
        SampleData read = WavIO.read(wav(1, 3, 8000, 16, data.array()));
        assertEquals(2, read.getLength());
        assertEquals(0.5f, read.getLeft()[0], 1e-4f);
        assertEquals(-0.5f, read.getRight()[0], 1e-4f);
    }

    @Test
    void shouldSkipUnknownChunksBeforeTheData() throws IOException {
        byte[] plain = wav(1, 1, 8000, 16, new byte[] {0, 64});
        ByteBuffer withList = ByteBuffer.allocate(plain.length + 12).order(ByteOrder.LITTLE_ENDIAN);
        withList.put(plain, 0, 36);
        withList.put("LIST".getBytes(StandardCharsets.US_ASCII)).putInt(4).put(new byte[] {1, 2, 3, 4});
        withList.put(plain, 36, plain.length - 36);
        SampleData read = WavIO.read(withList.array());
        assertEquals(0.5f, read.getLeft()[0], 1e-4f);
    }

    @Test
    void shouldRejectFilesThatAreNotWave() {
        assertThrows(IOException.class, () -> WavIO.read(new byte[] {1, 2, 3}));
        assertThrows(IOException.class, () -> WavIO.read("RIFF....AVI LIST".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void shouldRejectCompressedWave() {
        IOException e = assertThrows(IOException.class, () -> WavIO.read(wav(2, 1, 8000, 4, new byte[8])));
        assertTrue(e.getMessage().contains("Unsupported wav format 2"), e.getMessage());
    }

    @Test
    void shouldRejectAFileWithoutData() {
        byte[] truncated = java.util.Arrays.copyOf(wav(1, 1, 8000, 16, new byte[0]), 36);
        assertThrows(IOException.class, () -> WavIO.read(truncated));
    }
}
