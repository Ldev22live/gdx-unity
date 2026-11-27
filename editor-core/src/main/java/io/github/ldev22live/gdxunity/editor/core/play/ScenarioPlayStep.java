package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioRuntime;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioRuntimeComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * The scenario rules step of editor Play (TD-109): on the copy Play runs, after physics is created, it attaches a
 * {@link ScenarioRuntime} through {@link ScenarioRuntime#attach}, the same call the game runtime makes, so rules behave the same in
 * the editor and in an exported game. The rule log goes to {@link #setLog the editor's log} and is also kept here (the last
 * {@value #KEPT_LINES} lines). {@link PlayModeController} registers it after the genre profile step.
 */
public final class ScenarioPlayStep implements PlayModeController.Extension {
    /** How many rule log lines are kept for {@link #getRecentLog()}. */
    public static final int KEPT_LINES = 200;

    private final Deque<String> recent = new ArrayDeque<>();
    private ScenarioRuntime.Log log = line -> { };
    private ScenarioRuntimeComponent current;

    /** Where rule log lines also go (the Output Log in the desktop editor). */
    public void setLog(ScenarioRuntime.Log log) {
        this.log = Objects.requireNonNull(log, "log cannot be null");
    }

    @Override
    public List<String> onPlay(Scene runtimeScene, PlayModeController controller) {
        recent.clear();
        try {
            current = ScenarioRuntime.attach(runtimeScene, controller.getPhysicsSystem(), controller.getPhysicsSystem3D(), this::write);
        } catch (IllegalArgumentException e) {
            current = null;
            return List.of("Scenario: " + e.getMessage());
        }
        return List.of();
    }

    @Override
    public void onStop(Scene runtimeScene) {
        current = null;
    }

    private void write(String line) {
        if (recent.size() == KEPT_LINES) {
            recent.removeFirst();
        }
        recent.addLast(line);
        log.info(line);
    }

    /** The running scenario of the current Play, or null when not playing or the scene has no scenario. */
    public ScenarioRuntime getRuntime() {
        return current == null ? null : current.getRuntime();
    }

    /** The rule log of the current (or last) Play, oldest first. */
    public List<String> getRecentLog() {
        return new ArrayList<>(recent);
    }
}
