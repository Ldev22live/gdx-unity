package io.github.ldev22live.gdxunity.engine.audio.song;

import io.github.ldev22live.gdxunity.engine.audio.DrumKit;
import io.github.ldev22live.gdxunity.engine.audio.Instrument;
import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.audio.SampleInstrument;
import io.github.ldev22live.gdxunity.engine.audio.SfxrGenerator;
import io.github.ldev22live.gdxunity.engine.audio.SoundFont;
import io.github.ldev22live.gdxunity.engine.audio.SoundFontInstrument;
import io.github.ldev22live.gdxunity.engine.audio.Synthesizer;
import io.github.ldev22live.gdxunity.engine.audio.WavIO;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Builds instruments from {@link InstrumentSpec}s, reading each sample or SoundFont file once. */
public final class InstrumentFactory {
    private final MusicAssets assets;
    private final float sampleRate;
    private final Map<String, SampleData> samples = new HashMap<>();
    private final Map<String, SoundFont> soundFonts = new HashMap<>();

    public InstrumentFactory(MusicAssets assets, float sampleRate) {
        this.assets = Objects.requireNonNull(assets, "assets cannot be null");
        this.sampleRate = sampleRate;
    }

    /** Forgets cached files, so changed files are read again. */
    public void clearCache() {
        samples.clear();
        soundFonts.clear();
    }

    /**
     * Builds the instrument.
     *
     * @throws IOException when a sample or SoundFont file cannot be read or understood
     */
    public Instrument create(InstrumentSpec spec) throws IOException {
        Objects.requireNonNull(spec, "spec cannot be null");
        switch (spec.type) {
            case SYNTH:
                return new Synthesizer(sampleRate, spec.patch);
            case DRUMS:
                return new DrumKit(sampleRate);
            case SAMPLE: {
                SampleData data = samples.get(spec.path);
                if (data == null) {
                    data = WavIO.read(assets.read(spec.path));
                    samples.put(spec.path, data);
                }
                return sampler(spec, data);
            }
            case SFXR:
                return sampler(spec, SfxrGenerator.generate(spec.sfxr));
            case SOUNDFONT: {
                SoundFont font = soundFonts.get(spec.path);
                if (font == null) {
                    font = SoundFont.read(assets.read(spec.path));
                    soundFonts.put(spec.path, font);
                }
                SoundFont.Preset preset = font.findPreset(spec.bank, spec.program);
                if (preset == null) {
                    throw new IOException("The SoundFont '" + spec.path + "' has no preset " + spec.bank + ":" + spec.program);
                }
                return new SoundFontInstrument(sampleRate, font, preset);
            }
            default:
                throw new IllegalArgumentException("Unknown instrument type " + spec.type);
        }
    }

    private SampleInstrument sampler(InstrumentSpec spec, SampleData data) {
        SampleInstrument instrument = new SampleInstrument(sampleRate, data);
        instrument.setRootNote(spec.rootNote);
        instrument.setOneShot(spec.oneShot);
        return instrument;
    }
}
