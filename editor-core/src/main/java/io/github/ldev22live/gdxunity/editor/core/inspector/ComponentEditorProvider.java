package io.github.ldev22live.gdxunity.editor.core.inspector;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Strategy interface for rendering and binding property editors for a specific Component type.
 * Allows custom components to supply UI property inspection without hard-coding switch statements in InspectorPanel.
 *
 * @param <C> the Component type handled by this provider
 * @param <U> the UI container type (e.g., scene2d Table in desktop editor)
 */
public interface ComponentEditorProvider<C extends Component, U> {

    /**
     * @return the class of the Component this provider handles
     */
    Class<C> getComponentClass();

    /**
     * @return a user-friendly display title for the component section
     */
    String getDisplayName();

    /**
     * Builds and attaches the property controls for the given component into the target UI container.
     *
     * @param targetContainer the UI container to populate (e.g. Table)
     * @param entity the Entity possessing the component
     * @param component the Component instance to inspect/edit
     * @param editorState the current EditorState for command execution / undo-redo
     * @param skin the active UI skin or theme container
     */
    void buildEditor(U targetContainer, Entity entity, C component, EditorState editorState, Object skin);
}
