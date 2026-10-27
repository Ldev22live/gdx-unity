package io.github.ldev22live.gdxunity.editor.core.gizmo;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.Objects;

/**
 * Immutable snapshot of an entity's local and world transform at the start of a gizmo drag.
 */
public record TransformSnapshot(
    Vector3 localPosition,
    Quaternion localRotation,
    Vector3 localScale,
    Vector3 worldPosition,
    Quaternion worldRotation,
    Vector3 worldScale,
    Vector3 parentWorldPosition,
    Quaternion parentWorldRotation,
    Vector3 parentWorldScale
) {
    public static TransformSnapshot capture(Transform transform) {
        Objects.requireNonNull(transform, "transform cannot be null");

        Vector3 localPos = transform.getLocalPosition();
        Quaternion localRot = transform.getLocalRotation();
        Vector3 localSc = transform.getLocalScale();

        Vector3 worldPos = transform.getWorldPosition();
        Quaternion worldRot = transform.getWorldRotation();
        Vector3 worldSc = transform.getWorldScale();

        Transform parent = transform.getParent();
        Vector3 parentWPos = (parent != null) ? parent.getWorldPosition() : null;
        Quaternion parentWRot = (parent != null) ? parent.getWorldRotation() : null;
        Vector3 parentWScale = (parent != null) ? parent.getWorldScale() : null;

        return new TransformSnapshot(
            localPos, localRot, localSc,
            worldPos, worldRot, worldSc,
            parentWPos, parentWRot, parentWScale
        );
    }
}
