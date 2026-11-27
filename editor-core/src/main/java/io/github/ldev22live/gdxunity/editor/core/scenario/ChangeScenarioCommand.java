package io.github.ldev22live.gdxunity.editor.core.scenario;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;

/**
 * Replaces the active scene's {@code scenario} block with new JSON text, undoably (TD-108). The block's text before the edit is
 * recorded when it first runs; undo puts that exact text back, so data this version does not edit (objectives, rules, members of
 * newer clients) is restored byte for byte.
 */
public class ChangeScenarioCommand implements EditorCommand {
    private final EditorState editorState;
    private final String description;
    private final String after;
    private Scene scene;
    private String before;
    private boolean recorded;

    /**
     * @param after the new block as compact JSON, or null to remove the block
     */
    public ChangeScenarioCommand(EditorState editorState, String description, String after) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.after = after;
    }

    @Override
    public boolean execute() {
        Scene active = editorState.getActiveScene();
        if (active == null) {
            return false;
        }
        if (!recorded) {
            scene = active;
            before = active.getScenarioJson();
            recorded = true;
            if (Objects.equals(before, after)) {
                return false;
            }
        } else if (active != scene) {
            return false;
        }
        apply(after);
        return true;
    }

    @Override
    public boolean undo() {
        if (!recorded || editorState.getActiveScene() != scene) {
            return false;
        }
        apply(before);
        return true;
    }

    private void apply(String json) {
        scene.setPreservedFields(ScenarioSetup.withScenario(scene.getPreservedFields(), json));
        editorState.notifyHierarchyModified();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
