package io.github.ldev22live.gdxunity.editor.desktop.ui.music;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import io.github.ldev22live.gdxunity.engine.audio.song.SongPlayer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Feeds the music player's sound to the speakers from a background thread, through a libGDX {@link AudioDevice}.
 * The device opens on the first {@link #start()}, so the editor does not touch the sound card until music is used.
 */
final class MusicAudioOutput implements Disposable {
    /** Frames rendered per write; about 23 ms at 44.1 kHz, small enough for responsive preview notes. */
    private static final int BLOCK_FRAMES = 1024;
    private static final long IDLE_SLEEP_MILLIS = 20L;

    private final Supplier<SongPlayer> player;
    private volatile boolean running;
    private volatile String error;
    private volatile long framesWritten;
    private Thread thread;
    private AudioDevice device;

    MusicAudioOutput(Supplier<SongPlayer> player) {
        this.player = Objects.requireNonNull(player, "player cannot be null");
    }

    /** Why the sound device could not be used, or null. */
    String getError() {
        return error;
    }

    boolean isRunning() {
        return running;
    }

    /** Frames sent to the sound device since it started; shows that sound is really flowing. */
    long getFramesWritten() {
        return framesWritten;
    }

    /** Opens the device and starts the thread; does nothing if already started. */
    void start() {
        if (running) {
            return;
        }
        try {
            device = Gdx.audio.newAudioDevice((int) Song.SAMPLE_RATE, false);
        } catch (RuntimeException e) {
            error = "No sound output: " + e.getMessage();
            return;
        }
        error = null;
        running = true;
        thread = new Thread(this::run, "music-audio");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        float[] left = new float[BLOCK_FRAMES];
        float[] right = new float[BLOCK_FRAMES];
        float[] interleaved = new float[BLOCK_FRAMES * 2];
        while (running) {
            SongPlayer current = player.get();
            if (current == null) {
                try {
                    Thread.sleep(IDLE_SLEEP_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            current.render(left, right, BLOCK_FRAMES);
            for (int i = 0; i < BLOCK_FRAMES; i++) {
                interleaved[2 * i] = Math.min(Math.max(left[i], -1f), 1f);
                interleaved[2 * i + 1] = Math.min(Math.max(right[i], -1f), 1f);
            }
            // Blocks until the device has taken the samples, which paces the loop at real time
            device.writeSamples(interleaved, 0, interleaved.length);
            framesWritten += BLOCK_FRAMES;
        }
    }

    @Override
    public void dispose() {
        running = false;
        if (thread != null) {
            try {
                thread.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        if (device != null) {
            device.dispose();
            device = null;
        }
    }
}
