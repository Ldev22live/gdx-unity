package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Command for mutating properties of a Component with full undo and redo support.
 *
 * @param <C> type of Component being modified
 * @param <V> type of property value (value object, snapshot, or primitive wrapper)
 */
public class ModifyComponentPropertyCommand<C extends Component, V> implements EditorCommand {
    private final EditorState editorState;
    private final UUID entityId;
    private final C component;
    private final V oldValue;
    private final V newValue;
    private final Consumer<V> setter;
    private final String description;

    public ModifyComponentPropertyCommand(EditorState editorState,
                                          UUID entityId,
                                          C component,
                                          V oldValue,
                                          V newValue,
                                          Consumer<V> setter,
                                          String description) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.component = Objects.requireNonNull(component, "component cannot be null");
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.setter = Objects.requireNonNull(setter, "setter cannot be null");
        this.description = description != null ? description : "Modify Component Property";
    }

    @Override
    public boolean execute() {
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        setter.accept(newValue);
        editorState.notifyEntityTransformModified(entity);
        editorState.setStatusMessage(description, false);
        return true;
    }

    @Override
    public boolean undo() {
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        setter.accept(oldValue);
        editorState.notifyEntityTransformModified(entity);
        editorState.setStatusMessage("Undid: " + description, false);
        return true;
    }

    @Override
    public String getDescription() {
        return description;
    }

    private Entity findTarget() {
        if (editorState.getActiveScene() == null) return null;
        return editorState.getActiveScene().findEntityById(entityId);
    }
}
