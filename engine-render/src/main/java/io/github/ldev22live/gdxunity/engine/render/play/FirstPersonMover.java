package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.play.GameTestProfile;
import io.github.ldev22live.gdxunity.engine.scene.play.InputAction;
import io.github.ldev22live.gdxunity.engine.scene.play.InputMapping;
import io.github.ldev22live.gdxunity.engine.scene.play.InputSource;
import io.github.ldev22live.gdxunity.engine.scene.play.RuntimeOnlyComponent;

import java.util.Objects;

/**
 * The First-person Shooter + 3D movement a genre profile puts on the player of the Play copy (roadmap M3b.7): the turn keys turn
 * the player at the profile's turn rate, forward and back walk the way it faces, left and right step sideways without turning, and
 * the jump key jumps from the ground once per press. The first-person camera looks the way the player faces, so turning the player
 * turns the view. It drives the player's Character Controller 3D (TD-049), which leaves rotation to the game. Never saved.
 *
 * <p>The turn is written as the player's local rotation, so the player is expected at the scene's root, as for
 * {@link GroundWalkMover}.
 */
public class FirstPersonMover extends BaseComponent implements RuntimeOnlyComponent {
    private final GameTestProfile.MovementSetup movement;
    private final InputMapping keys;
    private final InputSource input;
    private final Vector3 direction = new Vector3();
    private final Vector3 velocity = new Vector3();
    private final Vector3 tmpForward = new Vector3();
    private boolean facingKnown;
    private float facingYaw;
    private boolean jumpHeld;
    private int jumps;

    public FirstPersonMover(GameTestProfile.MovementSetup movement, InputMapping keys, InputSource input) {
        this.movement = Objects.requireNonNull(movement, "movement cannot be null");
        this.keys = Objects.requireNonNull(keys, "keys cannot be null");
        this.input = Objects.requireNonNull(input, "input cannot be null");
        if (movement.kind() != GameTestProfile.MovementSetup.Kind.FIRST_PERSON_WALK) {
            throw new IllegalArgumentException("A FirstPersonMover runs FIRST_PERSON_WALK movement, not " + movement.kind());
        }
    }

    /**
     * @throws IllegalStateException when the scene has no 3D physics, or the player is not a character
     */
    @Override
    public void onUpdate(float deltaTime) {
        Scene scene = getEntity().getScene();
        PhysicsSystem3D physics = scene != null ? scene.getPhysics3D() : null;
        if (physics == null) {
            throw new IllegalStateException("'" + getEntity().getName() + "' walks with the First-person Shooter + 3D profile, which "
                + "needs 3D physics: give it a Character Controller 3D");
        }
        if (!facingKnown) {
            Vector3 facing = getEntity().getTransform().getWorldRotation().transform(CameraRigComponent.forward(0f, 0f, tmpForward));
            facingYaw = facing.x * facing.x + facing.z * facing.z > 1e-6f ? CameraRigComponent.yawOf(facing) : 0f;
            facingKnown = true;
        }
        float turn = (keys.isActive(InputAction.TURN_LEFT, input) ? 1f : 0f) - (keys.isActive(InputAction.TURN_RIGHT, input) ? 1f : 0f);
        if (turn != 0f) {
            // Positive yaw turns left, as for camera rigs
            facingYaw = GroundWalkMover.turnToward(facingYaw, facingYaw + turn * 90f, movement.turnDegreesPerSec() * deltaTime);
            getEntity().getTransform().setLocalEulerAngles(facingYaw, 0f, 0f);
        }
        float right = (keys.isActive(InputAction.MOVE_RIGHT, input) ? 1f : 0f) - (keys.isActive(InputAction.MOVE_LEFT, input) ? 1f : 0f);
        float ahead = (keys.isActive(InputAction.MOVE_FORWARD, input) ? 1f : 0f) - (keys.isActive(InputAction.MOVE_BACK, input) ? 1f : 0f);
        GroundWalkMover.walkDirection(facingYaw, right, ahead, direction);
        physics.moveCharacter(getEntity(), velocity.set(direction).scl(movement.speed()));
        boolean jumpPressed = keys.isActive(InputAction.JUMP, input);
        // One jump per press: holding the key does not jump again on landing
        if (jumpPressed && !jumpHeld && physics.jumpCharacter(getEntity(), movement.jumpSpeed())) {
            jumps++;
        }
        jumpHeld = jumpPressed;
    }

    /** The yaw the player faces, in degrees (yaw 0 faces -Z, positive turns left, as for camera rigs). */
    public float getFacingYaw() {
        return facingYaw;
    }

    /** How many jumps started since Play began. */
    public int getJumps() {
        return jumps;
    }
}
