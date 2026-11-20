package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.play.RuntimeOnlyComponent;

import java.util.Objects;

/**
 * A genre rule a profile puts on the player of the Play copy (roadmap M3b.7, Platformer + 3D): when the player falls more than
 * {@code depth} below where Play started it (the spawn point when the scene has one), it is put back there at once, facing the way
 * it started, with its fall speed cleared ({@link PhysicsSystem3D#teleportCharacter}). It works whether the profile or the author's
 * own script moves the player. Never saved.
 *
 * <p>The start is taken when the profile is applied, after the spawn point has placed the player, not on the first update, so the
 * first physics step cannot move it. The rotation is written as the player's local rotation, so the player is expected at the
 * scene's root, as for {@link GroundWalkMover}.
 */
public class FallRespawner extends BaseComponent implements RuntimeOnlyComponent {
    private final float depth;
    private final Vector3 start;
    private final Quaternion startRotation;
    private int respawns;

    /**
     * @param depth         how far below {@code start} the player may fall, in scene units
     * @param start         where to put the player back: its centre, in world space
     * @param startRotation the way it faces there
     * @throws IllegalArgumentException when {@code depth} is not above 0 or a value is not finite
     */
    public FallRespawner(float depth, Vector3 start, Quaternion startRotation) {
        if (!(depth > 0f) || !Float.isFinite(depth)) {
            throw new IllegalArgumentException("depth must be above 0: " + depth);
        }
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(startRotation, "startRotation cannot be null");
        if (!Float.isFinite(start.x) || !Float.isFinite(start.y) || !Float.isFinite(start.z)) {
            throw new IllegalArgumentException("start must be finite: " + start);
        }
        this.depth = depth;
        this.start = new Vector3(start);
        this.startRotation = new Quaternion(startRotation);
    }

    /**
     * @throws IllegalStateException when the scene has no 3D physics, or the player is not a character
     */
    @Override
    public void onUpdate(float deltaTime) {
        Scene scene = getEntity().getScene();
        PhysicsSystem3D physics = scene != null ? scene.getPhysics3D() : null;
        if (physics == null) {
            throw new IllegalStateException("'" + getEntity().getName() + "' is put back after a fall, which needs 3D physics: give it "
                + "a Character Controller 3D");
        }
        if (getEntity().getTransform().getWorldPosition().y >= start.y - depth) {
            return;
        }
        physics.teleportCharacter(getEntity(), start);
        getEntity().getTransform().setLocalRotation(startRotation);
        GroundWalkMover mover = getEntity().getComponent(GroundWalkMover.class);
        if (mover != null) {
            mover.faceCurrentRotation();
        }
        respawns++;
    }

    /** How many times the player was put back since Play began. */
    public int getRespawns() {
        return respawns;
    }

    /** How far below the start the player may fall, in scene units. */
    public float getDepth() {
        return depth;
    }

    /** Where the player is put back: a copy of its starting centre, in world space. */
    public Vector3 getStart() {
        return new Vector3(start);
    }
}
