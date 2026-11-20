package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
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
 * The Action RPG + 3D movement a genre profile puts on the player of the Play copy (TD-088): the move keys walk relative to the
 * way the game camera looks (forward is away from the camera), the jump key jumps from the ground once per press, and the player
 * turns to face where it walks at the profile's turn rate. It drives the player's Character Controller 3D, so terrain and colliders
 * hold it up and stop it, and slopes steeper than the controller's limit cannot be climbed. Turning the player here keeps TD-049's
 * rule that the controller leaves rotation to the game. Never saved.
 *
 * <p>The scene updates components after physics, so what is set here is used by the next physics step, as for scripts. The turn
 * is written as the player's local rotation, so the player is expected at the scene's root, like a camera rig's entity.
 */
public class GroundWalkMover extends BaseComponent implements RuntimeOnlyComponent {
    /** Below this squared length a horizontal direction counts as none. */
    private static final float NO_DIRECTION = 1e-6f;

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

    public GroundWalkMover(GameTestProfile.MovementSetup movement, InputMapping keys, InputSource input) {
        this.movement = Objects.requireNonNull(movement, "movement cannot be null");
        this.keys = Objects.requireNonNull(keys, "keys cannot be null");
        this.input = Objects.requireNonNull(input, "input cannot be null");
        if (movement.kind() != GameTestProfile.MovementSetup.Kind.GROUND_WALK) {
            throw new IllegalArgumentException("A GroundWalkMover runs GROUND_WALK movement, not " + movement.kind());
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
            throw new IllegalStateException("'" + getEntity().getName() + "' walks with the Action RPG + 3D profile, which needs 3D "
                + "physics: give it a Character Controller 3D");
        }
        if (!facingKnown) {
            facingYaw = yawFacing(getEntity().getTransform().getWorldRotation().transform(CameraRigComponent.forward(0f, 0f, tmpForward)),
                0f);
            facingKnown = true;
        }
        float right = (keys.isActive(InputAction.MOVE_RIGHT, input) ? 1f : 0f) - (keys.isActive(InputAction.MOVE_LEFT, input) ? 1f : 0f);
        float ahead = (keys.isActive(InputAction.MOVE_FORWARD, input) ? 1f : 0f) - (keys.isActive(InputAction.MOVE_BACK, input) ? 1f : 0f);
        walkDirection(viewYaw(scene), right, ahead, direction);
        physics.moveCharacter(getEntity(), velocity.set(direction).scl(movement.speed()));
        if (direction.len2() > NO_DIRECTION) {
            facingYaw = turnToward(facingYaw, CameraRigComponent.yawOf(direction), movement.turnDegreesPerSec() * deltaTime);
            getEntity().getTransform().setLocalEulerAngles(facingYaw, 0f, 0f);
        }
        boolean jumpPressed = keys.isActive(InputAction.JUMP, input);
        // One jump per press: holding the key does not jump again on landing
        if (jumpPressed && !jumpHeld && physics.jumpCharacter(getEntity(), movement.jumpSpeed())) {
            jumps++;
        }
        jumpHeld = jumpPressed;
    }

    /**
     * Takes the player's present rotation as the way it faces, as at the start of Play; for when something else turned it, such as
     * {@link FallRespawner} putting it back at the start.
     */
    public void faceCurrentRotation() {
        facingKnown = false;
    }

    /** How many jumps started since Play began. */
    public int getJumps() {
        return jumps;
    }

    /** The yaw the player faces, in degrees (yaw 0 faces -Z, positive turns left, as for camera rigs). */
    public float getFacingYaw() {
        return facingYaw;
    }

    /** The yaw the game camera looks along, so forward walks away from it; the player's own facing when there is no camera. */
    private float viewYaw(Scene scene) {
        CameraComponent camera = GameCameraRig.findActiveCamera(scene);
        if (camera == null) {
            return facingYaw;
        }
        return yawFacing(camera.getEntity().getTransform().getWorldRotation().transform(CameraRigComponent.forward(0f, 0f, tmpForward)),
            facingYaw);
    }

    private static float yawFacing(Vector3 forward, float whenVertical) {
        return forward.x * forward.x + forward.z * forward.z > NO_DIRECTION ? CameraRigComponent.yawOf(forward) : whenVertical;
    }

    /**
     * The horizontal unit direction to walk for move keys {@code right} and {@code ahead} (each -1, 0 or 1) seen from a camera
     * looking along {@code viewYawDegrees}; zero when no key is held or they cancel out.
     */
    public static Vector3 walkDirection(float viewYawDegrees, float right, float ahead, Vector3 out) {
        Objects.requireNonNull(out, "out cannot be null");
        float yaw = viewYawDegrees * MathUtils.degreesToRadians;
        float sin = MathUtils.sin(yaw);
        float cos = MathUtils.cos(yaw);
        // Forward is (-sin, 0, -cos) and right is (cos, 0, -sin), as CameraRigComponent.forward defines yaw
        out.set(-sin * ahead + cos * right, 0f, -cos * ahead - sin * right);
        return out.len2() > NO_DIRECTION ? out.nor() : out.setZero();
    }

    /** {@code from} turned toward {@code to} by at most {@code maxStep} degrees, the short way round, wrapped to -180..180. */
    public static float turnToward(float from, float to, float maxStep) {
        float difference = wrap(to - from);
        if (Math.abs(difference) <= maxStep) {
            return wrap(to);
        }
        return wrap(from + Math.signum(difference) * maxStep);
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped > 180f) {
            wrapped -= 360f;
        } else if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }
}
