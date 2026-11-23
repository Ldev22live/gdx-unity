package samples.common;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * Movement for 2.5D games: a box that moves in the XY plane (the side view), falls under gravity and collides with
 * solid boxes. The level is 3D, but gameplay happens at depth z = 0, which is what makes it 2.5D.
 *
 * <p>Not a script by itself: the game scripts create one {@code SideBody} per moving thing (player, crates) and call
 * {@link #step} once per frame after {@link Solid#refresh() refreshing} the solids.
 */
public final class SideBody {
    /** Fastest fall speed, so bodies never tunnel through thin platforms. */
    public static final float MAX_FALL_SPEED = 1500f;
    /** Boxes that only touch do not count as overlapping (protects against rounding while standing). */
    public static final float SKIN = 0.01f;

    /** A solid box in the side view, sized by its entity's Mesh Primitive. It may move; inactive entities are not solid. */
    public static final class Solid {
        public final Entity entity;
        public final float halfWidth;
        public final float halfHeight;
        /** Centre and the movement since the previous {@link #refresh()}. */
        public float x;
        public float y;
        public float dx;
        public float dy;

        public Solid(Entity entity) {
            this.entity = entity;
            MeshPrimitiveComponent mesh = entity.getComponent(MeshPrimitiveComponent.class);
            if (mesh == null) {
                throw new IllegalStateException("'" + entity.getName() + "' needs a Mesh Primitive to be solid");
            }
            Vector3 scale = entity.getTransform().getWorldScale();
            halfWidth = mesh.getSize().x * scale.x / 2f;
            halfHeight = mesh.getSize().y * scale.y / 2f;
            Vector3 position = entity.getTransform().getWorldPosition();
            x = position.x;
            y = position.y;
        }

        /** Reads the entity's current position; call once per frame after moving the entity. */
        public void refresh() {
            Vector3 position = entity.getTransform().getWorldPosition();
            dx = position.x - x;
            dy = position.y - y;
            x = position.x;
            y = position.y;
        }

        public boolean isSolid() {
            return entity.isActiveInHierarchy();
        }

        public boolean overlaps(float px, float py, float phw, float phh) {
            return Math.abs(px - x) < phw + halfWidth - SKIN && Math.abs(py - y) < phh + halfHeight - SKIN;
        }
    }

    /** Decides what happens when a body walks into a solid; return true if the solid was moved out of the way. */
    public interface Pusher {
        boolean push(Solid solid, float amount);
    }

    /** Solids of all entities whose name starts with one of the prefixes, in scene order. */
    public static List<Solid> solidsNamed(Scene scene, String... prefixes) {
        List<Solid> solids = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            for (String prefix : prefixes) {
                if (entity.getName().startsWith(prefix)) {
                    solids.add(new Solid(entity));
                    break;
                }
            }
        }
        return solids;
    }

    public float x;
    public float y;
    public final float halfWidth;
    public final float halfHeight;
    public float velocityX;
    public float velocityY;
    public boolean grounded;
    /** The solid stood on, or null; moving platforms carry the body along. */
    public Solid ground;
    /** This body's own solid when others can stand on it (crates); skipped in its own collisions. */
    public Solid self;

    public SideBody(float x, float y, float halfWidth, float halfHeight) {
        this.x = x;
        this.y = y;
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
    }

    /**
     * One frame: ride the platform stood on, apply gravity, then move and collide horizontally and vertically.
     *
     * @param pusher handles walking into solids, or null when nothing can be pushed
     */
    public void step(float deltaTime, float gravity, List<Solid> solids, Pusher pusher) {
        if (grounded && ground != null && ground.isSolid()) {
            x += ground.dx;
            y += ground.dy;
        }
        velocityY = Math.max(velocityY - gravity * deltaTime, -MAX_FALL_SPEED);
        moveX(velocityX * deltaTime, solids, pusher);
        moveY(velocityY * deltaTime, solids);
    }

    /** Moves sideways; stops at solids unless {@code pusher} moves them away. */
    public void moveX(float amount, List<Solid> solids, Pusher pusher) {
        if (amount == 0f) {
            return;
        }
        x += amount;
        for (Solid solid : solids) {
            if (solid == self || !solid.isSolid() || !solid.overlaps(x, y, halfWidth, halfHeight)) {
                continue;
            }
            if (pusher != null && pusher.push(solid, amount) && !solid.overlaps(x, y, halfWidth, halfHeight)) {
                continue;
            }
            x = amount > 0f ? solid.x - solid.halfWidth - halfWidth : solid.x + solid.halfWidth + halfWidth;
        }
    }

    /** Moves up or down; landing sets {@link #grounded} and {@link #ground}, hitting a ceiling stops the jump. */
    public void moveY(float amount, List<Solid> solids) {
        y += amount;
        grounded = false;
        ground = null;
        for (Solid solid : solids) {
            if (solid == self || !solid.isSolid() || !solid.overlaps(x, y, halfWidth, halfHeight)) {
                continue;
            }
            if (amount <= 0f) {
                y = solid.y + solid.halfHeight + halfHeight;
                grounded = true;
                ground = solid;
            } else {
                y = solid.y - solid.halfHeight - halfHeight;
            }
            velocityY = 0f;
        }
    }

    /** True when this body's box overlaps the given box. */
    public boolean touches(float boxX, float boxY, float boxHalfWidth, float boxHalfHeight) {
        return Math.abs(x - boxX) < halfWidth + boxHalfWidth && Math.abs(y - boxY) < halfHeight + boxHalfHeight;
    }

    /** Moves {@code entity} to this body's position at depth {@code z}. */
    public void moveEntity(Entity entity, float z) {
        entity.getTransform().setLocalPosition(x, y, z);
    }
}
