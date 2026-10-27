package io.github.ldev22live.gdxunity.engine.core;

import io.github.ldev22live.gdxunity.engine.core.resource.ResourceManager;
import io.github.ldev22live.gdxunity.engine.core.time.FrameClock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EngineLifecycleTest {

    @Test
    void shouldTransitionToRunningWhenEngineIsInitialized() {
        EngineApplication app = new EngineApplication();
        assertEquals(EngineState.NEW, app.getState());

        app.initialize();
        assertEquals(EngineState.RUNNING, app.getState());
    }

    @Test
    void shouldHandleDuplicateInitializationSafelyWhenAlreadyRunning() {
        EngineApplication app = new EngineApplication();
        app.initialize();
        assertEquals(EngineState.RUNNING, app.getState());

        // Second initialize call should be ignored safely without error
        assertDoesNotThrow(app::initialize);
        assertEquals(EngineState.RUNNING, app.getState());
    }

    @Test
    void shouldTransitionBetweenRunningAndPausedWhenPausedAndResumed() {
        EngineApplication app = new EngineApplication();
        app.initialize();
        assertEquals(EngineState.RUNNING, app.getState());

        app.pause();
        assertEquals(EngineState.PAUSED, app.getState());

        app.resume();
        assertEquals(EngineState.RUNNING, app.getState());
    }

    @Test
    void shouldNotRenderWhenNotInRunningState() {
        AtomicInteger renderCount = new AtomicInteger(0);
        EngineRenderer mockRenderer = new EngineRenderer() {
            @Override public void initialize() {}
            @Override public void resize(int width, int height) {}
            @Override public void render(float deltaTime) { renderCount.incrementAndGet(); }
            @Override public void dispose() {}
        };

        EngineApplication app = new EngineApplication(mockRenderer);
        // In NEW state
        app.render();
        assertEquals(0, renderCount.get());

        // In PAUSED state
        app.initialize();
        app.pause();
        app.render();
        assertEquals(0, renderCount.get());

        // In RUNNING state
        app.resume();
        app.render();
        assertEquals(1, renderCount.get());

        // In DISPOSED state
        app.dispose();
        app.render();
        assertEquals(1, renderCount.get());
    }

    @Test
    void shouldDisposeResourcesIdempotentlyWhenEngineIsDisposed() {
        AtomicInteger disposeCount = new AtomicInteger(0);
        EngineRenderer mockRenderer = new EngineRenderer() {
            @Override public void initialize() {}
            @Override public void resize(int width, int height) {}
            @Override public void render(float deltaTime) {}
            @Override public void dispose() { disposeCount.incrementAndGet(); }
        };

        EngineApplication app = new EngineApplication(mockRenderer);
        app.initialize();

        app.dispose();
        assertEquals(EngineState.DISPOSED, app.getState());
        assertEquals(1, disposeCount.get());

        // Second dispose should be a no-op
        assertDoesNotThrow(app::dispose);
        assertEquals(EngineState.DISPOSED, app.getState());
        assertEquals(1, disposeCount.get());
    }

    @Test
    void shouldTransitionToFailedAndCleanupWhenInitializationThrows() {
        AtomicBoolean cleanedUp = new AtomicBoolean(false);
        EngineRenderer failingRenderer = new EngineRenderer() {
            @Override
            public void initialize() {
                throw new RuntimeException("Simulated hardware init error");
            }

            @Override public void resize(int width, int height) {}
            @Override public void render(float deltaTime) {}

            @Override
            public void dispose() {
                cleanedUp.set(true);
            }
        };

        EngineApplication app = new EngineApplication(failingRenderer);

        RuntimeException ex = assertThrows(RuntimeException.class, app::initialize);
        assertTrue(ex.getMessage().contains("Simulated hardware init error"));
        assertEquals(EngineState.FAILED, app.getState());
        assertTrue(cleanedUp.get(), "Disposal/cleanup should have run on partial failure");
    }

    @Test
    void shouldNotifyListenersWhenStateTransitionsOccur() {
        EngineApplication app = new EngineApplication();
        AtomicInteger transitions = new AtomicInteger(0);
        app.addLifecycleListener((oldState, newState) -> transitions.incrementAndGet());

        app.initialize(); // NEW -> RUNNING
        app.pause();      // RUNNING -> PAUSED
        app.resume();     // PAUSED -> RUNNING
        app.dispose();    // RUNNING -> DISPOSING, DISPOSING -> DISPOSED

        assertEquals(5, transitions.get());
    }
}
