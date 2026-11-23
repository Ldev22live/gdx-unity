package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Arrays;
import java.util.List;

/**
 * A Schroeder/Freeverb style reverb: eight damped comb filters feeding four all-pass filters per side.
 * Parameters: {@code room} (0..1, decay length), {@code damping} (0..1, how dull the tail gets), {@code mix} (0..1).
 */
public final class ReverbEffect implements Effect {
    private static final List<String> NAMES = List.of("room", "damping", "mix");
    /** Delay lengths in samples at 44.1 kHz; the right side is offset so the two sides differ. */
    private static final int[] COMB_TUNING = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    private static final int[] ALLPASS_TUNING = {556, 441, 341, 225};
    private static final int STEREO_SPREAD = 23;
    private static final float REFERENCE_RATE = 44100f;
    private static final float ALLPASS_FEEDBACK = 0.5f;
    /** Input is scaled down because eight combs add up. */
    private static final float INPUT_GAIN = 0.015f;
    private static final float ROOM_SCALE = 0.28f;
    private static final float ROOM_OFFSET = 0.7f;
    private static final float DAMP_SCALE = 0.4f;
    private static final float WET_GAIN = 3f;

    private final Comb[] combsLeft = new Comb[COMB_TUNING.length];
    private final Comb[] combsRight = new Comb[COMB_TUNING.length];
    private final Allpass[] allpassLeft = new Allpass[ALLPASS_TUNING.length];
    private final Allpass[] allpassRight = new Allpass[ALLPASS_TUNING.length];
    private float room = 0.5f;
    private float damping = 0.5f;
    private float mix = 0.25f;

    public ReverbEffect(float sampleRate) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        float scale = sampleRate / REFERENCE_RATE;
        for (int i = 0; i < COMB_TUNING.length; i++) {
            combsLeft[i] = new Comb(Math.round(COMB_TUNING[i] * scale));
            combsRight[i] = new Comb(Math.round((COMB_TUNING[i] + STEREO_SPREAD) * scale));
        }
        for (int i = 0; i < ALLPASS_TUNING.length; i++) {
            allpassLeft[i] = new Allpass(Math.round(ALLPASS_TUNING[i] * scale));
            allpassRight[i] = new Allpass(Math.round((ALLPASS_TUNING[i] + STEREO_SPREAD) * scale));
        }
        applyParameters();
    }

    @Override
    public String getType() {
        return "reverb";
    }

    @Override
    public List<String> getParameterNames() {
        return NAMES;
    }

    @Override
    public float getParameter(String name) {
        return switch (name) {
            case "room" -> room;
            case "damping" -> damping;
            case "mix" -> mix;
            default -> throw new IllegalArgumentException("Unknown reverb parameter: " + name);
        };
    }

    @Override
    public void setParameter(String name, float value) {
        float clamped = Math.min(Math.max(value, 0f), 1f);
        switch (name) {
            case "room" -> room = clamped;
            case "damping" -> damping = clamped;
            case "mix" -> mix = clamped;
            default -> throw new IllegalArgumentException("Unknown reverb parameter: " + name);
        }
        applyParameters();
    }

    private void applyParameters() {
        float feedback = room * ROOM_SCALE + ROOM_OFFSET;
        float damp = damping * DAMP_SCALE;
        for (int i = 0; i < combsLeft.length; i++) {
            combsLeft[i].feedback = feedback;
            combsLeft[i].damp = damp;
            combsRight[i].feedback = feedback;
            combsRight[i].damp = damp;
        }
    }

    @Override
    public void process(float[] left, float[] right, int offset, int frames) {
        for (int i = 0; i < frames; i++) {
            float input = (left[offset + i] + right[offset + i]) * INPUT_GAIN;
            float outLeft = 0f;
            float outRight = 0f;
            for (int c = 0; c < combsLeft.length; c++) {
                outLeft += combsLeft[c].process(input);
                outRight += combsRight[c].process(input);
            }
            for (int a = 0; a < allpassLeft.length; a++) {
                outLeft = allpassLeft[a].process(outLeft);
                outRight = allpassRight[a].process(outRight);
            }
            left[offset + i] += outLeft * WET_GAIN * mix;
            right[offset + i] += outRight * WET_GAIN * mix;
        }
    }

    @Override
    public void reset() {
        for (Comb comb : combsLeft) {
            comb.clear();
        }
        for (Comb comb : combsRight) {
            comb.clear();
        }
        for (Allpass allpass : allpassLeft) {
            allpass.clear();
        }
        for (Allpass allpass : allpassRight) {
            allpass.clear();
        }
    }

    private static final class Comb {
        final float[] buffer;
        int index;
        float store;
        float feedback;
        float damp;

        Comb(int size) {
            buffer = new float[Math.max(size, 1)];
        }

        float process(float input) {
            float output = buffer[index];
            store = output * (1f - damp) + store * damp;
            buffer[index] = input + store * feedback;
            index = (index + 1) % buffer.length;
            return output;
        }

        void clear() {
            Arrays.fill(buffer, 0f);
            store = 0f;
            index = 0;
        }
    }

    private static final class Allpass {
        final float[] buffer;
        int index;

        Allpass(int size) {
            buffer = new float[Math.max(size, 1)];
        }

        float process(float input) {
            float buffered = buffer[index];
            float output = -input + buffered;
            buffer[index] = input + buffered * ALLPASS_FEEDBACK;
            index = (index + 1) % buffer.length;
            return output;
        }

        void clear() {
            Arrays.fill(buffer, 0f);
            index = 0;
        }
    }
}
