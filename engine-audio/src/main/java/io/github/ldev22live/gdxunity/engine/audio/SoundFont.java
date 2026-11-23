package io.github.ldev22live.gdxunity.engine.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A SoundFont 2 (.sf2) bank, read from its bytes. Supported: 16 bit sample data, presets with instrument zones,
 * key and velocity ranges, root key, tuning, panning, attenuation, the volume envelope (attack, decay, sustain,
 * release) and sample loops. Not supported: filters, modulators, 24 bit extension data, stereo sample links
 * (a linked pair plays as two mono regions) and SF3 (compressed) banks.
 */
public final class SoundFont {
    // Generator operators of the SF2 specification that are used here
    private static final int GEN_START_OFFSET = 0;
    private static final int GEN_END_OFFSET = 1;
    private static final int GEN_START_LOOP_OFFSET = 2;
    private static final int GEN_END_LOOP_OFFSET = 3;
    private static final int GEN_PAN = 17;
    private static final int GEN_ATTACK = 34;
    private static final int GEN_DECAY = 36;
    private static final int GEN_SUSTAIN = 37;
    private static final int GEN_RELEASE = 38;
    private static final int GEN_INSTRUMENT = 41;
    private static final int GEN_KEY_RANGE = 43;
    private static final int GEN_VEL_RANGE = 44;
    private static final int GEN_ATTENUATION = 48;
    private static final int GEN_COARSE_TUNE = 51;
    private static final int GEN_FINE_TUNE = 52;
    private static final int GEN_SAMPLE_ID = 53;
    private static final int GEN_SAMPLE_MODES = 54;
    private static final int GEN_SCALE_TUNING = 56;
    private static final int GEN_ROOT_KEY = 58;

    private static final int PHDR_SIZE = 38;
    private static final int BAG_SIZE = 4;
    private static final int GEN_RECORD_SIZE = 4;
    private static final int INST_SIZE = 22;
    private static final int SHDR_SIZE = 46;
    private static final int NAME_LENGTH = 20;
    /** Envelope time default (timecents): about one millisecond. */
    private static final int DEFAULT_TIMECENTS = -12000;
    private static final int TIMECENTS_PER_OCTAVE = 1200;
    /** Longest envelope time accepted, seconds. */
    private static final float MAX_ENVELOPE_SECONDS = 20f;
    private static final int DEFAULT_SCALE_TUNING = 100;
    private static final int CENTIBELS_PER_DECADE_OF_AMPLITUDE = 200;

    /** A playable sample area of an instrument with all settings resolved. */
    public static final class Region {
        final int minKey;
        final int maxKey;
        final int minVelocity;
        final int maxVelocity;
        final int sampleStart;
        final int sampleEnd;
        final int loopStart;
        final int loopEnd;
        final int sampleRate;
        final int rootKey;
        final float tuneCents;
        final float scaleTuning;
        final boolean loops;
        final boolean loopsUntilRelease;
        final float attenuationCentibels;
        final float pan;
        final float attack;
        final float decay;
        final float sustainLevel;
        final float release;

        Region(Map<Integer, Integer> g, Map<Integer, Integer> presetGenerators, Sample sample) {
            int[] key = range(g.get(GEN_KEY_RANGE), presetGenerators.get(GEN_KEY_RANGE));
            int[] velocity = range(g.get(GEN_VEL_RANGE), presetGenerators.get(GEN_VEL_RANGE));
            minKey = key[0];
            maxKey = key[1];
            minVelocity = velocity[0];
            maxVelocity = velocity[1];
            int startShift = g.getOrDefault(GEN_START_OFFSET, 0);
            int endShift = g.getOrDefault(GEN_END_OFFSET, 0);
            sampleStart = Math.max(sample.start + startShift, 0);
            sampleEnd = Math.max(sample.end + endShift, sampleStart);
            loopStart = sample.startLoop + g.getOrDefault(GEN_START_LOOP_OFFSET, 0);
            loopEnd = sample.endLoop + g.getOrDefault(GEN_END_LOOP_OFFSET, 0);
            sampleRate = sample.rate;
            int override = g.getOrDefault(GEN_ROOT_KEY, -1);
            rootKey = override >= 0 ? override : sample.originalPitch;
            tuneCents = (g.getOrDefault(GEN_COARSE_TUNE, 0) + presetGenerators.getOrDefault(GEN_COARSE_TUNE, 0)) * 100f
                + g.getOrDefault(GEN_FINE_TUNE, 0) + presetGenerators.getOrDefault(GEN_FINE_TUNE, 0) + sample.pitchCorrection;
            scaleTuning = g.getOrDefault(GEN_SCALE_TUNING, DEFAULT_SCALE_TUNING) / 100f;
            int mode = g.getOrDefault(GEN_SAMPLE_MODES, 0) & 3;
            loops = (mode == 1 || mode == 3) && loopEnd > loopStart;
            loopsUntilRelease = mode == 3;
            attenuationCentibels = Math.max(g.getOrDefault(GEN_ATTENUATION, 0) + presetGenerators.getOrDefault(GEN_ATTENUATION, 0), 0);
            pan = Math.min(Math.max((g.getOrDefault(GEN_PAN, 0) + presetGenerators.getOrDefault(GEN_PAN, 0)) / 500f, -1f), 1f);
            attack = seconds(g.getOrDefault(GEN_ATTACK, DEFAULT_TIMECENTS));
            decay = seconds(g.getOrDefault(GEN_DECAY, DEFAULT_TIMECENTS));
            release = seconds(g.getOrDefault(GEN_RELEASE, DEFAULT_TIMECENTS));
            float sustainCentibels = Math.max(g.getOrDefault(GEN_SUSTAIN, 0), 0);
            sustainLevel = (float) Math.pow(10.0, -sustainCentibels / CENTIBELS_PER_DECADE_OF_AMPLITUDE);
        }

        boolean matches(int key, int velocity) {
            return key >= minKey && key <= maxKey && velocity >= minVelocity && velocity <= maxVelocity;
        }
    }

    /** A preset (an instrument sound a player picks by bank and program number). */
    public static final class Preset {
        private final String name;
        private final int bank;
        private final int program;
        private final List<Region> regions;

        Preset(String name, int bank, int program, List<Region> regions) {
            this.name = name;
            this.bank = bank;
            this.program = program;
            this.regions = Collections.unmodifiableList(regions);
        }

        public String getName() {
            return name;
        }

        public int getBank() {
            return bank;
        }

        public int getProgram() {
            return program;
        }

        /** Regions that sound for the given key and velocity (1..127). */
        List<Region> regionsFor(int key, int velocity) {
            List<Region> matching = new ArrayList<>();
            for (Region region : regions) {
                if (region.matches(key, velocity)) {
                    matching.add(region);
                }
            }
            return matching;
        }
    }

    private static final class Sample {
        int start;
        int end;
        int startLoop;
        int endLoop;
        int rate;
        int originalPitch;
        int pitchCorrection;
    }

    private final short[] samples;
    private final List<Preset> presets;

    private SoundFont(short[] samples, List<Preset> presets) {
        this.samples = samples;
        this.presets = Collections.unmodifiableList(presets);
    }

    /** The presets in the file, in file order. */
    public List<Preset> getPresets() {
        return presets;
    }

    /** The 16 bit sample data of the whole bank. */
    short[] getSamples() {
        return samples;
    }

    /** The preset with the given bank and program, or null. */
    public Preset findPreset(int bank, int program) {
        for (Preset preset : presets) {
            if (preset.bank == bank && preset.program == program) {
                return preset;
            }
        }
        return null;
    }

    /** Parses an .sf2 file. */
    public static SoundFont read(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 12 || !tag(buffer, 0).equals("RIFF") || !tag(buffer, 8).equals("sfbk")) {
            throw new IOException("Not a SoundFont 2 file (RIFF/sfbk header missing)");
        }
        short[] pcm = null;
        Map<String, int[]> tables = new HashMap<>();
        int position = 12;
        while (position + 12 <= bytes.length) {
            String id = tag(buffer, position);
            long size = buffer.getInt(position + 4) & 0xFFFFFFFFL;
            int body = position + 8;
            if (id.equals("LIST")) {
                String type = tag(buffer, body);
                int end = (int) Math.min((long) body + size, bytes.length);
                if (type.equals("sdta")) {
                    pcm = readSampleData(buffer, body + 4, end);
                } else if (type.equals("pdta")) {
                    readTables(buffer, body + 4, end, tables);
                }
            }
            position = (int) Math.min((long) body + size + (size & 1), Integer.MAX_VALUE);
        }
        if (pcm == null) {
            throw new IOException("The SoundFont has no 16 bit sample data (smpl chunk)");
        }
        for (String needed : new String[] {"phdr", "pbag", "pgen", "inst", "ibag", "igen", "shdr"}) {
            if (!tables.containsKey(needed)) {
                throw new IOException("The SoundFont is missing its " + needed + " table");
            }
        }
        return new SoundFont(pcm, buildPresets(buffer, tables));
    }

    private static short[] readSampleData(ByteBuffer buffer, int start, int end) {
        int position = start;
        while (position + 8 <= end) {
            String id = tag(buffer, position);
            long size = buffer.getInt(position + 4) & 0xFFFFFFFFL;
            int body = position + 8;
            if (id.equals("smpl")) {
                int count = (int) (Math.min(size, end - body) / 2);
                short[] pcm = new short[count];
                for (int i = 0; i < count; i++) {
                    pcm[i] = buffer.getShort(body + i * 2);
                }
                return pcm;
            }
            position = (int) Math.min((long) body + size + (size & 1), end);
        }
        return null;
    }

    private static void readTables(ByteBuffer buffer, int start, int end, Map<String, int[]> tables) {
        int position = start;
        while (position + 8 <= end) {
            String id = tag(buffer, position);
            long size = buffer.getInt(position + 4) & 0xFFFFFFFFL;
            tables.put(id, new int[] {position + 8, (int) Math.min(size, end - position - 8)});
            position = (int) Math.min((long) position + 8 + size + (size & 1), end);
        }
    }

    private static List<Preset> buildPresets(ByteBuffer buffer, Map<String, int[]> tables) throws IOException {
        int presetCount = tables.get("phdr")[1] / PHDR_SIZE - 1;
        int instrumentCount = tables.get("inst")[1] / INST_SIZE - 1;
        int sampleCount = tables.get("shdr")[1] / SHDR_SIZE - 1;
        if (presetCount < 0 || instrumentCount < 0 || sampleCount < 0) {
            throw new IOException("The SoundFont has an empty preset, instrument or sample table");
        }
        Sample[] sampleHeaders = new Sample[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int at = tables.get("shdr")[0] + i * SHDR_SIZE;
            Sample sample = new Sample();
            sample.start = buffer.getInt(at + NAME_LENGTH);
            sample.end = buffer.getInt(at + NAME_LENGTH + 4);
            sample.startLoop = buffer.getInt(at + NAME_LENGTH + 8);
            sample.endLoop = buffer.getInt(at + NAME_LENGTH + 12);
            sample.rate = buffer.getInt(at + NAME_LENGTH + 16);
            sample.originalPitch = buffer.get(at + NAME_LENGTH + 20) & 0xFF;
            sample.pitchCorrection = buffer.get(at + NAME_LENGTH + 21);
            sampleHeaders[i] = sample;
        }
        List<List<Map<Integer, Integer>>> instrumentZones = new ArrayList<>();
        for (int i = 0; i < instrumentCount; i++) {
            int bagStart = u16(buffer, tables.get("inst")[0] + i * INST_SIZE + NAME_LENGTH);
            int bagEnd = u16(buffer, tables.get("inst")[0] + (i + 1) * INST_SIZE + NAME_LENGTH);
            instrumentZones.add(zones(buffer, tables.get("ibag"), tables.get("igen"), bagStart, bagEnd));
        }
        List<Preset> presets = new ArrayList<>();
        for (int i = 0; i < presetCount; i++) {
            int at = tables.get("phdr")[0] + i * PHDR_SIZE;
            String name = text(buffer, at, NAME_LENGTH);
            int program = u16(buffer, at + NAME_LENGTH);
            int bank = u16(buffer, at + NAME_LENGTH + 2);
            int bagStart = u16(buffer, at + NAME_LENGTH + 4);
            int bagEnd = u16(buffer, at + PHDR_SIZE + NAME_LENGTH + 4);
            List<Region> regions = new ArrayList<>();
            for (Map<Integer, Integer> presetZone : zones(buffer, tables.get("pbag"), tables.get("pgen"), bagStart, bagEnd)) {
                Integer instrument = presetZone.get(GEN_INSTRUMENT);
                if (instrument == null || instrument < 0 || instrument >= instrumentCount) {
                    continue;
                }
                for (Map<Integer, Integer> zone : instrumentZones.get(instrument)) {
                    Integer sampleId = zone.get(GEN_SAMPLE_ID);
                    if (sampleId == null || sampleId < 0 || sampleId >= sampleCount) {
                        continue;
                    }
                    regions.add(new Region(zone, presetZone, sampleHeaders[sampleId]));
                }
            }
            presets.add(new Preset(name, bank, program, regions));
        }
        return presets;
    }

    /**
     * Zones of one preset or instrument: each zone's generators, with the global zone (the first zone, when it has
     * no instrument or sample) folded into every other zone.
     */
    private static List<Map<Integer, Integer>> zones(ByteBuffer buffer, int[] bagTable, int[] genTable,
                                                     int bagStart, int bagEnd) {
        List<Map<Integer, Integer>> zones = new ArrayList<>();
        Map<Integer, Integer> global = null;
        for (int bag = bagStart; bag < bagEnd; bag++) {
            int genStart = u16(buffer, bagTable[0] + bag * BAG_SIZE);
            int genEnd = u16(buffer, bagTable[0] + (bag + 1) * BAG_SIZE);
            Map<Integer, Integer> generators = new HashMap<>();
            for (int gen = genStart; gen < genEnd; gen++) {
                int at = genTable[0] + gen * GEN_RECORD_SIZE;
                int operator = u16(buffer, at);
                int amount = operator == GEN_KEY_RANGE || operator == GEN_VEL_RANGE
                    ? u16(buffer, at + 2) : buffer.getShort(at + 2);
                generators.put(operator, amount);
            }
            boolean terminal = generators.containsKey(GEN_INSTRUMENT) || generators.containsKey(GEN_SAMPLE_ID);
            if (!terminal && bag == bagStart) {
                global = generators;
                continue;
            }
            if (global != null) {
                Map<Integer, Integer> merged = new HashMap<>(global);
                merged.putAll(generators);
                generators = merged;
            }
            zones.add(generators);
        }
        return zones;
    }

    private static int[] range(Integer instrument, Integer preset) {
        int minKey = 0;
        int maxKey = 127;
        for (Integer packed : new Integer[] {instrument, preset}) {
            if (packed != null) {
                minKey = Math.max(minKey, packed & 0xFF);
                maxKey = Math.min(maxKey, (packed >> 8) & 0xFF);
            }
        }
        return new int[] {minKey, maxKey};
    }

    private static float seconds(int timecents) {
        return (float) Math.min(Math.pow(2.0, (double) timecents / TIMECENTS_PER_OCTAVE), MAX_ENVELOPE_SECONDS);
    }

    private static int u16(ByteBuffer buffer, int at) {
        return buffer.getShort(at) & 0xFFFF;
    }

    private static String tag(ByteBuffer buffer, int at) {
        byte[] id = new byte[4];
        buffer.get(at, id);
        return new String(id, StandardCharsets.US_ASCII);
    }

    private static String text(ByteBuffer buffer, int at, int length) {
        byte[] raw = new byte[length];
        buffer.get(at, raw);
        int end = 0;
        while (end < length && raw[end] != 0) {
            end++;
        }
        return new String(raw, 0, end, StandardCharsets.US_ASCII);
    }
}
