package samples.shooter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/** Flies the player's ship with WASD or the arrow keys, inside the lower part of the screen. */
public class ShooterShip extends ScriptComponent {
    /** Ship speed in units per second. */
    static final float SPEED = 340f;
    /** Area the ship may fly in (ship centre). */
    static final float MIN_X = 30f;
    static final float MAX_X = 610f;
    static final float MIN_Y = 40f;
    static final float MAX_Y = 260f;

    @Override
    public void onUpdate(float deltaTime) {
        float dx = 0f;
        float dy = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) dx -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) dx += 1f;
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN)) dy -= 1f;
        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP)) dy += 1f;
        Vector3 position = getTransform().getLocalPosition();
        getTransform().setLocalPosition(
            MathUtils.clamp(position.x + dx * SPEED * deltaTime, MIN_X, MAX_X),
            MathUtils.clamp(position.y + dy * SPEED * deltaTime, MIN_Y, MAX_Y),
            position.z);
    }
}
