package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Undo for operations that touch many entities at once (prefab create, instantiate, apply, revert, unpack).
 * <p>
 * Records every entity of the scene before and after the mutation: the {@link Entity} and {@link Component}
 * objects themselves plus name, active state, parent and local transform. Undo and redo put back exactly those
 * objects, so earlier and later commands in the history that hold references to them keep working.
 */
public class SceneSnapshotCommand implements EditorCommand {

    /** The change to record; returns false when nothing happened (the command is then dropped). */
    @FunctionalInterface
    public interface Mutation {
        boolean apply() throws Exception;
    }

    private final EditorState editorState;
    private final String description;
    private final Mutation mutation;
    private List<EntityRecord> before;
    private List<EntityRecord> after;

    public SceneSnapshotCommand(EditorState editorState, String description, Mutation mutation) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.mutation = Objects.requireNonNull(mutation, "mutation cannot be null");
    }

    @Override
    public boolean execute() throws Exception {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Cannot modify scene during Play/Pause mode", true);
            return false;
        }
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return false;
        }
        if (after != null) {
            restore(scene, after);
            afterRestore(true);
            return true;
        }
        before = capture(scene);
        boolean changed;
        try {
            changed = mutation.apply();
        } catch (Exception e) {
            restore(scene, before);
            throw e;
        }
        if (!changed) {
            restore(scene, before);
            return false;
        }
        after = capture(scene);
        editorState.notifyHierarchyModified();
        return true;
    }

    @Override
    public boolean undo() {
        Scene scene = editorState.getActiveScene();
        if (scene == null || before == null) {
            return false;
        }
        restore(scene, before);
        afterRestore(false);
        return true;
    }

    /** Hook for state outside the scene (for example a prefab file); {@code redo} is false on undo. */
    protected void afterRestore(boolean redo) {
    }

    @Override
    public String getDescription() {
        return description;
    }

    private static List<EntityRecord> capture(Scene scene) {
        List<EntityRecord> records = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            records.add(new EntityRecord(entity));
        }
        return records;
    }

    private void restore(Scene scene, List<EntityRecord> records) {
        Map<Entity, EntityRecord> wanted = new IdentityHashMap<>();
        for (EntityRecord record : records) {
            wanted.put(record.entity, record);
        }
        for (Entity entity : scene.getAllEntities()) {
            if (!wanted.containsKey(entity)) {
                scene.removeEntity(entity, false);
            }
        }
        for (EntityRecord record : records) {
            if (record.entity.getScene() != scene) {
                scene.addEntity(record.entity);
            }
        }
        for (EntityRecord record : records) {
            Transform transform = record.entity.getTransform();
            Transform parent = record.parent != null ? record.parent.getTransform() : null;
            if (transform.getParent() != parent) {
                transform.setParent(parent, false);
            }
            transform.setLocalPosition(record.position);
            transform.setLocalRotation(record.rotation);
            transform.setLocalScale(record.scale);
            record.entity.setName(record.name);
            record.entity.setActive(record.active);
            restoreComponents(record);
        }
        if (editorState.getSelectedEntityId() != null && scene.findEntityById(editorState.getSelectedEntityId()) == null) {
            editorState.clearSelection();
        }
        editorState.notifyHierarchyModified();
    }

    private static void restoreComponents(EntityRecord record) {
        Map<Component, Boolean> wanted = new IdentityHashMap<>();
        for (Component component : record.components) {
            wanted.put(component, Boolean.TRUE);
        }
        for (Component component : new ArrayList<>(record.entity.getAllComponents())) {
            if (!wanted.containsKey(component)) {
                record.entity.removeComponent(component);
            }
        }
        for (Component component : record.components) {
            if (component.getEntity() != record.entity) {
                record.entity.addComponent(component);
            }
        }
    }

    private static final class EntityRecord {
        final Entity entity;
        final String name;
        final boolean active;
        final Entity parent;
        final Vector3 position;
        final Quaternion rotation;
        final Vector3 scale;
        final List<Component> components;

        EntityRecord(Entity entity) {
            this.entity = entity;
            this.name = entity.getName();
            this.active = entity.isActiveSelf();
            Transform transform = entity.getTransform();
            this.parent = transform.getParent() != null ? transform.getParent().getEntity() : null;
            this.position = new Vector3(transform.getLocalPosition());
            this.rotation = new Quaternion(transform.getLocalRotation());
            this.scale = new Vector3(transform.getLocalScale());
            this.components = List.copyOf(entity.getAllComponents());
        }
    }
}
