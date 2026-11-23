package samples.fps;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * The solid parts of the arena, read from the scene: active boxes whose names start with "Wall", "Crate" or
 * "Pillar". They block movement and shots. Boxes are used as they are placed, without rotation, so keep solid
 * boxes axis-aligned.
 *
 * <p>Not a script by itself: {@link FpsGame} builds one when the game starts.
 */
public final class Arena {
    /** Name prefixes of the solid boxes. */
    public static final String[] SOLID_PREFIXES = {"Wall", "Crate", "Pillar"};
    /** Returned by {@link #raycast} when nothing solid is in the way. */
    public static final float NO_HIT = Float.MAX_VALUE;
    /** A ray direction component smaller than this counts as parallel to that pair of box faces. */
    private static final float PARALLEL = 1e-6f;

    private final List<Vector3> centres = new ArrayList<>();
    private final List<Vector3> halfSizes = new ArrayList<>();

    private Arena() {
    }

    public static Arena fromScene(Scene scene) {
        Arena arena = new Arena();
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy() || !isSolidName(entity.getName())) {
                continue;
            }
            MeshPrimitiveComponent mesh = entity.getComponent(MeshPrimitiveComponent.class);
            if (mesh == null) {
                throw new IllegalStateException("'" + entity.getName() + "' needs a Mesh Primitive (a box) to be solid");
            }
            arena.centres.add(entity.getTransform().getWorldPosition());
            arena.halfSizes.add(mesh.getSize().scl(0.5f));
        }
        return arena;
    }

    private static boolean isSolidName(String name) {
        for (String prefix : SOLID_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves a body standing on the floor by (dx, dz) and stops it at solid boxes. The body is a square of
     * {@code radius} around {@code position}; X and Z are resolved one after the other, so bodies slide along walls.
     */
    public void move(Vector3 position, float dx, float dz, float radius) {
        position.x += dx;
        for (int i = 0; i < centres.size(); i++) {
            if (overlaps(i, position, radius)) {
                Vector3 c = centres.get(i);
                Vector3 h = halfSizes.get(i);
                position.x = dx > 0f ? c.x - h.x - radius : c.x + h.x + radius;
            }
        }
        position.z += dz;
        for (int i = 0; i < centres.size(); i++) {
            if (overlaps(i, position, radius)) {
                Vector3 c = centres.get(i);
                Vector3 h = halfSizes.get(i);
                position.z = dz > 0f ? c.z - h.z - radius : c.z + h.z + radius;
            }
        }
    }

    private boolean overlaps(int i, Vector3 position, float radius) {
        Vector3 c = centres.get(i);
        Vector3 h = halfSizes.get(i);
        return Math.abs(position.x - c.x) < h.x + radius && Math.abs(position.z - c.z) < h.z + radius;
    }

    /** Distance along the unit ray to the nearest solid box, or {@link #NO_HIT}. */
    public float raycast(Vector3 origin, Vector3 direction) {
        float nearest = NO_HIT;
        for (int i = 0; i < centres.size(); i++) {
            nearest = Math.min(nearest, rayBox(origin, direction, centres.get(i), halfSizes.get(i)));
        }
        return nearest;
    }

    /** Distance along the unit ray to an axis-aligned box, or {@link #NO_HIT}; 0 when the origin is inside it. */
    public static float rayBox(Vector3 origin, Vector3 direction, Vector3 centre, Vector3 half) {
        // Slab test: the ray is inside the box where it is between all three pairs of parallel faces
        float near = 0f;
        float far = NO_HIT;
        for (int axis = 0; axis < 3; axis++) {
            float o = component(origin, axis);
            float d = component(direction, axis);
            float c = component(centre, axis);
            float h = component(half, axis);
            if (Math.abs(d) < PARALLEL) {
                if (Math.abs(o - c) > h) {
                    return NO_HIT;
                }
                continue;
            }
            float t1 = (c - h - o) / d;
            float t2 = (c + h - o) / d;
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) {
                return NO_HIT;
            }
        }
        return near;
    }

    private static float component(Vector3 v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }
}
