package io.github.ldev22live.gdxunity.engine.audio;

import com.badlogic.gdx.utils.JsonValue;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The settings of the FM synthesizer (TD-075): a sine <em>carrier</em> whose phase is bent by a sine <em>modulator</em> running at
 * {@link #ratio} times the note's frequency. {@link #index} is how far the modulator bends the carrier at the start of a note; it
 * then falls towards {@link #indexSustain} of that, so the tone starts bright and settles, as a struck bell or tine does. Plain fields so
 * the editor can bind knobs to them; {@link FmSynthesizer} reads them while it renders.
 */
public final class FmPatch {
    /** Names of the factory patches, in menu order. */
    private static final List<String> PRESETS = List.of("Bell", "FM Piano", "Glass", "Metal Pad", "FM Bass");

    /** Modulator frequency as a multiple of the note's; whole numbers sound harmonic, others bell-like or metallic. */
    public float ratio = 2f;
    /** Peak modulation at the start of a note, in radians; 0 is a plain sine. */
    public float index = 2f;
    /** Share of {@link #index} left once the modulation has fallen, 0..1. */
    public float indexSustain = 0.2f;
    /** Time constant of the modulation's fall, in seconds. */
    public float indexDecay = 0.5f;
    /** Envelope times in seconds and sustain level 0..1. */
    public float attack = 0.005f, decay = 0.6f, sustain = 0.3f, release = 0.4f;
    /** Output level, 0..1. */
    public float volume = 0.6f;

    /** A copy that can be edited without touching this patch. */
    public FmPatch copy() {
        FmPatch c = new FmPatch();
        c.copyFrom(this);
        return c;
    }

    /** Takes every setting from {@code other}. */
    public void copyFrom(FmPatch other) {
        Objects.requireNonNull(other, "other cannot be null");
        ratio = other.ratio;
        index = other.index;
        indexSustain = other.indexSustain;
        indexDecay = other.indexDecay;
        attack = other.attack;
        decay = other.decay;
        sustain = other.sustain;
        release = other.release;
        volume = other.volume;
    }

    /** The factory patch names. */
    public static List<String> presetNames() {
        return PRESETS;
    }

    /** A factory patch by name. */
    public static FmPatch preset(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        FmPatch p = new FmPatch();
        switch (name) {
            case "Bell" -> {
                p.ratio = 3.5f;
                p.index = 4f;
                p.indexSustain = 0.1f;
                p.indexDecay = 1.2f;
                p.attack = 0.001f;
                p.decay = 2.5f;
                p.sustain = 0f;
                p.release = 1f;
            }
            case "FM Piano" -> {
                p.ratio = 1f;
                p.index = 2.2f;
                p.indexSustain = 0.15f;
                p.indexDecay = 0.4f;
                p.attack = 0.002f;
                p.decay = 1.4f;
                p.sustain = 0.1f;
                p.release = 0.3f;
            }
            case "Glass" -> {
                p.ratio = 5f;
                p.index = 2f;
                p.indexSustain = 0.5f;
                p.indexDecay = 0.8f;
                p.attack = 0.02f;
                p.decay = 1f;
                p.sustain = 0.5f;
                p.release = 0.8f;
                p.volume = 0.5f;
            }
            case "Metal Pad" -> {
                p.ratio = 1.41f;
                p.index = 3f;
                p.indexSustain = 0.6f;
                p.indexDecay = 2f;
                p.attack = 0.4f;
                p.decay = 0.6f;
                p.sustain = 0.8f;
                p.release = 1.2f;
                p.volume = 0.5f;
            }
            case "FM Bass" -> {
                p.ratio = 1f;
                p.index = 3f;
                p.indexSustain = 0.3f;
                p.indexDecay = 0.15f;
                p.attack = 0.001f;
                p.decay = 0.2f;
                p.sustain = 0.8f;
                p.release = 0.1f;
                p.volume = 0.7f;
            }
            default -> throw new IllegalArgumentException("Unknown FM preset: " + name);
        }
        return p;
    }

    /** Writes the settings as JSON object text, for embedding in a song file. */
    public String toJson() {
        return String.format(Locale.ROOT,
            "{\"ratio\":%s,\"index\":%s,\"indexSustain\":%s,\"indexDecay\":%s,\"attack\":%s,\"decay\":%s,\"sustain\":%s,"
                + "\"release\":%s,\"volume\":%s}",
            ratio, index, indexSustain, indexDecay, attack, decay, sustain, release, volume);
    }

    /** Reads settings written by {@link #toJson()}; a missing field keeps its default. */
    public static FmPatch fromJson(JsonValue json) {
        Objects.requireNonNull(json, "json cannot be null");
        FmPatch p = new FmPatch();
        p.ratio = json.getFloat("ratio", p.ratio);
        p.index = json.getFloat("index", p.index);
        p.indexSustain = json.getFloat("indexSustain", p.indexSustain);
        p.indexDecay = json.getFloat("indexDecay", p.indexDecay);
        p.attack = json.getFloat("attack", p.attack);
        p.decay = json.getFloat("decay", p.decay);
        p.sustain = json.getFloat("sustain", p.sustain);
        p.release = json.getFloat("release", p.release);
        p.volume = json.getFloat("volume", p.volume);
        return p;
    }
}
