package io.github.ldev22live.gdxunity.engine.audio.song;

import io.github.ldev22live.gdxunity.engine.audio.SampleData;

import java.io.IOException;
import java.util.Objects;

/** Renders a song to audio without a sound device, for WAV export and for tests. */
public final class SongExporter {
    private static final int BLOCK = 1024;
    /** The tail after the last note stops when the output stays under this for {@link #SILENCE_SECONDS}. */
    private static final float SILENCE_LEVEL = 0.0005f;
    private static final float SILENCE_SECONDS = 0.25f;
    /** Longest tail rendered after the music ends, seconds. */
    public static final float MAX_TAIL_SECONDS = 8f;
    /** Longest export, seconds; guards against runaway lengths. */
    public static final float MAX_SECONDS = 20f * 60f;

    private SongExporter() { }

    /**
     * Renders the pattern or the playlist once (no looping), then the effect and release tail until silence.
     *
     * @param mode      what to render
     * @param patternId pattern for {@link Schedule.Mode#PATTERN}; ignored otherwise
     * @return stereo audio at {@link Song#SAMPLE_RATE}
     * @throws IOException when the song has nothing to render or a file it uses cannot be read
     */
    public static SampleData render(Song song, MusicAssets assets, Schedule.Mode mode, int patternId) throws IOException {
        Objects.requireNonNull(song, "song cannot be null");
        SongPlayer player = new SongPlayer(song, assets);
        try {
            if (!player.getErrors().isEmpty()) {
                throw new IOException("Cannot export: " + String.join("; ", player.getErrors()));
            }
            player.setPlayback(mode, patternId);
            player.setLoop(false);
            player.seek(0);
            player.play();
            int musicFrames = framesFor(song, mode, patternId);
            if (musicFrames <= 0) {
                throw new IOException("Nothing to export: the "
                    + (mode == Schedule.Mode.SONG ? "playlist is empty" : "pattern has no length"));
            }
            int maxFrames = (int) (MAX_SECONDS * Song.SAMPLE_RATE);
            int tailFrames = (int) (MAX_TAIL_SECONDS * Song.SAMPLE_RATE);
            int silentNeeded = (int) (SILENCE_SECONDS * Song.SAMPLE_RATE);
            float[] left = new float[Math.min(maxFrames, musicFrames + tailFrames + BLOCK)];
            float[] right = new float[left.length];
            float[] blockLeft = new float[BLOCK];
            float[] blockRight = new float[BLOCK];
            int written = 0;
            int silent = 0;
            while (written < left.length) {
                int frames = Math.min(BLOCK, left.length - written);
                player.render(blockLeft, blockRight, frames);
                float peak = 0f;
                for (int i = 0; i < frames; i++) {
                    left[written + i] = blockLeft[i];
                    right[written + i] = blockRight[i];
                    peak = Math.max(peak, Math.max(Math.abs(blockLeft[i]), Math.abs(blockRight[i])));
                }
                written += frames;
                silent = peak < SILENCE_LEVEL ? silent + frames : 0;
                if (!player.isPlaying() && silent >= silentNeeded) {
                    break;
                }
            }
            int end = Math.max(written - Math.min(silent, written), Math.min(musicFrames, written));
            float[] trimmedLeft = new float[end];
            float[] trimmedRight = new float[end];
            System.arraycopy(left, 0, trimmedLeft, 0, end);
            System.arraycopy(right, 0, trimmedRight, 0, end);
            return new SampleData(trimmedLeft, trimmedRight, (int) Song.SAMPLE_RATE);
        } finally {
            player.dispose();
        }
    }

    /** Frames the music itself lasts at the song's tempo, not counting the tail. */
    public static int framesFor(Song song, Schedule.Mode mode, int patternId) {
        int ticks = mode == Schedule.Mode.SONG
            ? song.getPlaylistLengthTicks()
            : (song.getPattern(patternId) == null ? 0 : song.getPattern(patternId).getLengthTicks());
        double framesPerTick = Song.SAMPLE_RATE * 60.0 / (song.getBpm() * Song.TICKS_PER_BEAT);
        return (int) Math.ceil(ticks * framesPerTick);
    }
}
