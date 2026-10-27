package io.github.ldev22live.gdxunity.editor.core.workspace;

/**
 * Context provided to a StudioWorkspace during initialization and execution.
 * Provides access to shared editor services.
 */
public interface WorkspaceContext {
    io.github.ldev22live.gdxunity.editor.core.EditorState getEditorState();
}
