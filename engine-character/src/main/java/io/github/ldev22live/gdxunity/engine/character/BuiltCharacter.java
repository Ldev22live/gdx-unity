package io.github.ldev22live.gdxunity.engine.character;

import java.util.List;

/**
 * A character ready to draw or write out (TD-053): the morphed body in meters standing on y = 0, its skeleton at rest and the
 * skin. Arrays are the builder's own; treat them as read-only.
 *
 * @param positions   x, y, z of every drawn vertex, meters
 * @param normals     smooth normals, one per drawn vertex
 * @param uvs         u, v per drawn vertex
 * @param triangles   three drawn-vertex numbers per triangle
 * @param joints      four bone numbers per drawn vertex (unused slots are bone 0 with weight 0)
 * @param weights     four weights per drawn vertex, adding up to 1
 * @param bones       the rig, parents before children
 * @param jointHeads  x, y, z of every bone's head at rest, meters (world space)
 * @param height      height of the body in meters
 */
public record BuiltCharacter(float[] positions, float[] normals, float[] uvs, int[] triangles, int[] joints, float[] weights,
                             List<CharacterData.Bone> bones, float[] jointHeads, float height) {

    public int vertexCount() {
        return positions.length / 3;
    }

    public int triangleCount() {
        return triangles.length / 3;
    }

    /** World position of bone {@code index} at rest. */
    public float[] jointHead(int index) {
        return new float[] {jointHeads[index * 3], jointHeads[index * 3 + 1], jointHeads[index * 3 + 2]};
    }
}
