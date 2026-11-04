package io.github.ldev22live.gdxunity.engine.audio;

import java.util.List;

/**
 * A stereo filter. Parameters: {@code mode} (0 low-pass, 1 high-pass, 2 band-pass), {@code cutoff} (Hz) and
 * {@code resonance} (Q, 0.5..20).
 */
public final class FilterEffect implements Effect {
    private static final List<String> NAMES = List.of("mode", "cutoff", "resonance");
    private static final BiquadFilter.Type[] MODES = BiquadFilter.Type.values();
    private static final float MAX_CUTOFF = 20000f;

    private final BiquadFilter filterLeft;
    private final BiquadFilter filterRight;
    private int mode;
    private float cutoff = 4000f;
    private float resonance = 0.7f;

    public FilterEffect(float sampleRate) {
        filterLeft = new BiquadFilter(sampleRate);
        filterRight = new BiquadFilter(sampleRate);
        apply();
    }

    @Override
    public String getType() {
        return "filter";
    }

    @Override
    public List<String> getParameterNames() {
        return NAMES;
    }

    @Override
    public float getParameter(String name) {
        return switch (name) {
            case "mode" -> mode;
            case "cutoff" -> cutoff;
            case "resonance" -> resonance;
            default -> throw new IllegalArgumentException("Unknown filter parameter: " + name);
        };
    }

    @Override
    public void setParameter(String name, float value) {
        switch (name) {
            case "mode" -> mode = Math.min(Math.max(Math.round(value), 0), MODES.length - 1);
            case "cutoff" -> cutoff = Math.min(Math.max(value, BiquadFilter.MIN_CUTOFF), MAX_CUTOFF);
            case "resonance" -> resonance = Math.min(Math.max(value, BiquadFilter.MIN_Q), BiquadFilter.MAX_Q);
            default -> throw new IllegalArgumentException("Unknown filter parameter: " + name);
        }
        apply();
    }

    private void apply() {
        filterLeft.set(MODES[mode], cutoff, resonance);
        filterRight.set(MODES[mode], cutoff, resonance);
    }

    @Override
    public void process(float[] left, float[] right, int offset, int frames) {
        for (int i = 0; i < frames; i++) {
            left[offset + i] = filterLeft.process(left[offset + i]);
            right[offset + i] = filterRight.process(right[offset + i]);
        }
    }

    @Override
    public void reset() {
        filterLeft.reset();
        filterRight.reset();
    }
}
