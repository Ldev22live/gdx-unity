package io.github.ldev22live.gdxunity.editor.core.agent;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Context provided to an AgentTool during validation and execution.
 * Exposes authoritative editor state, scene access, and transient step variables.
 */
public class AgentToolContext {
    private final EditorState editorState;
    private final Map<String, Object> stepVariables = new LinkedHashMap<>();
    private boolean confirmationApproved = false;

    public AgentToolContext(EditorState editorState) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
    }

    public AgentToolContext(EditorState editorState, boolean confirmationApproved) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.confirmationApproved = confirmationApproved;
    }

    public boolean isConfirmationApproved() {
        return confirmationApproved;
    }

    public void setConfirmationApproved(boolean confirmationApproved) {
        this.confirmationApproved = confirmationApproved;
    }

    public EditorState getEditorState() {
        return editorState;
    }

    public Scene getActiveScene() {
        return editorState.getActiveScene();
    }

    public void setStepVariable(String key, Object value) {
        if (key != null) {
            stepVariables.put(key, value);
        }
    }

    public Object getStepVariable(String key) {
        return stepVariables.get(key);
    }

    public boolean hasStepVariable(String key) {
        return stepVariables.containsKey(key);
    }

    public Map<String, Object> getAllStepVariables() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(stepVariables));
    }

    public void clearStepVariables() {
        stepVariables.clear();
    }
}
