package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import java.util.Objects;

/**
 * Triangles of a visible mesh, as read for a mesh collider (TD-052): vertex positions in the entity's local space in
 * scene units (before the entity's scale), and triangles as index triples into them.
 *
 * @param positions x, y, z of every vertex, one after the other
 * @param indices   three vertex numbers per triangle
 */
public record MeshGeometry3D(float[] positions, int[] indices) {

    public MeshGeometry3D {
        Objects.requireNonNull(positions, "positions cannot be null");
        Objects.requireNonNull(indices, "indices cannot be null");
        if (positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions must hold x, y and z per vertex, got " + positions.length + " numbers");
        }
        if (indices.length % 3 != 0) {
            throw new IllegalArgumentException("indices must hold three vertex numbers per triangle, got " + indices.length);
        }
        int vertices = positions.length / 3;
        for (int index : indices) {
            if (index < 0 || index >= vertices) {
                throw new IllegalArgumentException("triangle index " + index + " is outside the " + vertices + " vertices");
            }
        }
        positions = positions.clone();
        indices = indices.clone();
    }

    public int vertexCount() {
        return positions.length / 3;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    @Override
    public float[] positions() {
        return positions.clone();
    }

    @Override
    public int[] indices() {
        return indices.clone();
    }
}
