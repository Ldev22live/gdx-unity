package samples.walker;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/**
 * Walks a Character Controller 3D (TD-049): <b>WASD</b> or the arrow keys walk along the world axes (W goes into the screen),
 * <b>Space</b> jumps while standing on the ground. The "Help Text" entity shows whether the character stands on the ground.
 * The physics system moves the entity; the script only says where it wants to go.
 */
public class WalkerPlayer extends ScriptComponent {
    /** Walking speed in scene units per second (3 m/s). */
    static final float WALK_SPEED = 300f;
    /** Jump take-off speed in scene units per second: about one meter of height. */
    static final float JUMP_SPEED = 450f;
    static final String HINT = "WASD or arrows walk, Space jumps. Climb the steps and the gentle ramp, jump onto the low block, push the crates.  ";

    private TextComponent label;

    @Override
    public void onStart() {
        super.onStart();
        Entity help = getScene().findEntityByName("Help Text");
        label = help != null ? help.getComponent(TextComponent.class) : null;
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (getPhysics3D() == null) {
            return;
        }
        float x = 0f;
        float z = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) x -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) x += 1f;
        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP)) z -= 1f;
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN)) z += 1f;

        Vector3 walk = new Vector3(x, 0f, z);
        if (!walk.isZero()) {
            walk.nor().scl(WALK_SPEED);
        }
        moveCharacter3D(walk);
        if (Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            jumpCharacter3D(JUMP_SPEED);
        }
        if (label != null) {
            label.setText(HINT + (isGrounded3D() ? "On the ground" : "In the air"));
        }
    }
}
