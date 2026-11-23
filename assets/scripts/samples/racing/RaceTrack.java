package samples.racing;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The circuit, read from the scene: entities named "Road 1", "Road 2", ... are straight road pieces, driven in that
 * order, and the last one joins back to the first. Each piece is a box turned only around Y. Its length (size Z) is
 * the straight plus one road width, so neighbouring pieces overlap at the corners and leave no gaps.
 *
 * <p>Not a script by itself: {@link RaceGame} builds one when the race starts. Move or turn the road pieces in the
 * editor and the race follows them.
 */
public final class RaceTrack {
    /** Name prefix of the road pieces; the number after it is the driving order. */
    public static final String ROAD_PREFIX = "Road ";

    /** One straight piece of road: from {@code start}, {@code length} units along {@code dir}. */
    public static final class Segment {
        public final float startX;
        public final float startZ;
        /** Unit direction of travel in the XZ plane. */
        public final float dirX;
        public final float dirZ;
        public final float length;
        public final float halfWidth;

        Segment(Entity road) {
            MeshPrimitiveComponent mesh = road.getComponent(MeshPrimitiveComponent.class);
            if (mesh == null) {
                throw new IllegalStateException("'" + road.getName() + "' needs a Mesh Primitive (a box)");
            }
            Vector3 size = mesh.getSize();
            Vector3 centre = road.getTransform().getWorldPosition();
            // A box looks along its local -Z axis; turned by yaw that is (-sin yaw, -cos yaw)
            float yaw = road.getTransform().getWorldRotation().getYaw() * MathUtils.degreesToRadians;
            dirX = -MathUtils.sin(yaw);
            dirZ = -MathUtils.cos(yaw);
            halfWidth = size.x / 2f;
            length = size.z - size.x;
            if (length <= 0f) {
                throw new IllegalStateException("'" + road.getName() + "' must be longer (size Z) than it is wide (size X)");
            }
            startX = centre.x - dirX * length / 2f;
            startZ = centre.z - dirZ * length / 2f;
        }

        /** How far along the straight a point is: 0 at its start, 1 at its end. */
        public float along(float x, float z) {
            return ((x - startX) * dirX + (z - startZ) * dirZ) / length;
        }

        /** Signed distance of a point from the centre line; positive is to the right of the direction of travel. */
        public float across(float x, float z) {
            return (x - startX) * -dirZ + (z - startZ) * dirX;
        }

        /** True when the point is on this piece's box, including the overlap past both ends. */
        public boolean contains(float x, float z) {
            float overhang = halfWidth / length;
            float t = along(x, z);
            return t >= -overhang && t <= 1f + overhang && Math.abs(across(x, z)) <= halfWidth;
        }
    }

    private final List<Segment> segments = new ArrayList<>();

    private RaceTrack() {
    }

    /** Reads the road pieces from the scene, in the order of their numbers. */
    public static RaceTrack fromScene(Scene scene) {
        List<Entity> roads = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            if (entity.getName().startsWith(ROAD_PREFIX)) {
                roads.add(entity);
            }
        }
        if (roads.size() < 3) {
            throw new IllegalStateException("A circuit needs at least 3 entities named 'Road 1', 'Road 2', ...; found "
                + roads.size());
        }
        roads.sort(Comparator.comparingInt(RaceTrack::roadNumber));
        RaceTrack track = new RaceTrack();
        for (Entity road : roads) {
            track.segments.add(new Segment(road));
        }
        return track;
    }

    private static int roadNumber(Entity road) {
        String number = road.getName().substring(ROAD_PREFIX.length()).trim();
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Road pieces are named 'Road <number>': '" + road.getName() + "'", e);
        }
    }

    public int size() {
        return segments.size();
    }

    public Segment segment(int index) {
        return segments.get(Math.floorMod(index, segments.size()));
    }

    /** True when the point is on any road piece; off the road is grass. */
    public boolean onRoad(float x, float z) {
        for (Segment segment : segments) {
            if (segment.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The point {@code distance} units further along the centre line than {@code t} on piece {@code index}, moved
     * {@code offset} units to the right. Used by the rival cars to pick where to steer.
     */
    public Vector2 pointAhead(int index, float t, float distance, float offset, Vector2 out) {
        Segment segment = segment(index);
        float remaining = MathUtils.clamp(t, 0f, 1f) * segment.length + distance;
        while (remaining > segment.length) {
            remaining -= segment.length;
            index++;
            segment = segment(index);
        }
        return out.set(segment.startX + segment.dirX * remaining - segment.dirZ * offset,
            segment.startZ + segment.dirZ * remaining + segment.dirX * offset);
    }
}
