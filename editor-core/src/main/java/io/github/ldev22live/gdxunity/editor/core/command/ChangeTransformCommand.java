package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to modify an entity's local transform (position, rotation, scale).
 */
public class ChangeTransformCommand implements EditorCommand {
    private final EditorState editorState;
    private final UUID entityId;
    private final TransformSnapshot before;
    private final TransformSnapshot after;
    private final String description;

    public ChangeTransformCommand(EditorState editorState, UUID entityId, TransformSnapshot before, TransformSnapshot after, String description) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.before = Objects.requireNonNull(before, "before snapshot cannot be null");
        this.after = Objects.requireNonNull(after, "after snapshot cannot be null");
        this.description = (description != null) ? description : "Modify Transform";
    }

    public static boolean hasChanged(TransformSnapshot a, TransformSnapshot b) {
        if (a == null || b == null) return false;
        if (!a.localPosition().epsilonEquals(b.localPosition(), 0.0001f)) return true;
        if (!a.localScale().epsilonEquals(b.localScale(), 0.0001f)) return true;
        return !a.localRotation().equals(b.localRotation());
    }

    @Override
    public boolean execute() {
        if (!hasChanged(before, after)) {
            return false;
        }
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        applySnapshot(entity.getTransform(), after);
        editorState.notifyEntityTransformModified(entity);
        return true;
    }

    @Override
    public boolean undo() {
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        applySnapshot(entity.getTransform(), before);
        editorState.notifyEntityTransformModified(entity);
        editorState.setStatusMessage("Undid transform change on " + entity.getName(), false);
        return true;
    }

    private void applySnapshot(Transform transform, TransformSnapshot snapshot) {
        transform.setLocalPosition(snapshot.localPosition());
        transform.setLocalRotation(snapshot.localRotation());
        transform.setLocalScale(snapshot.localScale());
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
