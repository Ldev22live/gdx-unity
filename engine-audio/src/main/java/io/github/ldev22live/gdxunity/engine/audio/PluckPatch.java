package io.github.ldev22live.gdxunity.engine.audio;

import com.badlogic.gdx.utils.JsonValue;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The settings of the plucked-string instrument (TD-075), a Karplus-Strong string: a burst of noise circulates in a delay line the
 * length of one pitch period, and each trip through a small low-pass filter takes a little of the high end and the energy away.
 * Plain fields so the editor can bind knobs to them; {@link PluckedString} reads them while it renders.
 */
public final class PluckPatch {
    /** Names of the factory patches, in menu order. */
    private static final List<String> PRESETS = List.of("Nylon Guitar", "Harp", "Pizzicato");

    /** Seconds for the string to fall by 60 dB while the key is held. */
    public float decay = 2f;
    /** How much high end each trip loses, 0 (rings brightly) .. 1 (dies dull and fast). */
    public float damping = 0.5f;
    /** How bright the first pluck is, 0 (a soft thump) .. 1 (full noise, a hard pick). */
    public float brightness = 0.7f;
    /** Seconds the string takes to fall by 60 dB once the key is let go. */
    public float release = 0.15f;
    /** Output level, 0..1. */
    public float volume = 0.6f;

    /** A copy that can be edited without touching this patch. */
    public PluckPatch copy() {
        PluckPatch c = new PluckPatch();
        c.copyFrom(this);
        return c;
    }

    /** Takes every setting from {@code other}. */
    public void copyFrom(PluckPatch other) {
        Objects.requireNonNull(other, "other cannot be null");
        decay = other.decay;
        damping = other.damping;
        brightness = other.brightness;
        release = other.release;
        volume = other.volume;
    }

    /** The factory patch names. */
    public static List<String> presetNames() {
        return PRESETS;
    }

    /** A factory patch by name. */
    public static PluckPatch preset(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        PluckPatch p = new PluckPatch();
        switch (name) {
            case "Nylon Guitar" -> {
                p.decay = 2.5f;
                p.damping = 0.5f;
                p.brightness = 0.6f;
                p.release = 0.15f;
            }
            case "Harp" -> {
                p.decay = 4f;
                p.damping = 0.25f;
                p.brightness = 0.8f;
                p.release = 0.3f;
            }
            case "Pizzicato" -> {
                p.decay = 0.6f;
                p.damping = 0.7f;
                p.brightness = 0.5f;
                p.release = 0.08f;
            }
            default -> throw new IllegalArgumentException("Unknown plucked-string preset: " + name);
        }
        return p;
    }

    /** Writes the settings as JSON object text, for embedding in a song file. */
    public String toJson() {
        return String.format(Locale.ROOT,
            "{\"decay\":%s,\"damping\":%s,\"brightness\":%s,\"release\":%s,\"volume\":%s}",
            decay, damping, brightness, release, volume);
    }

    /** Reads settings written by {@link #toJson()}; a missing field keeps its default. */
    public static PluckPatch fromJson(JsonValue json) {
        Objects.requireNonNull(json, "json cannot be null");
        PluckPatch p = new PluckPatch();
        p.decay = json.getFloat("decay", p.decay);
        p.damping = json.getFloat("damping", p.damping);
        p.brightness = json.getFloat("brightness", p.brightness);
        p.release = json.getFloat("release", p.release);
        p.volume = json.getFloat("volume", p.volume);
        return p;
    }
}
