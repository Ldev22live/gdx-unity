package io.github.ldev22live.gdxunity.engine.audio.song;

import io.github.ldev22live.gdxunity.engine.audio.SynthPatch;
import io.github.ldev22live.gdxunity.engine.audio.Waveform;

import java.util.List;

/** Small songs for the tests. */
final class SongTestSupport {
    /** Frames in a tick at 120 bpm: 44100 * 60 / (120 * 96). */
    static final double FRAMES_PER_TICK_120 = 44100.0 * 60.0 / (120.0 * Song.TICKS_PER_BEAT);

    private SongTestSupport() { }

    /** A sine that starts at once and sustains, so note timing is easy to see. */
    static SynthPatch cleanSine() {
        SynthPatch patch = new SynthPatch();
        patch.osc1 = Waveform.SINE;
        patch.osc2Mix = 0f;
        patch.cutoff = 20000f;
        patch.attack = 0.001f;
        patch.sustain = 1f;
        patch.release = 0.02f;
        patch.volume = 1f;
        return patch;
    }

    /** One synth channel on strip 0 and one one-bar pattern with a single note at {@code tick}. */
    static Song songWithNote(int tick, int length) {
        Song song = new Song("test");
        Channel channel = song.addChannel("Lead", InstrumentSpec.synth(cleanSine()), 0);
        Pattern pattern = song.addPattern("P", 1);
        pattern.setNotes(channel.getId(), List.of(new Note(tick, length, 69, 1f)));
        song.getMixer().getStrip(0).setVolume(1f);
        return song;
    }

    static float[] render(SongPlayer player, int frames) {
        float[] left = new float[frames];
        float[] right = new float[frames];
        player.render(left, right, frames);
        return left;
    }

    /** Index of the first frame whose magnitude exceeds {@code threshold}, or -1. */
    static int firstSound(float[] data, float threshold) {
        for (int i = 0; i < data.length; i++) {
            if (Math.abs(data[i]) > threshold) {
                return i;
            }
        }
        return -1;
    }

    static float peak(float[] data, int from, int to) {
        float peak = 0f;
        for (int i = from; i < Math.min(to, data.length); i++) {
            peak = Math.max(peak, Math.abs(data[i]));
        }
        return peak;
    }
}
