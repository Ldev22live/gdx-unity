package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/**
 * One collision shape handed to a {@link PhysicsBackend3D}, in meters with the entity scale already applied.
 *
 * @param halfExtents box half sizes (BOX only, otherwise zero)
 * @param radius      sphere or capsule radius (zero for BOX)
 * @param halfHeight  half the capsule's cylinder height, along the body's Y axis (CAPSULE only)
 * @param offset      shape center in the body's local frame
 */
public record ShapeDescriptor3D(Kind kind, Vector3 halfExtents, float radius, float halfHeight, Vector3 offset) {

    public enum Kind {
        BOX,
        SPHERE,
        CAPSULE
    }

    public ShapeDescriptor3D {
        Objects.requireNonNull(kind, "kind cannot be null");
        halfExtents = new Vector3(Objects.requireNonNull(halfExtents, "halfExtents cannot be null"));
        offset = new Vector3(Objects.requireNonNull(offset, "offset cannot be null"));
    }

    public static ShapeDescriptor3D box(Vector3 halfExtents, Vector3 offset) {
        return new ShapeDescriptor3D(Kind.BOX, halfExtents, 0f, 0f, offset);
    }

    public static ShapeDescriptor3D sphere(float radius, Vector3 offset) {
        return new ShapeDescriptor3D(Kind.SPHERE, Vector3.Zero, radius, 0f, offset);
    }

    public static ShapeDescriptor3D capsule(float radius, float halfHeight, Vector3 offset) {
        return new ShapeDescriptor3D(Kind.CAPSULE, Vector3.Zero, radius, halfHeight, offset);
    }
}
