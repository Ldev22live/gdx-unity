package io.github.ldev22live.gdxunity.engine.audio;

import com.badlogic.gdx.utils.JsonValue;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Settings of the retro sound-effect generator {@link SfxrGenerator}, in the style of sfxr/bfxr. Every value is
 * normalised: 0..1, or -1..1 for the ones that can go both ways. Factory sounds come from {@link #preset}, and
 * {@link #randomized} and {@link #mutated} explore around them.
 */
public final class SfxrParams {
    /** Shape of the main oscillator. */
    public enum Shape { SQUARE, SAW, SINE, NOISE }

    /** Names of the factory sounds, in menu order. */
    private static final List<String> PRESETS = List.of("Coin", "Laser", "Explosion", "Powerup", "Hurt", "Jump", "Blip");
    /** Number of numeric parameters; used when mutating. */
    private static final int MUTABLE_COUNT = 21;

    public Shape shape = Shape.SQUARE;
    /** Start frequency, 0..1. */
    public float baseFreq = 0.3f;
    /** Lowest frequency the slide may reach before the sound stops, 0..1. */
    public float minFreq;
    /** Frequency slide, -1..1, and its change over time. */
    public float slide, deltaSlide;
    /** Square duty cycle 0..1 and its sweep -1..1. */
    public float duty, dutySweep;
    /** Vibrato depth and speed, 0..1. */
    public float vibratoDepth, vibratoSpeed;
    /** Envelope in 0..1 (squared into time), and the extra loudness at the start of the sustain. */
    public float attack, sustain = 0.3f, decay = 0.4f, punch;
    /** Low-pass cutoff 0..1 (1 is off), its sweep -1..1, and resonance 0..1. */
    public float lowPassCutoff = 1f, lowPassSweep, lowPassResonance;
    /** High-pass cutoff 0..1 and its sweep -1..1. */
    public float highPassCutoff, highPassSweep;
    /** Phaser offset and sweep, -1..1. */
    public float phaserOffset, phaserSweep;
    /** How often the sound restarts its pitch (0 never), 0..1. */
    public float repeatSpeed;
    /** Pitch jump partway through, -1..1, and how soon it happens, 0..1. */
    public float arpeggioChange, arpeggioSpeed;
    /** Output level, 0..1. */
    public float volume = 0.5f;

    /** The factory sound names. */
    public static List<String> presetNames() {
        return PRESETS;
    }

    /** A copy to edit. */
    public SfxrParams copy() {
        SfxrParams c = new SfxrParams();
        c.copyFrom(this);
        return c;
    }

    /** Takes every setting from {@code o}. */
    public void copyFrom(SfxrParams o) {
        Objects.requireNonNull(o, "o cannot be null");
        shape = o.shape;
        baseFreq = o.baseFreq;
        minFreq = o.minFreq;
        slide = o.slide;
        deltaSlide = o.deltaSlide;
        duty = o.duty;
        dutySweep = o.dutySweep;
        vibratoDepth = o.vibratoDepth;
        vibratoSpeed = o.vibratoSpeed;
        attack = o.attack;
        sustain = o.sustain;
        decay = o.decay;
        punch = o.punch;
        lowPassCutoff = o.lowPassCutoff;
        lowPassSweep = o.lowPassSweep;
        lowPassResonance = o.lowPassResonance;
        highPassCutoff = o.highPassCutoff;
        highPassSweep = o.highPassSweep;
        phaserOffset = o.phaserOffset;
        phaserSweep = o.phaserSweep;
        repeatSpeed = o.repeatSpeed;
        arpeggioChange = o.arpeggioChange;
        arpeggioSpeed = o.arpeggioSpeed;
        volume = o.volume;
    }

    /** A factory sound by name. */
    public static SfxrParams preset(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        SfxrParams p = new SfxrParams();
        switch (name) {
            case "Coin" -> {
                p.baseFreq = 0.4f;
                p.attack = 0f;
                p.sustain = 0.02f;
                p.decay = 0.3f;
                p.punch = 0.4f;
                p.arpeggioChange = 0.35f;
                p.arpeggioSpeed = 0.6f;
            }
            case "Laser" -> {
                p.shape = Shape.SAW;
                p.baseFreq = 0.7f;
                p.minFreq = 0.15f;
                p.slide = -0.35f;
                p.sustain = 0.1f;
                p.decay = 0.35f;
                p.duty = 0.3f;
            }
            case "Explosion" -> {
                p.shape = Shape.NOISE;
                p.baseFreq = 0.1f;
                p.slide = -0.15f;
                p.deltaSlide = 0.1f;
                p.sustain = 0.35f;
                p.decay = 0.6f;
                p.punch = 0.6f;
                p.lowPassCutoff = 0.5f;
            }
            case "Powerup" -> {
                p.shape = Shape.SQUARE;
                p.baseFreq = 0.25f;
                p.slide = 0.3f;
                p.sustain = 0.25f;
                p.decay = 0.4f;
                p.vibratoDepth = 0.3f;
                p.vibratoSpeed = 0.5f;
            }
            case "Hurt" -> {
                p.shape = Shape.SAW;
                p.baseFreq = 0.35f;
                p.slide = -0.25f;
                p.sustain = 0.1f;
                p.decay = 0.3f;
                p.punch = 0.5f;
                p.lowPassCutoff = 0.7f;
            }
            case "Jump" -> {
                p.baseFreq = 0.3f;
                p.slide = 0.25f;
                p.sustain = 0.12f;
                p.decay = 0.25f;
                p.duty = 0.4f;
            }
            case "Blip" -> {
                p.baseFreq = 0.45f;
                p.sustain = 0.05f;
                p.decay = 0.15f;
                p.highPassCutoff = 0.1f;
            }
            default -> throw new IllegalArgumentException("Unknown sfxr preset: " + name);
        }
        return p;
    }

    /** A random sound from a seed: the same seed always gives the same sound. */
    public static SfxrParams randomized(long seed) {
        Random random = new Random(seed);
        SfxrParams p = new SfxrParams();
        p.shape = Shape.values()[random.nextInt(Shape.values().length)];
        p.baseFreq = 0.1f + random.nextFloat() * 0.6f;
        p.slide = (random.nextFloat() - 0.5f) * 0.8f;
        p.duty = random.nextFloat();
        p.dutySweep = (random.nextFloat() - 0.5f) * 0.4f;
        p.sustain = 0.05f + random.nextFloat() * 0.3f;
        p.decay = 0.1f + random.nextFloat() * 0.5f;
        p.punch = random.nextFloat() * 0.5f;
        if (random.nextBoolean()) {
            p.vibratoDepth = random.nextFloat() * 0.5f;
            p.vibratoSpeed = random.nextFloat() * 0.6f;
        }
        if (random.nextBoolean()) {
            p.arpeggioChange = (random.nextFloat() - 0.5f) * 0.8f;
            p.arpeggioSpeed = 0.3f + random.nextFloat() * 0.5f;
        }
        if (random.nextInt(3) == 0) {
            p.lowPassCutoff = 0.3f + random.nextFloat() * 0.6f;
        }
        return p;
    }

    /**
     * A variation of this sound: every number moves by up to {@code amount} (0..1) of its range.
     * The same seed gives the same variation.
     */
    public SfxrParams mutated(long seed, float amount) {
        Random random = new Random(seed);
        SfxrParams p = copy();
        float[] values = p.toArray();
        for (int i = 0; i < values.length; i++) {
            if (random.nextInt(2) == 0) {
                values[i] += (random.nextFloat() * 2f - 1f) * amount * 0.5f;
            }
        }
        p.fromArray(values);
        return p;
    }

    private float[] toArray() {
        return new float[] {baseFreq, minFreq, slide, deltaSlide, duty, dutySweep, vibratoDepth, vibratoSpeed,
            attack, sustain, decay, punch, lowPassCutoff, lowPassSweep, lowPassResonance, highPassCutoff,
            highPassSweep, phaserOffset, phaserSweep, repeatSpeed, arpeggioChange};
    }

    private void fromArray(float[] v) {
        if (v.length != MUTABLE_COUNT) {
            throw new IllegalArgumentException("Expected " + MUTABLE_COUNT + " values, got " + v.length);
        }
        baseFreq = clamp(v[0], 0f, 1f);
        minFreq = clamp(v[1], 0f, 1f);
        slide = clamp(v[2], -1f, 1f);
        deltaSlide = clamp(v[3], -1f, 1f);
        duty = clamp(v[4], 0f, 1f);
        dutySweep = clamp(v[5], -1f, 1f);
        vibratoDepth = clamp(v[6], 0f, 1f);
        vibratoSpeed = clamp(v[7], 0f, 1f);
        attack = clamp(v[8], 0f, 1f);
        sustain = clamp(v[9], 0f, 1f);
        decay = clamp(v[10], 0f, 1f);
        punch = clamp(v[11], 0f, 1f);
        lowPassCutoff = clamp(v[12], 0f, 1f);
        lowPassSweep = clamp(v[13], -1f, 1f);
        lowPassResonance = clamp(v[14], 0f, 1f);
        highPassCutoff = clamp(v[15], 0f, 1f);
        highPassSweep = clamp(v[16], -1f, 1f);
        phaserOffset = clamp(v[17], -1f, 1f);
        phaserSweep = clamp(v[18], -1f, 1f);
        repeatSpeed = clamp(v[19], 0f, 1f);
        arpeggioChange = clamp(v[20], -1f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    /** JSON object text, for embedding in a song or an audio asset. */
    public String toJson() {
        float[] v = toArray();
        StringBuilder out = new StringBuilder("{\"shape\":\"").append(shape).append("\",\"volume\":")
            .append(String.format(Locale.ROOT, "%s", volume)).append(",\"arpeggioSpeed\":")
            .append(String.format(Locale.ROOT, "%s", arpeggioSpeed)).append(",\"values\":[");
        for (int i = 0; i < v.length; i++) {
            out.append(i > 0 ? "," : "").append(String.format(Locale.ROOT, "%s", v[i]));
        }
        return out.append("]}").toString();
    }

    /** Reads what {@link #toJson()} wrote. */
    public static SfxrParams fromJson(JsonValue json) {
        Objects.requireNonNull(json, "json cannot be null");
        SfxrParams p = new SfxrParams();
        p.shape = Shape.valueOf(json.getString("shape", p.shape.name()));
        p.volume = json.getFloat("volume", p.volume);
        p.arpeggioSpeed = json.getFloat("arpeggioSpeed", p.arpeggioSpeed);
        JsonValue values = json.get("values");
        if (values != null) {
            if (values.size != MUTABLE_COUNT) {
                throw new IllegalArgumentException("sfxr values must have " + MUTABLE_COUNT + " numbers, got " + values.size);
            }
            float[] v = new float[MUTABLE_COUNT];
            for (int i = 0; i < v.length; i++) {
                v[i] = values.get(i).asFloat();
            }
            p.fromArray(v);
        }
        return p;
    }
}
