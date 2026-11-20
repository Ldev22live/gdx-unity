package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.audio.SfxPlayer;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.EngineSoundComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The game runtime's sound device (found in roadmap M9.5): the launcher starts the session before the game window exists, when there
 * is no audio backend, so the device must be opened once the renderer initializes ({@link GameSession#startAudioOutput()}). Before
 * this, a game run from the launcher or a packaged game played no sound at all. Also the output's latency and render statistics.
 */
class GameAudioOutputTest {
    /** What LibGDX's OpenAL device reports with the default configuration: 9 buffers of 512 frames. */
    private static final int DEVICE_LATENCY_FRAMES = 4608;

    @TempDir
    Path project;

    private final List<String> info = new ArrayList<>();
    private Scene scene;
    private Audio previousAudio;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        previousAudio = Gdx.audio;
        Gdx.audio = null;
        scene = new Scene("Race");
        scene.createEntity("Camera").addComponent(new CameraComponent());
    }

    @AfterEach
    void restore() {
        Gdx.audio = previousAudio;
    }

    private GameSession session() {
        return new GameSession(project.toFile(), scene, new GameScripts(project.toFile(), getClass().getClassLoader()),
            new GameSession.Log() {
                @Override
                public void info(String message) {
                    info.add(message);
                }

                @Override
                public void error(String message) {
                    info.add("error: " + message);
                }
            });
    }

    private static Audio backend() {
        AudioDevice device = mock(AudioDevice.class);
        when(device.getLatency()).thenReturn(DEVICE_LATENCY_FRAMES);
        Audio audio = mock(Audio.class);
        when(audio.newAudioDevice(anyInt(), anyBoolean())).thenReturn(device);
        return audio;
    }

    @Test
    void shouldOpenTheDeviceWhenTheRendererInitializesAfterTheSessionStartedWithoutABackend() {
        scene.createEntity("Car").addComponent(new EngineSoundComponent("Muscle V8"));
        GameSession session = session();
        assertEquals(List.of(), session.start());
        assertFalse(session.getAudio().isOutputRunning(), "no backend yet when the launcher starts the session");

        Gdx.audio = backend();
        session.startAudioOutput();

        assertTrue(session.getAudio().isOutputRunning(), "the device opens once the window's audio backend exists");
        AudioOutputStats stats = session.getAudio().getOutputStats();
        assertNotNull(stats);
        assertEquals(DEVICE_LATENCY_FRAMES + 512, stats.latencyFrames(), "device buffers plus one block of the output");
        assertEquals((DEVICE_LATENCY_FRAMES + 512) * 1000.0 / SfxPlayer.SAMPLE_RATE, stats.latencyMillis(), 1e-9);
        assertEquals(512 * 1000.0 / SfxPlayer.SAMPLE_RATE, stats.blockMillis(), 1e-9);
        session.dispose();
    }

    @Test
    void shouldOpenTheDeviceOnlyOnce() {
        scene.createEntity("Car").addComponent(new EngineSoundComponent("Muscle V8"));
        GameSession session = session();
        session.start();
        Audio backend = backend();
        Gdx.audio = backend;

        session.startAudioOutput();
        session.startAudioOutput();

        verify(backend, times(1)).newAudioDevice(anyInt(), anyBoolean());
        session.dispose();
    }

    @Test
    void shouldOpenNoDeviceForASceneWithoutSound() {
        GameSession session = session();
        session.start();
        Audio backend = backend();
        Gdx.audio = backend;

        session.startAudioOutput();

        assertFalse(session.getAudio().isOutputRunning());
        assertNull(session.getAudio().getOutputStats());
        verifyNoInteractions(backend);
        session.dispose();
    }

    @Test
    void shouldLogAMissingDeviceAndKeepPlaying() {
        scene.createEntity("Car").addComponent(new EngineSoundComponent("Muscle V8"));
        GameSession session = session();
        session.start();
        Audio backend = mock(Audio.class);
        when(backend.newAudioDevice(anyInt(), anyBoolean())).thenThrow(new IllegalStateException("no sound card"));
        Gdx.audio = backend;

        session.startAudioOutput();

        assertFalse(session.getAudio().isOutputRunning());
        assertEquals(List.of("No sound output: no sound card"), info);
        assertTrue(session.isStarted());
        session.dispose();
    }

    @Test
    void shouldDoNothingBeforeTheSessionStarts() {
        scene.createEntity("Car").addComponent(new EngineSoundComponent("Muscle V8"));
        GameSession session = session();
        Gdx.audio = backend();

        session.startAudioOutput();

        assertNull(session.getAudio());
        session.dispose();
    }
}
