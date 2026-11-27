package io.github.ldev22live.gdxunity.engine.scene.scenario;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.RuntimeOnlyComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Carries a playing scene's {@link ScenarioRuntime} (TD-109): it advances the rules with the scene update, and the HUD reads what to
 * show from it. Runtime only, so it can never be saved.
 */
public final class ScenarioRuntimeComponent extends BaseComponent implements RuntimeOnlyComponent {
    /** The name of the runtime-only entity that carries it. */
    public static final String ENTITY_NAME = "Scenario Rules (Play)";

    private final ScenarioRuntime runtime;
    private int linesRevision = -1;
    private List<String> objectiveLines = List.of();

    public ScenarioRuntimeComponent(ScenarioRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime cannot be null");
    }

    public ScenarioRuntime getRuntime() {
        return runtime;
    }

    @Override
    public void onUpdate(float deltaTime) {
        runtime.update(deltaTime);
    }

    /** The visible objectives as HUD lines ({@code [x] Reach the house}), rebuilt only when the runtime changed. */
    public List<String> objectiveLines() {
        if (linesRevision != runtime.getRevision()) {
            linesRevision = runtime.getRevision();
            List<String> lines = new ArrayList<>();
            for (ScenarioRuntime.Objective objective : runtime.getObjectives()) {
                if (objective.visible()) {
                    lines.add((objective.complete() ? "[x] " : "[ ] ") + objective.text());
                }
            }
            objectiveLines = List.copyOf(lines);
        }
        return objectiveLines;
    }
}
