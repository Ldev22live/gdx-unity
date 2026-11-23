package samples.breakout;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/** Moves the paddle left and right between the walls. */
public class BreakoutPaddle extends ScriptComponent {
    /** Paddle speed in units per second. */
    static final float SPEED = 520f;
    /** Inner edges of the side walls of the 640-unit-wide playfield. */
    static final float LEFT_WALL = 10f;
    static final float RIGHT_WALL = 630f;

    private float halfWidth;

    @Override
    public void onStart() {
        super.onStart();
        halfWidth = getComponent(ShapeRendererComponent.class).getWidth() / 2f;
    }

    @Override
    public void onUpdate(float deltaTime) {
        float direction = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) direction -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) direction += 1f;
        Vector3 position = getTransform().getLocalPosition();
        float x = MathUtils.clamp(position.x + direction * SPEED * deltaTime, LEFT_WALL + halfWidth, RIGHT_WALL - halfWidth);
        getTransform().setLocalPosition(x, position.y, position.z);
    }
}
