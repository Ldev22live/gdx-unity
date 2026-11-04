package samples.collector;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/**
 * Rolls the player ball across the arena: WASD or the arrow keys accelerate it (W is away from the camera), it
 * slows down on its own and bounces off the arena edge. R puts it back at the start.
 */
public class CollectorPlayer extends ScriptComponent {
    /** Acceleration from the keys, in units per second squared. */
    static final float ACCELERATION = 1500f;
    /** Top speed in units per second. */
    static final float MAX_SPEED = 460f;
    /** Fraction of speed kept per second without input (rolling friction). */
    static final float DRAG = 0.15f;
    /** Half the arena's inner size minus the ball radius; the walls stand just outside. */
    static final float ARENA_HALF_SIZE = 740f;
    /** Share of speed kept when bouncing off a wall. */
    static final float WALL_BOUNCE = 0.6f;

    private final Vector3 start = new Vector3();
    private final Vector3 velocity = new Vector3();
    private final Vector3 position = new Vector3();

    @Override
    public void onStart() {
        super.onStart();
        start.set(getTransform().getLocalPosition());
        position.set(start);
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            position.set(start);
            velocity.setZero();
        }
        float inputX = 0f;
        float inputZ = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) inputX -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) inputX += 1f;
        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP)) inputZ -= 1f;
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN)) inputZ += 1f;

        velocity.x += inputX * ACCELERATION * deltaTime;
        velocity.z += inputZ * ACCELERATION * deltaTime;
        velocity.scl((float) Math.pow(DRAG, deltaTime)).limit(MAX_SPEED);
        position.mulAdd(velocity, deltaTime);

        if (Math.abs(position.x) > ARENA_HALF_SIZE) {
            position.x = Math.signum(position.x) * ARENA_HALF_SIZE;
            velocity.x = -velocity.x * WALL_BOUNCE;
        }
        if (Math.abs(position.z) > ARENA_HALF_SIZE) {
            position.z = Math.signum(position.z) * ARENA_HALF_SIZE;
            velocity.z = -velocity.z * WALL_BOUNCE;
        }
        getTransform().setLocalPosition(position);
    }
}
