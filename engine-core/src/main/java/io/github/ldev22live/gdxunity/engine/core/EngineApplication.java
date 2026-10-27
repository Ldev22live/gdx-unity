package io.github.ldev22live.gdxunity.engine.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceManager;
import io.github.ldev22live.gdxunity.engine.core.time.FrameClock;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core engine application implementing LibGDX's {@link ApplicationAdapter} and {@link EngineLifecycle}.
 * Provides an explicit state machine: NEW -> RUNNING <-> PAUSED -> DISPOSING -> DISPOSED (or FAILED).
 */
public class EngineApplication extends ApplicationAdapter implements EngineLifecycle {
    private static final String TAG = "EngineApplication";

    private final FrameClock frameClock;
    private final ResourceManager resourceManager;
    private final List<EngineLifecycleListener> listeners = new CopyOnWriteArrayList<>();

    private EngineRenderer renderer;
    private volatile EngineState state = EngineState.NEW;
    private Throwable failureCause;

    public EngineApplication() {
        this(new FrameClock(), new ResourceManager(), null);
    }

    public EngineApplication(EngineRenderer renderer) {
        this(new FrameClock(), new ResourceManager(), renderer);
    }

    public EngineApplication(FrameClock frameClock, ResourceManager resourceManager, EngineRenderer renderer) {
        this.frameClock = Objects.requireNonNull(frameClock, "frameClock cannot be null");
        this.resourceManager = Objects.requireNonNull(resourceManager, "resourceManager cannot be null");
        this.renderer = renderer;
    }

    // --- LibGDX ApplicationListener methods mapped to EngineLifecycle ---

    @Override
    public void create() {
        initialize();
    }

    @Override
    public void resize(int width, int height) {
        if (state == EngineState.DISPOSED || state == EngineState.FAILED) {
            return;
        }
        if (width < 0 || height < 0) {
            logError("Ignoring invalid resize dimensions: " + width + "x" + height);
            return;
        }
        if (renderer != null) {
            try {
                renderer.resize(width, height);
            } catch (Throwable t) {
                logError("Renderer error during resize", t);
            }
        }
    }

    @Override
    public void render() {
        if (state != EngineState.RUNNING) {
            return;
        }

        try {
            float dt = frameClock.tick();
            if (renderer != null) {
                renderer.render(dt);
            }
        } catch (Throwable t) {
            transitionTo(EngineState.FAILED);
            this.failureCause = t;
            logError("Uncaught exception in render loop, transitioned to FAILED", t);
            // Safe cleanup of resources on fatal failure
            try {
                dispose();
            } catch (Throwable cleanupEx) {
                logError("Exception during failure cleanup", cleanupEx);
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new RuntimeException("Engine render failed", t);
            }
        }
    }

    @Override
    public void pause() {
        if (state == EngineState.RUNNING) {
            transitionTo(EngineState.PAUSED);
        }
    }

    @Override
    public void resume() {
        if (state == EngineState.PAUSED) {
            transitionTo(EngineState.RUNNING);
        }
    }

    @Override
    public void dispose() {
        if (state == EngineState.DISPOSED || state == EngineState.DISPOSING) {
            return;
        }

        EngineState previous = state;
        transitionTo(EngineState.DISPOSING);

        Throwable disposalError = null;

        if (renderer != null) {
            try {
                renderer.dispose();
            } catch (Throwable t) {
                disposalError = t;
                logError("Error disposing renderer", t);
            }
        }

        try {
            resourceManager.dispose();
        } catch (Throwable t) {
            if (disposalError == null) {
                disposalError = t;
            } else {
                disposalError.addSuppressed(t);
            }
            logError("Error disposing resource manager", t);
        }

        transitionTo(EngineState.DISPOSED);

        if (disposalError != null) {
            if (disposalError instanceof RuntimeException) {
                throw (RuntimeException) disposalError;
            }
            throw new RuntimeException("Disposal encountered errors", disposalError);
        }
    }

    // --- EngineLifecycle methods ---

    @Override
    public synchronized void initialize() {
        if (state == EngineState.RUNNING) {
            logInfo("Engine is already initialized and running. Ignoring duplicate initialize() call.");
            return;
        }
        if (state == EngineState.DISPOSED || state == EngineState.DISPOSING) {
            throw new IllegalStateException("Cannot initialize engine after it has been disposed.");
        }
        if (state == EngineState.FAILED) {
            throw new IllegalStateException("Cannot initialize engine in FAILED state.", failureCause);
        }

        try {
            if (renderer != null) {
                renderer.initialize();
            }
            frameClock.reset();
            transitionTo(EngineState.RUNNING);
        } catch (Throwable t) {
            this.failureCause = t;
            transitionTo(EngineState.FAILED);
            // Attempt cleanup on partial failure
            try {
                if (renderer != null) {
                    renderer.dispose();
                }
                resourceManager.dispose();
            } catch (Throwable cleanupEx) {
                t.addSuppressed(cleanupEx);
            }
            logError("Initialization failed", t);
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new RuntimeException("Engine initialization failed", t);
            }
        }
    }

    public synchronized void transitionTo(EngineState newState) {
        if (this.state == newState) {
            return;
        }
        validateTransition(this.state, newState);
        EngineState oldState = this.state;
        this.state = newState;
        for (EngineLifecycleListener listener : listeners) {
            try {
                listener.onStateChanged(oldState, newState);
            } catch (Throwable t) {
                logError("Lifecycle listener threw exception on transition: " + oldState + " -> " + newState, t);
            }
        }
    }

    private void validateTransition(EngineState from, EngineState to) {
        switch (from) {
            case NEW:
                if (to == EngineState.RUNNING || to == EngineState.FAILED || to == EngineState.DISPOSING) {
                    return;
                }
                break;
            case RUNNING:
                if (to == EngineState.PAUSED || to == EngineState.DISPOSING || to == EngineState.FAILED) {
                    return;
                }
                break;
            case PAUSED:
                if (to == EngineState.RUNNING || to == EngineState.DISPOSING || to == EngineState.FAILED) {
                    return;
                }
                break;
            case DISPOSING:
                if (to == EngineState.DISPOSED) {
                    return;
                }
                break;
            case DISPOSED:
                // No transitions out of DISPOSED
                break;
            case FAILED:
                if (to == EngineState.DISPOSING || to == EngineState.DISPOSED) {
                    return;
                }
                break;
        }
        throw new IllegalStateException("Illegal state transition from " + from + " to " + to);
    }

    public void addLifecycleListener(EngineLifecycleListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeLifecycleListener(EngineLifecycleListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public EngineState getState() {
        return state;
    }

    public Throwable getFailureCause() {
        return failureCause;
    }

    public FrameClock getFrameClock() {
        return frameClock;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public EngineRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(EngineRenderer renderer) {
        this.renderer = renderer;
    }

    private void logInfo(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message);
        } else {
            System.out.println("[" + TAG + "] INFO: " + message);
        }
    }

    private void logError(String message) {
        if (Gdx.app != null) {
            Gdx.app.error(TAG, message);
        } else {
            System.err.println("[" + TAG + "] ERROR: " + message);
        }
    }

    private void logError(String message, Throwable t) {
        if (Gdx.app != null) {
            Gdx.app.error(TAG, message, t);
        } else {
            System.err.println("[" + TAG + "] ERROR: " + message);
            if (t != null) t.printStackTrace(System.err);
        }
    }
}
