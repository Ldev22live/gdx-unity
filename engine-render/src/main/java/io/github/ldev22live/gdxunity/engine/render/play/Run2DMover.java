package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.play.GameTestProfile;
import io.github.ldev22live.gdxunity.engine.scene.play.InputAction;
import io.github.ldev22live.gdxunity.engine.scene.play.InputMapping;
import io.github.ldev22live.gdxunity.engine.scene.play.InputSource;
import io.github.ldev22live.gdxunity.engine.scene.play.RuntimeOnlyComponent;

import java.util.Objects;

/**
 * The Platformer + 2D movement a genre profile puts on the player of the Play copy (TD-105): left and right set the horizontal
 * velocity of the player's dynamic Rigid Body 2D, and the jump key sets its upward velocity when {@link PhysicsSystem2D#isGrounded}
 * finds ground under it. Box2D's gravity and the level's 2D colliders do the rest; the vertical velocity is otherwise left alone, so
 * the player falls and lands as a body does. The runtime body's rotation is fixed, so the player stays upright. Never saved.
 */
public class Run2DMover extends BaseComponent implements RuntimeOnlyComponent {
    private final GameTestProfile.MovementSetup movement;
    private final InputMapping keys;
    private final InputSource input;
    private final Vector2 velocity = new Vector2();
    private boolean upright;
    private boolean jumpHeld;
    private int jumps;

    public Run2DMover(GameTestProfile.MovementSetup movement, InputMapping keys, InputSource input) {
        this.movement = Objects.requireNonNull(movement, "movement cannot be null");
        this.keys = Objects.requireNonNull(keys, "keys cannot be null");
        this.input = Objects.requireNonNull(input, "input cannot be null");
        if (movement.kind() != GameTestProfile.MovementSetup.Kind.RUN_2D) {
            throw new IllegalArgumentException("A Run2DMover runs RUN_2D movement, not " + movement.kind());
        }
    }

    /**
     * @throws IllegalStateException when the scene has no 2D physics, or the player has no 2D body
     */
    @Override
    public void onUpdate(float deltaTime) {
        PhysicsSystem2D physics = getEntity().getScene() != null ? getEntity().getScene().getPhysics2D() : null;
        if (physics == null || physics.getBody(getEntity()) == null) {
            throw new IllegalStateException("'" + getEntity().getName() + "' runs with the Platformer + 2D profile, which needs 2D "
                + "physics: give it a Rigid Body 2D and a 2D collider");
        }
        if (!upright) {
            physics.getBody(getEntity()).setFixedRotation(true);
            upright = true;
        }
        float direction = (keys.isActive(InputAction.MOVE_RIGHT, input) ? 1f : 0f) - (keys.isActive(InputAction.MOVE_LEFT, input) ? 1f : 0f);
        physics.getLinearVelocity(getEntity(), velocity);
        float vertical = velocity.y;
        boolean jumpPressed = keys.isActive(InputAction.JUMP, input);
        // One jump per press: holding the key does not jump again on landing
        if (jumpPressed && !jumpHeld && physics.isGrounded(getEntity())) {
            vertical = movement.jumpSpeed();
            jumps++;
        }
        jumpHeld = jumpPressed;
        physics.setLinearVelocity(getEntity(), direction * movement.speed(), vertical);
    }

    /** How many jumps started since Play began. */
    public int getJumps() {
        return jumps;
    }
}
