package io.github.ldev22live.gdxunity.engine.scene.navigation;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CapsuleCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ColliderGeometrySource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightField3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightFieldSource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshGeometry3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Bakes a {@link NavMeshBoundsComponent}'s box into a {@link NavGrid3D} (roadmap M6.4, TD-092). Pure scene traversal, without a
 * graphics context or the physics engine:
 * <ol>
 *   <li><b>Solids.</b> Every enabled, non-trigger 3D collider of an active entity that does not move: no Rigid Body 3D, or a static
 *       one. Boxes (turned and scaled with their entity), spheres, capsules (as their box), mesh and convex hull colliders (as the box
 *       of their triangles, when the scene can read them) and terrain colliders (their height field, holes left open). Moving bodies,
 *       characters and agents are not obstacles.</li>
 *   <li><b>Floor.</b> At each cell's centre, a vertical line meets the solids in spans. The floor is the highest span top inside the
 *       box with {@link NavMeshBoundsComponent#getAgentHeight()} free above it; a cell without one has no floor.</li>
 *   <li><b>Connections.</b> Neighbouring floors connect when they differ by at most the climb: the step height, or one cell at the
 *       steepest slope, whichever is higher. A crate is therefore its own island, and the ground around it ends at its side.</li>
 *   <li><b>Clearance.</b> Cells closer than {@link NavMeshBoundsComponent#getAgentRadius()} to an edge (a missing or unconnected
 *       neighbour, or the box's side) are not walkable, measured along the floor.</li>
 * </ol>
 */
public final class NavMeshBaker {
    private static final float DIAGONAL = (float) Math.sqrt(2.0);

    /** A baked grid and what was left out, with reasons a person can act on. */
    public record Result(NavGrid3D grid, List<String> warnings) {
        public Result {
            Objects.requireNonNull(grid, "grid cannot be null");
            warnings = List.copyOf(warnings);
        }
    }

    private NavMeshBaker() {}

    /**
     * Bakes the box of {@code bounds} on {@code entity} in {@code scene}.
     *
     * @throws IllegalArgumentException when the grid would have more than {@link NavMeshBoundsComponent#MAX_CELLS} cells
     */
    public static Result bake(Scene scene, Entity entity, NavMeshBoundsComponent bounds) {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(bounds, "bounds cannot be null");
        Matrix4 world = entity.getTransform().getWorldMatrix();
        Vector3 center = world.getTranslation(new Vector3());
        Vector3 scale = world.getScale(new Vector3());
        Vector3 size = bounds.getSize().scl(Math.abs(scale.x), Math.abs(scale.y), Math.abs(scale.z));
        float cell = bounds.getCellSize();
        int columns = Math.max(1, (int) Math.ceil(size.x / cell));
        int rows = Math.max(1, (int) Math.ceil(size.z / cell));
        if ((long) columns * rows > NavMeshBoundsComponent.MAX_CELLS) {
            throw new IllegalArgumentException(entity.getName() + ": the Nav Mesh Bounds would need " + (long) columns * rows
                + " cells, more than " + NavMeshBoundsComponent.MAX_CELLS + "; make the box smaller or the cells larger");
        }
        float originX = center.x - columns * cell / 2f;
        float originZ = center.z - rows * cell / 2f;
        float minY = center.y - size.y / 2f;
        float maxY = center.y + size.y / 2f;
        List<String> warnings = new ArrayList<>();
        List<Solid> solids = collectSolids(scene, warnings);

        int count = columns * rows;
        float[] floor = new float[count];
        float[] spans = new float[16];
        for (int row = 0; row < rows; row++) {
            float z = originZ + (row + 0.5f) * cell;
            for (int column = 0; column < columns; column++) {
                float x = originX + (column + 0.5f) * cell;
                int found = 0;
                for (Solid solid : solids) {
                    if (x < solid.minX || x > solid.maxX || z < solid.minZ || z > solid.maxZ) continue;
                    if (found * 2 + 2 > spans.length) spans = Arrays.copyOf(spans, spans.length * 2);
                    if (solid.span(x, z, spans, found * 2)) found++;
                }
                floor[row * columns + column] = floorOf(spans, found, minY, maxY, bounds.getAgentHeight());
            }
        }
        float climb = Math.max(bounds.getStepHeight(), cell * (float) Math.tan(Math.toRadians(bounds.getMaxSlope())));
        boolean[] walkable = clearance(columns, rows, floor, climb, cell, bounds.getAgentRadius());
        return new Result(new NavGrid3D(columns, rows, originX, originZ, cell, climb, floor, walkable), warnings);
    }

    /** The highest span top within the box with enough room above it, or NaN. Spans are pairs {bottom, top}, in any order. */
    static float floorOf(float[] spans, int count, float minY, float maxY, float agentHeight) {
        float best = Float.NaN;
        for (int i = 0; i < count; i++) {
            float top = spans[i * 2 + 1];
            if (top < minY || top > maxY || (!Float.isNaN(best) && top <= best)) continue;
            boolean room = true;
            for (int j = 0; j < count && room; j++) {
                if (j == i) continue;
                float bottom = spans[j * 2];
                float otherTop = spans[j * 2 + 1];
                // Another solid overlapping the space just above this top (or covering the top itself) takes the room away
                room = !(otherTop > top && bottom < top + agentHeight);
            }
            if (room) best = top;
        }
        return best;
    }

    /**
     * Which cells keep {@code radius} from every edge: a distance along connected floors, by a chamfer walk from the edge cells (those
     * with a missing or unconnected neighbour), each counted half a cell from the edge itself.
     */
    static boolean[] clearance(int columns, int rows, float[] floor, float climb, float cell, float radius) {
        int count = columns * rows;
        boolean[] walkable = new boolean[count];
        float[] distance = new float[count];
        Arrays.fill(distance, Float.POSITIVE_INFINITY);
        // A ring of cells waiting to pass their distance on; each cell is in it at most once, so count + 1 places never overflow
        int capacity = count + 1;
        int[] ring = new int[capacity];
        boolean[] queued = new boolean[count];
        int head = 0;
        int tail = 0;
        for (int at = 0; at < count; at++) {
            if (!Float.isNaN(floor[at]) && isEdge(at, columns, rows, floor, climb)) {
                distance[at] = 0f;
                queued[at] = true;
                ring[tail++] = at;
            }
        }
        // Relaxation until nothing improves: distances only fall
        while (head != tail) {
            int at = ring[head];
            head = (head + 1) % capacity;
            queued[at] = false;
            int column = at % columns;
            int row = at / columns;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    int c = column + dx;
                    int r = row + dz;
                    if (c < 0 || r < 0 || c >= columns || r >= rows) continue;
                    int next = r * columns + c;
                    if (Float.isNaN(floor[next]) || Math.abs(floor[next] - floor[at]) > climb) continue;
                    float candidate = distance[at] + (dx != 0 && dz != 0 ? DIAGONAL : 1f) * cell;
                    if (candidate < distance[next]) {
                        distance[next] = candidate;
                        if (!queued[next]) {
                            queued[next] = true;
                            ring[tail] = next;
                            tail = (tail + 1) % capacity;
                        }
                    }
                }
            }
        }
        for (int at = 0; at < count; at++) {
            walkable[at] = !Float.isNaN(floor[at]) && distance[at] + cell * 0.5f >= radius;
        }
        return walkable;
    }

    private static boolean isEdge(int at, int columns, int rows, float[] floor, float climb) {
        int column = at % columns;
        int row = at / columns;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                int c = column + dx;
                int r = row + dz;
                if (c < 0 || r < 0 || c >= columns || r >= rows) return true;
                int next = r * columns + c;
                if (Float.isNaN(floor[next]) || Math.abs(floor[next] - floor[at]) > climb) return true;
            }
        }
        return false;
    }

    private static List<Solid> collectSolids(Scene scene, List<String> warnings) {
        List<Solid> solids = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy() || movesOrWalks(entity)) continue;
            Matrix4 world = entity.getTransform().getWorldMatrix();
            for (Component component : entity.getAllComponents()) {
                if (!component.isEnabled() || !(component instanceof Collider3DComponent collider) || collider.isTrigger()) continue;
                Vector3 offset = collider.getOffset();
                if (collider instanceof BoxCollider3DComponent box) {
                    Vector3 half = box.getSize().scl(0.5f);
                    solids.add(new BoxSolid(world, new Vector3(offset).sub(half), new Vector3(offset).add(half)));
                } else if (collider instanceof SphereCollider3DComponent sphere) {
                    solids.add(new SphereSolid(world, offset, sphere.getRadius()));
                } else if (collider instanceof CapsuleCollider3DComponent capsule) {
                    float r = capsule.getRadius();
                    float h = capsule.getHeight() / 2f + r;
                    solids.add(new BoxSolid(world, new Vector3(offset).sub(r, h, r), new Vector3(offset).add(r, h, r)));
                } else if (collider instanceof TerrainCollider3DComponent) {
                    HeightFieldSource source = scene.getHeightFields();
                    if (source == null) {
                        warnings.add(entity.getName() + ": its terrain could not be read here, so it is not part of the navigation");
                        continue;
                    }
                    try {
                        solids.add(new TerrainSolid(world, source.heightFieldOf(entity)));
                    } catch (IllegalStateException e) {
                        warnings.add(entity.getName() + ": " + e.getMessage());
                    }
                } else {
                    ColliderGeometrySource source = scene.getColliderGeometry();
                    MeshGeometry3D mesh = null;
                    if (source != null) {
                        try {
                            mesh = source.geometryOf(entity);
                        } catch (IllegalStateException e) {
                            warnings.add(entity.getName() + ": " + e.getMessage());
                            continue;
                        }
                    }
                    if (mesh == null || mesh.vertexCount() == 0) {
                        warnings.add(entity.getName() + ": its mesh collider could not be read here, so it is not an obstacle");
                        continue;
                    }
                    solids.add(meshBox(world, mesh));
                }
            }
        }
        return solids;
    }

    /** Whether the entity walks or can be moved by physics: then it is never an obstacle baked into the floor. */
    private static boolean movesOrWalks(Entity entity) {
        if (entity.getComponent(CharacterController3DComponent.class) != null || entity.getComponent(NavAgent3DComponent.class) != null) {
            return true;
        }
        RigidBody3DComponent body = entity.getComponent(RigidBody3DComponent.class);
        return body != null && body.isEnabled() && body.getMotionType() != RigidBody3DComponent.MotionType.STATIC;
    }

    private static BoxSolid meshBox(Matrix4 world, MeshGeometry3D mesh) {
        float[] positions = mesh.positions();
        Vector3 min = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        Vector3 max = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (int i = 0; i + 2 < positions.length; i += 3) {
            min.set(Math.min(min.x, positions[i]), Math.min(min.y, positions[i + 1]), Math.min(min.z, positions[i + 2]));
            max.set(Math.max(max.x, positions[i]), Math.max(max.y, positions[i + 1]), Math.max(max.z, positions[i + 2]));
        }
        return new BoxSolid(world, min, max);
    }

    /** Something solid: its box on the ground plane, and where a vertical line at x, z passes through it. */
    abstract static class Solid {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        /** Writes {bottom, top} of the line's way through this solid at {@code out[at]}; false when the line misses it. */
        abstract boolean span(float x, float z, float[] out, int at);

        void include(Vector3 point) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
        }
    }

    /** A box in its entity's space (turned and scaled with it): the line is taken into that space and clipped by the slabs. */
    static final class BoxSolid extends Solid {
        private final Matrix4 inverse;
        private final Vector3 min;
        private final Vector3 max;
        private final Vector3 origin = new Vector3();
        private final Vector3 direction = new Vector3();
        private final Vector3 corner = new Vector3();

        BoxSolid(Matrix4 world, Vector3 min, Vector3 max) {
            this.min = new Vector3(min);
            this.max = new Vector3(max);
            Matrix4 copy = new Matrix4(world);
            for (int i = 0; i < 8; i++) {
                include(corner.set((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z).mul(copy));
            }
            this.inverse = Math.abs(copy.det()) < Float.MIN_NORMAL ? null : copy.inv();
        }

        @Override
        boolean span(float x, float z, float[] out, int at) {
            if (inverse == null) return false;
            // Two points of the line y -> (x, y, z), taken into local space: a line there too
            origin.set(x, 0f, z).mul(inverse);
            direction.set(x, 1f, z).mul(inverse).sub(origin);
            float low = -Float.MAX_VALUE;
            float high = Float.MAX_VALUE;
            float[] o = {origin.x, origin.y, origin.z};
            float[] d = {direction.x, direction.y, direction.z};
            float[] lo = {min.x, min.y, min.z};
            float[] hi = {max.x, max.y, max.z};
            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(d[axis]) < 1e-9f) {
                    if (o[axis] < lo[axis] || o[axis] > hi[axis]) return false;
                    continue;
                }
                float t1 = (lo[axis] - o[axis]) / d[axis];
                float t2 = (hi[axis] - o[axis]) / d[axis];
                low = Math.max(low, Math.min(t1, t2));
                high = Math.min(high, Math.max(t1, t2));
                if (low > high) return false;
            }
            out[at] = low;
            out[at + 1] = high;
            return true;
        }
    }

    /** A sphere, its radius scaled by the entity's largest scale. */
    static final class SphereSolid extends Solid {
        private final Vector3 center;
        private final float radius;

        SphereSolid(Matrix4 world, Vector3 offset, float radius) {
            this.center = new Vector3(offset).mul(world);
            Vector3 scale = world.getScale(new Vector3());
            this.radius = radius * Math.max(Math.abs(scale.x), Math.max(Math.abs(scale.y), Math.abs(scale.z)));
            minX = center.x - this.radius;
            maxX = center.x + this.radius;
            minZ = center.z - this.radius;
            maxZ = center.z + this.radius;
        }

        @Override
        boolean span(float x, float z, float[] out, int at) {
            float dx = x - center.x;
            float dz = z - center.z;
            float h2 = radius * radius - dx * dx - dz * dz;
            if (h2 < 0f) return false;
            float h = (float) Math.sqrt(h2);
            out[at] = center.y - h;
            out[at + 1] = center.y + h;
            return true;
        }
    }

    /** A terrain's height field: everything below its surface is solid; a hole is open. */
    static final class TerrainSolid extends Solid {
        private final Matrix4 world;
        private final Matrix4 inverse;
        private final HeightField3D field;
        private final float[] heights;
        private final Vector3 point = new Vector3();

        TerrainSolid(Matrix4 world, HeightField3D field) {
            this.world = new Matrix4(world);
            this.inverse = new Matrix4(world).inv();
            this.field = field;
            this.heights = field.heights();
            float x0 = field.originX();
            float z0 = field.originZ();
            float x1 = x0 + (field.columns() - 1) * field.cellSizeX();
            float z1 = z0 + (field.rows() - 1) * field.cellSizeZ();
            for (int i = 0; i < 4; i++) {
                include(point.set((i & 1) == 0 ? x0 : x1, 0f, (i & 2) == 0 ? z0 : z1).mul(this.world));
            }
        }

        @Override
        boolean span(float x, float z, float[] out, int at) {
            point.set(x, 0f, z).mul(inverse);
            float gx = (point.x - field.originX()) / field.cellSizeX();
            float gz = (point.z - field.originZ()) / field.cellSizeZ();
            if (gx < 0f || gz < 0f || gx > field.columns() - 1 || gz > field.rows() - 1) {
                return false;
            }
            // The far edge belongs to the last cell
            int c = Math.min((int) gx, field.columns() - 2);
            int r = Math.min((int) gz, field.rows() - 2);
            int columns = field.columns();
            float h00 = heights[r * columns + c];
            float h10 = heights[r * columns + c + 1];
            float h01 = heights[(r + 1) * columns + c];
            float h11 = heights[(r + 1) * columns + c + 1];
            if (HeightField3D.isHole(h00) || HeightField3D.isHole(h10) || HeightField3D.isHole(h01) || HeightField3D.isHole(h11)) {
                return false;
            }
            float fx = gx - c;
            float fz = gz - r;
            float height = (h00 * (1 - fx) + h10 * fx) * (1 - fz) + (h01 * (1 - fx) + h11 * fx) * fz;
            point.set((float) (field.originX() + gx * field.cellSizeX()), height, (float) (field.originZ() + gz * field.cellSizeZ()))
                .mul(world);
            out[at] = -Float.MAX_VALUE;
            out[at + 1] = point.y;
            return true;
        }
    }
}
