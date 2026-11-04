package samples.runner;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/**
 * Runner player: A/D or the arrow keys switch between three lanes, Space jumps. The world moves towards the player
 * (see {@link RunnerGame}), so the player only moves sideways and up.
 */
public class RunnerPlayer extends ScriptComponent {
    /** X position of the left, middle and right lane. */
    static final float[] LANES = {-130f, 0f, 130f};
    /** Sideways speed while changing lanes. */
    static final float LANE_CHANGE_SPEED = 1100f;
    static final float JUMP_SPEED = 760f;
    static final float GRAVITY = 2400f;

    private float groundY;
    private float z;
    private int lane = 1;
    private float x;
    private float y;
    private float velocityY;

    @Override
    public void onStart() {
        super.onStart();
        Vector3 start = getTransform().getLocalPosition();
        groundY = start.y;
        z = start.z;
        x = LANES[lane];
        y = groundY;
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            lane = 1;
            x = LANES[lane];
            y = groundY;
            velocityY = 0f;
        }
        if ((Gdx.input.isKeyJustPressed(Keys.A) || Gdx.input.isKeyJustPressed(Keys.LEFT)) && lane > 0) lane--;
        if ((Gdx.input.isKeyJustPressed(Keys.D) || Gdx.input.isKeyJustPressed(Keys.RIGHT)) && lane < LANES.length - 1) lane++;
        boolean onGround = y <= groundY;
        if (onGround && (Gdx.input.isKeyJustPressed(Keys.SPACE) || Gdx.input.isKeyJustPressed(Keys.W)
            || Gdx.input.isKeyJustPressed(Keys.UP))) {
            velocityY = JUMP_SPEED;
        }

        float targetX = LANES[lane];
        float step = LANE_CHANGE_SPEED * deltaTime;
        x = Math.abs(targetX - x) <= step ? targetX : x + Math.signum(targetX - x) * step;
        velocityY -= GRAVITY * deltaTime;
        y += velocityY * deltaTime;
        if (y < groundY) {
            y = groundY;
            velocityY = 0f;
        }
        getTransform().setLocalPosition(x, y, z);
    }
}
