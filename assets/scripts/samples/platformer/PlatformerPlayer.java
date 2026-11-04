package samples.platformer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 2D platformer player: run, jump, land on platforms, collect coins and reach the goal.
 *
 * <p>Scene conventions (rename to taste):
 * <ul>
 *   <li>Entities whose name starts with "Platform" are solid boxes (their Shape Renderer size is the box).</li>
 *   <li>Entities whose name starts with "Coin" are collected on touch.</li>
 *   <li>"Goal" ends the level; "Score Text" and "Message Text" show progress.</li>
 * </ul>
 * Movement uses simple box collision written here instead of the physics engine, so every rule is easy to change.
 */
public class PlatformerPlayer extends ScriptComponent {
    /** Horizontal run speed in units per second. */
    static final float RUN_SPEED = 260f;
    /** Upward speed at the start of a jump. */
    static final float JUMP_SPEED = 700f;
    /** Downward acceleration in units per second squared. */
    static final float GRAVITY = 1700f;
    /** Fastest fall speed, so the player never tunnels through thin platforms. */
    static final float MAX_FALL_SPEED = 900f;
    /** Below this height the player has fallen off the level and starts again. */
    static final float FALL_LIMIT = -300f;
    /** Boxes that only touch do not count as overlapping (protects against rounding while standing on a platform). */
    static final float SKIN = 0.01f;

    /** One solid or collectable box: centre and half size. */
    record Box(Entity entity, float x, float y, float halfWidth, float halfHeight) {
        boolean overlaps(float px, float py, float phw, float phh) {
            return Math.abs(px - x) < phw + halfWidth - SKIN && Math.abs(py - y) < phh + halfHeight - SKIN;
        }
    }

    private final List<Box> platforms = new ArrayList<>();
    private final List<Box> coins = new ArrayList<>();
    private Box goal;
    private TextComponent scoreText;
    private TextComponent messageText;

    private float startX;
    private float startY;
    private float halfWidth;
    private float halfHeight;
    private float x;
    private float y;
    private float velocityY;
    private boolean grounded;
    private int coinsCollected;
    private boolean finished;

    @Override
    public void onStart() {
        super.onStart();
        for (Entity entity : getScene().getAllEntities()) {
            String name = entity.getName();
            if (name.startsWith("Platform")) {
                platforms.add(boxOf(entity));
            } else if (name.startsWith("Coin")) {
                coins.add(boxOf(entity));
            } else if (name.equals("Goal")) {
                goal = boxOf(entity);
            }
        }
        scoreText = textOf("Score Text");
        messageText = textOf("Message Text");
        Box self = boxOf(getEntity());
        halfWidth = self.halfWidth();
        halfHeight = self.halfHeight();
        startX = self.x();
        startY = self.y();
        restart();
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        if (finished) {
            return;
        }
        float direction = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) direction -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) direction += 1f;
        boolean jump = Gdx.input.isKeyJustPressed(Keys.SPACE) || Gdx.input.isKeyJustPressed(Keys.W)
            || Gdx.input.isKeyJustPressed(Keys.UP);
        if (jump && grounded) {
            velocityY = JUMP_SPEED;
        }
        velocityY = Math.max(velocityY - GRAVITY * deltaTime, -MAX_FALL_SPEED);

        moveHorizontally(direction * RUN_SPEED * deltaTime);
        moveVertically(velocityY * deltaTime);
        getTransform().setLocalPosition(x, y, 0f);

        collectCoins();
        if (goal != null && goal.overlaps(x, y, halfWidth, halfHeight)) {
            finished = true;
            messageText.setText("Level complete! " + coinsCollected + "/" + coins.size() + " coins. Press R to play again");
        }
        if (y < FALL_LIMIT) {
            x = startX;
            y = startY;
            velocityY = 0f;
        }
    }

    /** Moves sideways and stops at the side of any platform hit. */
    private void moveHorizontally(float dx) {
        x += dx;
        for (Box platform : platforms) {
            if (platform.overlaps(x, y, halfWidth, halfHeight)) {
                x = dx > 0 ? platform.x() - platform.halfWidth() - halfWidth : platform.x() + platform.halfWidth() + halfWidth;
            }
        }
    }

    /** Moves up or down; landing on a platform makes the player grounded, hitting one from below stops the jump. */
    private void moveVertically(float dy) {
        y += dy;
        grounded = false;
        for (Box platform : platforms) {
            if (platform.overlaps(x, y, halfWidth, halfHeight)) {
                if (dy <= 0f) {
                    y = platform.y() + platform.halfHeight() + halfHeight;
                    grounded = true;
                } else {
                    y = platform.y() - platform.halfHeight() - halfHeight;
                }
                velocityY = 0f;
            }
        }
    }

    private void collectCoins() {
        for (Box coin : coins) {
            if (coin.entity().isActiveSelf() && coin.overlaps(x, y, halfWidth, halfHeight)) {
                coin.entity().setActive(false);
                coinsCollected++;
                updateScore();
            }
        }
    }

    private void restart() {
        x = startX;
        y = startY;
        velocityY = 0f;
        coinsCollected = 0;
        finished = false;
        for (Box coin : coins) {
            coin.entity().setActive(true);
        }
        getTransform().setLocalPosition(x, y, 0f);
        messageText.setText("");
        updateScore();
    }

    private void updateScore() {
        scoreText.setText("Coins: " + coinsCollected + " / " + coins.size());
    }

    private TextComponent textOf(String name) {
        Entity entity = getScene().findEntityByName(name);
        if (entity == null || entity.getComponent(TextComponent.class) == null) {
            throw new IllegalStateException("The scene needs an entity '" + name + "' with a Text component");
        }
        return entity.getComponent(TextComponent.class);
    }

    /** Box of an entity's Shape Renderer at its world position, including the entity's scale. */
    static Box boxOf(Entity entity) {
        ShapeRendererComponent shape = entity.getComponent(ShapeRendererComponent.class);
        if (shape == null) {
            throw new IllegalStateException("'" + entity.getName() + "' needs a Shape Renderer to have a size");
        }
        Vector3 position = entity.getTransform().getWorldPosition();
        Vector3 scale = entity.getTransform().getWorldScale();
        return new Box(entity, position.x, position.y, shape.getWidth() * scale.x / 2f, shape.getHeight() * scale.y / 2f);
    }
}
