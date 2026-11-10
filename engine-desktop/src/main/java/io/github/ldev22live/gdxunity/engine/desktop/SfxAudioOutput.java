package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.audio.SfxPlayer;

import java.util.Objects;

/**
 * Feeds an {@link SfxPlayer}'s sound to the speakers from a background thread, through a libGDX
 * {@link AudioDevice}. Needs a running LibGDX application; without one {@link #start()} records an error instead of
 * throwing, because a missing sound device must not stop a game from playing.
 */
public final class SfxAudioOutput implements Disposable {
    /** Frames rendered per write; about 12 ms at 44.1 kHz, short enough that an effect starts promptly. */
    private static final int BLOCK_FRAMES = 512;
    /** How long {@link #dispose()} waits for the output thread to finish its current write. */
    private static final long JOIN_MILLIS = 500L;

    private final StereoSource player;
    private volatile boolean running;
    private volatile String error;
    private Thread thread;
    private AudioDevice device;

    /** @param player the mixer to play; the caller keeps ownership of it */
    public SfxAudioOutput(SfxPlayer player) {
        this(Objects.requireNonNull(player, "player cannot be null")::render);
    }

    /** @param source what to play: for a scene with songs, effects plus songs (see {@link GameAudio}) */
    SfxAudioOutput(StereoSource source) {
        this.player = Objects.requireNonNull(source, "source cannot be null");
    }

    /** Why the sound device could not be used, or null. */
    public String getError() {
        return error;
    }

    public boolean isRunning() {
        return running;
    }

    /** Opens the device and starts the thread; does nothing if already started. */
    public void start() {
        if (running) {
            return;
        }
        if (Gdx.audio == null) {
            error = "No sound output: there is no LibGDX audio backend";
            return;
        }
        try {
            device = Gdx.audio.newAudioDevice(SfxPlayer.SAMPLE_RATE, false);
        } catch (RuntimeException e) {
            error = "No sound output: " + e.getMessage();
            return;
        }
        error = null;
        running = true;
        thread = new Thread(this::run, "sfx-audio");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        float[] left = new float[BLOCK_FRAMES];
        float[] right = new float[BLOCK_FRAMES];
        float[] interleaved = new float[BLOCK_FRAMES * 2];
        while (running) {
            player.render(left, right, BLOCK_FRAMES);
            for (int i = 0; i < BLOCK_FRAMES; i++) {
                interleaved[2 * i] = Math.min(Math.max(left[i], -1f), 1f);
                interleaved[2 * i + 1] = Math.min(Math.max(right[i], -1f), 1f);
            }
            // Blocks until the device has taken the samples, which paces the loop at real time
            device.writeSamples(interleaved, 0, interleaved.length);
        }
    }

    /** Stops the thread and closes the device. Idempotent. */
    @Override
    public void dispose() {
        running = false;
        if (thread != null) {
            try {
                thread.join(JOIN_MILLIS);
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
