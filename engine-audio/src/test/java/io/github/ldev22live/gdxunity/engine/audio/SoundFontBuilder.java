package io.github.ldev22live.gdxunity.engine.audio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a tiny SoundFont 2 file from scratch, so the reader is tested against bytes laid out as the specification
 * says rather than against its own output: one looping 440 Hz sine sample, one instrument, one preset.
 */
final class SoundFontBuilder {
    static final int SAMPLE_FRAMES = 4410;
    static final int SAMPLE_RATE = 44100;
    static final int ROOT_KEY = 69;

    // generator operators
    static final int PAN = 17;
    static final int ATTACK = 34;
    static final int RELEASE = 38;
    static final int INSTRUMENT = 41;
    static final int KEY_RANGE = 43;
    static final int ATTENUATION = 48;
    static final int SAMPLE_ID = 53;
    static final int SAMPLE_MODES = 54;
    static final int ROOT_KEY_OVERRIDE = 58;

    private final List<List<int[]>> instrumentZones = new ArrayList<>();
    private final List<int[]> presetGenerators = new ArrayList<>();
    private String presetName = "Test Piano";
    private int bank;
    private int program;

    SoundFontBuilder presetName(String name, int bank, int program) {
        this.presetName = name;
        this.bank = bank;
        this.program = program;
        return this;
    }

    /** Adds an instrument zone from {operator, amount} pairs; the sample id is appended automatically. */
    SoundFontBuilder zone(int... operatorAmountPairs) {
        List<int[]> generators = new ArrayList<>();
        for (int i = 0; i < operatorAmountPairs.length; i += 2) {
            generators.add(new int[] {operatorAmountPairs[i], operatorAmountPairs[i + 1]});
        }
        generators.add(new int[] {SAMPLE_ID, 0});
        instrumentZones.add(generators);
        return this;
    }

    static int keyRange(int low, int high) {
        return low | (high << 8);
    }

    byte[] build() {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        ByteBuffer samples = ByteBuffer.allocate((SAMPLE_FRAMES + 46) * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < SAMPLE_FRAMES; i++) {
            samples.putShort((short) Math.round(16000 * Math.sin(2.0 * Math.PI * 440.0 * i / SAMPLE_RATE)));
        }
        pcm.writeBytes(samples.array());

        ByteBuffer shdr = ByteBuffer.allocate(46 * 2).order(ByteOrder.LITTLE_ENDIAN);
        name(shdr, "Sine440");
        shdr.putInt(0).putInt(SAMPLE_FRAMES).putInt(0).putInt(SAMPLE_FRAMES).putInt(SAMPLE_RATE);
        shdr.put((byte) ROOT_KEY).put((byte) 0).putShort((short) 0).putShort((short) 1);
        name(shdr, "EOS");
        shdr.position(46 * 2);

        ByteBuffer phdr = ByteBuffer.allocate(38 * 2).order(ByteOrder.LITTLE_ENDIAN);
        name(phdr, presetName);
        phdr.putShort((short) program).putShort((short) bank).putShort((short) 0).putInt(0).putInt(0).putInt(0);
        name(phdr, "EOP");
        phdr.putShort((short) 0).putShort((short) 0).putShort((short) 1).putInt(0).putInt(0).putInt(0);

        List<int[]> pgen = new ArrayList<>(presetGenerators);
        pgen.add(new int[] {INSTRUMENT, 0});
        ByteBuffer pbag = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        pbag.putShort((short) 0).putShort((short) 0).putShort((short) pgen.size()).putShort((short) 0);

        ByteBuffer inst = ByteBuffer.allocate(22 * 2).order(ByteOrder.LITTLE_ENDIAN);
        name(inst, "Sine");
        inst.putShort((short) 0);
        name(inst, "EOI");
        inst.putShort((short) instrumentZones.size());

        ByteBuffer ibag = ByteBuffer.allocate(4 * (instrumentZones.size() + 1)).order(ByteOrder.LITTLE_ENDIAN);
        List<int[]> igen = new ArrayList<>();
        for (List<int[]> zone : instrumentZones) {
            ibag.putShort((short) igen.size()).putShort((short) 0);
            igen.addAll(zone);
        }
        ibag.putShort((short) igen.size()).putShort((short) 0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        chunk(info, "ifil", new byte[] {2, 0, 1, 0});
        ByteArrayOutputStream sdta = new ByteArrayOutputStream();
        chunk(sdta, "smpl", pcm.toByteArray());
        ByteArrayOutputStream pdta = new ByteArrayOutputStream();
        chunk(pdta, "phdr", phdr.array());
        chunk(pdta, "pbag", pbag.array());
        chunk(pdta, "pmod", new byte[10]);
        chunk(pdta, "pgen", generators(pgen, true));
        chunk(pdta, "inst", inst.array());
        chunk(pdta, "ibag", ibag.array());
        chunk(pdta, "imod", new byte[10]);
        chunk(pdta, "igen", generators(igen, true));
        chunk(pdta, "shdr", shdr.array());
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("sfbk".getBytes(StandardCharsets.US_ASCII));
        list(body, "INFO", info.toByteArray());
        list(body, "sdta", sdta.toByteArray());
        list(body, "pdta", pdta.toByteArray());
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le(body.size()));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] generators(List<int[]> generators, boolean terminal) {
        ByteBuffer buffer = ByteBuffer.allocate((generators.size() + (terminal ? 1 : 0)) * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int[] generator : generators) {
            buffer.putShort((short) generator[0]).putShort((short) generator[1]);
        }
        return buffer.array();
    }

    private static void name(ByteBuffer buffer, String text) {
        byte[] bytes = new byte[20];
        byte[] raw = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, bytes, 0, Math.min(raw.length, 19));
        buffer.put(bytes);
    }

    private static byte[] le(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private static void chunk(ByteArrayOutputStream out, String id, byte[] data) {
        out.writeBytes(id.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le(data.length));
        out.writeBytes(data);
        if ((data.length & 1) == 1) {
            out.write(0);
        }
    }

    private static void list(ByteArrayOutputStream out, String type, byte[] data) {
        out.writeBytes("LIST".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le(data.length + 4));
        out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(data);
    }
}
