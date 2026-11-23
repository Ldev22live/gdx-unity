package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command to duplicate an entity in the active scene with independent identity,
 * copied transform offset, and undo/redo support.
 * <p>
 * Components are copied through the scene serializer, so every component type that can be saved in a scene file is
 * copied with all its saved fields, and a new component type is covered as soon as it has a serializer (the same
 * path the prefab system uses). A component without a serializer cannot be copied; the status message says how many
 * were left out.
 */
public class DuplicateEntityCommand implements EditorCommand {
    /** How far, in scene units on x and y, the copy is placed from the original. */
    private static final float COPY_OFFSET = 20f;

    private final EditorState editorState;
    private final UUID sourceEntityId;
    private Entity duplicatedEntity;
    private UUID duplicatedId;
    private int uncopiedComponents;
    /** Components detached by Scene.removeEntity on undo; re-attached on redo. */
    private List<Component> componentsToRestore = List.of();

    public DuplicateEntityCommand(EditorState editorState, UUID sourceEntityId) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.sourceEntityId = Objects.requireNonNull(sourceEntityId, "sourceEntityId cannot be null");
    }

    @Override
    public boolean execute() {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Cannot duplicate entity during Play/Pause mode", true);
            return false;
        }
        Scene scene = editorState.getActiveScene();
        if (scene == null) return false;

        Entity source = scene.findEntityById(sourceEntityId);
        if (source == null && duplicatedEntity == null) return false;

        if (duplicatedEntity == null) {
            duplicatedEntity = scene.createEntity(source.getName() + " (Copy)");
            duplicatedId = duplicatedEntity.getId();

            Vector3 pos = source.getTransform().getLocalPosition();
            duplicatedEntity.getTransform().setLocalPosition(pos.x + COPY_OFFSET, pos.y + COPY_OFFSET, pos.z);
            duplicatedEntity.getTransform().setLocalRotation(source.getTransform().getLocalRotation());
            duplicatedEntity.getTransform().setLocalScale(source.getTransform().getLocalScale());

            copyComponents(source, duplicatedEntity);
        } else {
            // Redo: re-add the existing entity instance with the components undo detached
            scene.addEntity(duplicatedEntity);
            for (Component component : componentsToRestore) {
                duplicatedEntity.addComponent(component);
            }
        }

        editorState.setSelectedEntity(duplicatedEntity);
        editorState.notifyHierarchyModified();
        boolean incomplete = uncopiedComponents > 0;
        editorState.setStatusMessage("Duplicated: " + duplicatedEntity.getName()
            + (incomplete ? " (" + uncopiedComponents + " component(s) without a serializer were not copied)" : ""), incomplete);
        return true;
    }

    /** Copies every component of {@code source} that the scene serializer can save onto {@code target}. */
    private void copyComponents(Entity source, Entity target) {
        SceneSerializer serializer = editorState.getSerializer();
        JsonValue saved = serializer.entityToJsonValue(source).get("components");
        int copied = 0;
        if (saved != null) {
            for (JsonValue entry = saved.child; entry != null; entry = entry.next) {
                Component copy = serializer.readComponent(entry);
                if (copy != null) {
                    target.addComponent(copy);
                    copied++;
                }
            }
        }
        uncopiedComponents = source.getAllComponents().size() - copied;
    }

    @Override
    public boolean undo() {
        if (duplicatedEntity == null) return false;
        Scene scene = editorState.getActiveScene();
        if (scene == null) return false;

        if (Objects.equals(editorState.getSelectedEntityId(), duplicatedId)) {
            editorState.clearSelection();
        }
        // Scene.removeEntity detaches all components; remember them for redo
        componentsToRestore = new ArrayList<>(duplicatedEntity.getAllComponents());
        boolean removed = scene.removeEntity(duplicatedEntity, false);
        if (removed) {
            editorState.notifyHierarchyModified();
            editorState.setStatusMessage("Undid duplication of: " + duplicatedEntity.getName(), false);
        }
        return removed;
    }

    @Override
    public String getDescription() {
        return "Duplicate Entity";
    }

    public Entity getDuplicatedEntity() {
        return duplicatedEntity;
    }
}
