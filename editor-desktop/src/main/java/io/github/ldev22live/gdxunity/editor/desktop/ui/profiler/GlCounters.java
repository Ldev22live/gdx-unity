package io.github.ldev22live.gdxunity.editor.desktop.ui.profiler;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import io.github.ldev22live.gdxunity.engine.core.profile.FrameProfiler;

import java.util.List;
import java.util.Objects;

/**
 * The frame debugger's counts (roadmap M9.6): draw calls, vertices, texture binds, shader switches and GL errors, read from LibGDX's
 * {@link GLProfiler}, so the Profiler shows them per pass (scene view, 3D scene, editor UI).
 * <p>
 * Counting wraps every GL call and asks the driver for errors after each one, which costs time of its own: the counts are switched on
 * only while the Profiler records, and the panel says the times include this. A GL error is counted, not logged per call, so a scene
 * that makes one every frame does not flood the Output Log.
 */
public final class GlCounters implements FrameProfiler.CounterSource {
    private static final List<String> NAMES = List.of("Draw calls", "Vertices", "Texture binds", "Shader switches", "GL errors");
    private static final int DRAW_CALLS = 0;
    private static final int VERTICES = 1;
    private static final int TEXTURE_BINDS = 2;
    private static final int SHADER_SWITCHES = 3;
    private static final int GL_ERRORS = 4;

    private final GLProfiler gl;
    private long errors;
    private int lastError;

    public GlCounters(Graphics graphics) {
        gl = new GLProfiler(Objects.requireNonNull(graphics, "graphics cannot be null"));
        gl.setListener(error -> {
            errors++;
            lastError = error;
        });
    }

    /** Starts or stops counting: wraps or unwraps {@code Gdx.gl}. */
    public void setEnabled(boolean enabled) {
        if (enabled && !gl.isEnabled()) {
            gl.enable();
        } else if (!enabled && gl.isEnabled()) {
            gl.disable();
        }
    }

    public boolean isEnabled() {
        return gl.isEnabled();
    }

    /** The most recent GL error code counted, 0 when none. */
    public int getLastError() {
        return lastError;
    }

    @Override
    public int size() {
        return NAMES.size();
    }

    @Override
    public String name(int counter) {
        return NAMES.get(counter);
    }

    @Override
    public long read(int counter) {
        return switch (counter) {
            case DRAW_CALLS -> gl.getDrawCalls();
            case VERTICES -> (long) gl.getVertexCount().total;
            case TEXTURE_BINDS -> gl.getTextureBindings();
            case SHADER_SWITCHES -> gl.getShaderSwitches();
            case GL_ERRORS -> errors;
            default -> throw new IllegalArgumentException("No counter " + counter);
        };
    }

    /** Resets the totals each frame, so the vertex total (a float) never grows past what it holds exactly. */
    @Override
    public void frameStarted() {
        gl.reset();
        errors = 0;
    }
}
