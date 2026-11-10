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
 * @param points      x, y, z of the vertices (HULL: the points around which the hull is built; MESH: the mesh vertices),
 *                    in the body's local frame before {@code offset}; empty for the other kinds
 * @param indices     three vertex numbers per triangle (MESH only, otherwise empty)
 */
public record ShapeDescriptor3D(Kind kind, Vector3 halfExtents, float radius, float halfHeight, Vector3 offset,
                                float[] points, int[] indices) {

    public enum Kind {
        BOX,
        SPHERE,
        CAPSULE,
        /** Convex hull of {@code points} (TD-052); any motion type. */
        HULL,
        /** Triangle mesh (TD-052); static bodies only. */
        MESH
    }

    public ShapeDescriptor3D {
        Objects.requireNonNull(kind, "kind cannot be null");
        halfExtents = new Vector3(Objects.requireNonNull(halfExtents, "halfExtents cannot be null"));
        offset = new Vector3(Objects.requireNonNull(offset, "offset cannot be null"));
        points = Objects.requireNonNull(points, "points cannot be null").clone();
        indices = Objects.requireNonNull(indices, "indices cannot be null").clone();
    }

    /** A primitive shape: no vertex data. */
    public ShapeDescriptor3D(Kind kind, Vector3 halfExtents, float radius, float halfHeight, Vector3 offset) {
        this(kind, halfExtents, radius, halfHeight, offset, new float[0], new int[0]);
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

    /** Convex hull around {@code points} (x, y, z each). */
    public static ShapeDescriptor3D hull(float[] points, Vector3 offset) {
        return new ShapeDescriptor3D(Kind.HULL, Vector3.Zero, 0f, 0f, offset, points, new int[0]);
    }

    /** Triangle mesh of {@code points} (x, y, z each) and {@code indices} (three per triangle). */
    public static ShapeDescriptor3D mesh(float[] points, int[] indices, Vector3 offset) {
        return new ShapeDescriptor3D(Kind.MESH, Vector3.Zero, 0f, 0f, offset, points, indices);
    }

    @Override
    public float[] points() {
        return points.clone();
    }

    @Override
    public int[] indices() {
        return indices.clone();
    }
}
