package io.github.ldev22live.gdxunity.engine.animation;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.Objects;

/**
 * One animated property of one entity: which entity (a path of child names below the entity playing the clip), which
 * property, and the curve of its values.
 */
public class AnimationTrack {

    /** Separates child names in {@link #getTargetPath()}. */
    public static final String PATH_SEPARATOR = "/";

    private final String targetPath;
    private final String propertyId;
    private final AnimationCurve curve;

    /**
     * @param targetPath "" for the entity playing the clip, otherwise child names joined by "/" (e.g. "Arm/Hand")
     * @param propertyId an {@link AnimatedProperty} id
     */
    public AnimationTrack(String targetPath, String propertyId, AnimationCurve curve) {
        this.targetPath = Objects.requireNonNull(targetPath, "targetPath cannot be null");
        this.propertyId = Objects.requireNonNull(propertyId, "propertyId cannot be null");
        if (propertyId.isBlank()) {
            throw new IllegalArgumentException("propertyId cannot be blank");
        }
        this.curve = Objects.requireNonNull(curve, "curve cannot be null");
    }

    public String getTargetPath() {
        return targetPath;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public AnimationCurve getCurve() {
        return curve;
    }

    public boolean targets(String path, String property) {
        return targetPath.equals(path) && propertyId.equals(property);
    }

    /** The entity this track animates when the clip plays on {@code root}, or null if no such child exists. */
    public Entity resolveTarget(Entity root) {
        return resolve(root, targetPath);
    }

    /** Follows child names from {@code root}; the first child with each name is used. */
    public static Entity resolve(Entity root, String path) {
        Objects.requireNonNull(root, "root cannot be null");
        if (path.isEmpty()) {
            return root;
        }
        Entity current = root;
        for (String name : path.split(PATH_SEPARATOR, -1)) {
            Entity next = null;
            for (Transform child : current.getTransform().getChildren()) {
                if (child.getEntity().getName().equals(name)) {
                    next = child.getEntity();
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            current = next;
        }
        return current;
    }

    /**
     * Path from {@code root} to {@code target} for {@link #resolve}, or null if {@code target} is not {@code root}
     * or below it.
     */
    public static String pathOf(Entity root, Entity target) {
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        StringBuilder path = new StringBuilder();
        Entity current = target;
        while (current != root) {
            Transform parent = current.getTransform().getParent();
            if (parent == null) {
                return null;
            }
            path.insert(0, path.isEmpty() ? current.getName() : current.getName() + PATH_SEPARATOR);
            current = parent.getEntity();
        }
        return path.toString();
    }

    public AnimationTrack copy() {
        return new AnimationTrack(targetPath, propertyId, curve.copy());
    }
}
