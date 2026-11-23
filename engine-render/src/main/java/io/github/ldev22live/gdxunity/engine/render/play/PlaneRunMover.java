package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.play.GameTestProfile;
import io.github.ldev22live.gdxunity.engine.scene.play.InputAction;
import io.github.ldev22live.gdxunity.engine.scene.play.InputMapping;
import io.github.ldev22live.gdxunity.engine.scene.play.InputSource;
import io.github.ldev22live.gdxunity.engine.scene.play.RuntimeOnlyComponent;

import java.util.Objects;

/**
 * The Platformer + 2.5D movement a genre profile puts on the player of the Play copy (TD-088): left and right run along x, the jump
 * key jumps from the ground, and the player is held at its starting depth on the play plane
 * ({@link PhysicsSystem3D#lockCharacterDepth}). It drives the player's Character Controller 3D, so the level's colliders and terrain
 * stop it. Never saved.
 *
 * <p>The scene updates components after physics, so what is set here is used by the next physics step, as for scripts.
 */
public class PlaneRunMover extends BaseComponent implements RuntimeOnlyComponent {
    private final GameTestProfile.MovementSetup movement;
    private final InputMapping keys;
    private final InputSource input;
    private final Vector3 velocity = new Vector3();
    private boolean onPlane;
    private boolean jumpHeld;
    private int jumps;

    public PlaneRunMover(GameTestProfile.MovementSetup movement, InputMapping keys, InputSource input) {
        this.movement = Objects.requireNonNull(movement, "movement cannot be null");
        this.keys = Objects.requireNonNull(keys, "keys cannot be null");
        this.input = Objects.requireNonNull(input, "input cannot be null");
        if (movement.kind() != GameTestProfile.MovementSetup.Kind.PLANE_RUN) {
            throw new IllegalArgumentException("A PlaneRunMover runs PLANE_RUN movement, not " + movement.kind());
        }
    }

    /**
     * @throws IllegalStateException when the scene has no 3D physics, or the player is not a character
     */
    @Override
    public void onUpdate(float deltaTime) {
        PhysicsSystem3D physics = getEntity().getScene() != null ? getEntity().getScene().getPhysics3D() : null;
        if (physics == null) {
            throw new IllegalStateException("'" + getEntity().getName() + "' runs with the Platformer + 2.5D profile, which needs 3D "
                + "physics: give it a Character Controller 3D");
        }
        if (!onPlane) {
            physics.lockCharacterDepth(getEntity(), getEntity().getTransform().getWorldPosition().z);
            onPlane = true;
        }
        float direction = (keys.isActive(InputAction.MOVE_RIGHT, input) ? 1f : 0f) - (keys.isActive(InputAction.MOVE_LEFT, input) ? 1f : 0f);
        physics.moveCharacter(getEntity(), velocity.set(direction * movement.speed(), 0f, 0f));
        boolean jumpPressed = keys.isActive(InputAction.JUMP, input);
        // One jump per press: holding the key does not jump again on landing
        if (jumpPressed && !jumpHeld && physics.jumpCharacter(getEntity(), movement.jumpSpeed())) {
            jumps++;
        }
        jumpHeld = jumpPressed;
    }

    /** How many jumps started since Play began. */
    public int getJumps() {
        return jumps;
    }
}
