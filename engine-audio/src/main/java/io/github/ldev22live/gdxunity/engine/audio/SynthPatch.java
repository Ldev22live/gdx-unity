package io.github.ldev22live.gdxunity.engine.audio;

import com.badlogic.gdx.utils.JsonValue;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The settings of the built-in synthesizer: two oscillators, an amplitude envelope, a resonant filter with its own
 * envelope amount and one LFO. Plain fields so the editor can bind knobs to them; {@link Synthesizer} reads them
 * while it renders, so a change is heard on the next note.
 */
public final class SynthPatch {
    /** What the LFO moves. */
    public enum LfoTarget { NONE, PITCH, CUTOFF, VOLUME }

    /** Names of the factory patches, in menu order. */
    private static final List<String> PRESETS = List.of("Saw Lead", "Square Chip", "Sub Bass", "Soft Pad", "Pluck");

    /** First oscillator. */
    public Waveform osc1 = Waveform.SAW;
    /** Second oscillator. */
    public Waveform osc2 = Waveform.SAW;
    /** Second oscillator's offset from the first, in cents (100 = a semitone). */
    public float osc2Detune = 7f;
    /** Second oscillator's offset in octaves. */
    public int osc2Octave = 0;
    /** Share of the second oscillator in the mix, 0 (silent) .. 1 (only osc 2). */
    public float osc2Mix = 0.5f;
    /** Square wave width, 0.05..0.95. */
    public float pulseWidth = 0.5f;
    /** Envelope times in seconds and sustain level 0..1. */
    public float attack = 0.01f, decay = 0.2f, sustain = 0.7f, release = 0.25f;
    /** Filter type, cutoff in Hz and resonance (Q). */
    public BiquadFilter.Type filterType = BiquadFilter.Type.LOW_PASS;
    public float cutoff = 8000f;
    public float resonance = 0.7f;
    /** How far the envelope opens the filter, in octaves above the cutoff at full level. */
    public float filterEnvOctaves = 0f;
    /** LFO target, rate in Hz and depth (semitones for pitch, octaves for cutoff, 0..1 for volume). */
    public LfoTarget lfoTarget = LfoTarget.NONE;
    public float lfoRate = 5f;
    public float lfoDepth = 0f;
    /** Output level, 0..1. */
    public float volume = 0.6f;

    /** A copy that can be edited without touching this patch. */
    public SynthPatch copy() {
        SynthPatch c = new SynthPatch();
        c.copyFrom(this);
        return c;
    }

    /** Takes every setting from {@code other}. */
    public void copyFrom(SynthPatch other) {
        Objects.requireNonNull(other, "other cannot be null");
        osc1 = other.osc1;
        osc2 = other.osc2;
        osc2Detune = other.osc2Detune;
        osc2Octave = other.osc2Octave;
        osc2Mix = other.osc2Mix;
        pulseWidth = other.pulseWidth;
        attack = other.attack;
        decay = other.decay;
        sustain = other.sustain;
        release = other.release;
        filterType = other.filterType;
        cutoff = other.cutoff;
        resonance = other.resonance;
        filterEnvOctaves = other.filterEnvOctaves;
        lfoTarget = other.lfoTarget;
        lfoRate = other.lfoRate;
        lfoDepth = other.lfoDepth;
        volume = other.volume;
    }

    /** The factory patch names. */
    public static List<String> presetNames() {
        return PRESETS;
    }

    /** A factory patch by name. */
    public static SynthPatch preset(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        SynthPatch p = new SynthPatch();
        switch (name) {
            case "Saw Lead" -> {
                p.attack = 0.005f;
                p.release = 0.2f;
                p.cutoff = 6000f;
                p.lfoTarget = LfoTarget.PITCH;
                p.lfoRate = 5.5f;
                p.lfoDepth = 0.12f;
            }
            case "Square Chip" -> {
                p.osc1 = Waveform.SQUARE;
                p.osc2 = Waveform.SQUARE;
                p.pulseWidth = 0.25f;
                p.osc2Mix = 0f;
                p.attack = 0.002f;
                p.decay = 0.08f;
                p.sustain = 0.6f;
                p.release = 0.08f;
                p.cutoff = 12000f;
            }
            case "Sub Bass" -> {
                p.osc1 = Waveform.SINE;
                p.osc2 = Waveform.SQUARE;
                p.osc2Octave = -1;
                p.osc2Mix = 0.35f;
                p.osc2Detune = 0f;
                p.attack = 0.004f;
                p.sustain = 0.9f;
                p.release = 0.12f;
                p.cutoff = 900f;
                p.volume = 0.8f;
            }
            case "Soft Pad" -> {
                p.osc1 = Waveform.SAW;
                p.osc2 = Waveform.TRIANGLE;
                p.osc2Detune = 12f;
                p.attack = 0.6f;
                p.decay = 0.5f;
                p.sustain = 0.8f;
                p.release = 1.2f;
                p.cutoff = 2500f;
                p.lfoTarget = LfoTarget.CUTOFF;
                p.lfoRate = 0.3f;
                p.lfoDepth = 0.6f;
                p.volume = 0.5f;
            }
            case "Pluck" -> {
                p.osc1 = Waveform.TRIANGLE;
                p.osc2 = Waveform.SAW;
                p.osc2Mix = 0.3f;
                p.attack = 0.001f;
                p.decay = 0.25f;
                p.sustain = 0f;
                p.release = 0.15f;
                p.cutoff = 1200f;
                p.filterEnvOctaves = 3f;
                p.resonance = 2f;
            }
            default -> throw new IllegalArgumentException("Unknown synth preset: " + name);
        }
        return p;
    }

    /** Writes the settings as JSON object text, for embedding in a song file. */
    public String toJson() {
        return String.format(Locale.ROOT,
            "{\"osc1\":\"%s\",\"osc2\":\"%s\",\"osc2Detune\":%s,\"osc2Octave\":%d,\"osc2Mix\":%s,\"pulseWidth\":%s,"
                + "\"attack\":%s,\"decay\":%s,\"sustain\":%s,\"release\":%s,\"filterType\":\"%s\",\"cutoff\":%s,"
                + "\"resonance\":%s,\"filterEnvOctaves\":%s,\"lfoTarget\":\"%s\",\"lfoRate\":%s,\"lfoDepth\":%s,"
                + "\"volume\":%s}",
            osc1, osc2, osc2Detune, osc2Octave, osc2Mix, pulseWidth, attack, decay, sustain, release, filterType,
            cutoff, resonance, filterEnvOctaves, lfoTarget, lfoRate, lfoDepth, volume);
    }

    /** Reads settings written by {@link #toJson()}; a missing field keeps its default. */
    public static SynthPatch fromJson(JsonValue json) {
        Objects.requireNonNull(json, "json cannot be null");
        SynthPatch p = new SynthPatch();
        p.osc1 = Waveform.valueOf(json.getString("osc1", p.osc1.name()));
        p.osc2 = Waveform.valueOf(json.getString("osc2", p.osc2.name()));
        p.osc2Detune = json.getFloat("osc2Detune", p.osc2Detune);
        p.osc2Octave = json.getInt("osc2Octave", p.osc2Octave);
        p.osc2Mix = json.getFloat("osc2Mix", p.osc2Mix);
        p.pulseWidth = json.getFloat("pulseWidth", p.pulseWidth);
        p.attack = json.getFloat("attack", p.attack);
        p.decay = json.getFloat("decay", p.decay);
        p.sustain = json.getFloat("sustain", p.sustain);
        p.release = json.getFloat("release", p.release);
        p.filterType = BiquadFilter.Type.valueOf(json.getString("filterType", p.filterType.name()));
        p.cutoff = json.getFloat("cutoff", p.cutoff);
        p.resonance = json.getFloat("resonance", p.resonance);
        p.filterEnvOctaves = json.getFloat("filterEnvOctaves", p.filterEnvOctaves);
        p.lfoTarget = LfoTarget.valueOf(json.getString("lfoTarget", p.lfoTarget.name()));
        p.lfoRate = json.getFloat("lfoRate", p.lfoRate);
        p.lfoDepth = json.getFloat("lfoDepth", p.lfoDepth);
        p.volume = json.getFloat("volume", p.volume);
        return p;
    }
}
