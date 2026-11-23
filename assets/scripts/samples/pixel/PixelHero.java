package samples.pixel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Pixel-art platformer hero: A/D or the arrows run, Space/W/Up jump, R restarts. The level is a tile map named "Level" (its solid tiles are the ground), the hero is
 * a Sprite Animator whose "idle" and "walk" tags this script plays with {@code play(tag)} and mirrors with {@code setFlipX}. Entities named "Coin ..." are collected on
 * touch and counted on "Score Text". The camera named "Main Camera" follows sideways.
 *
 * <p>Movement is written here instead of using the physics engine (as in the other platformer samples): the body is a box, tested against the tile map with
 * {@link TilemapComponent#isSolidAt}, moved a few units at a time so it cannot pass through a thin floor.
 */
public class PixelHero extends ScriptComponent {
    static final float RUN_SPEED = 220f;
    static final float JUMP_SPEED = 640f;
    static final float GRAVITY = 1600f;
    static final float MAX_FALL_SPEED = 900f;
    /** Below this height the hero has fallen out of the level and starts again. */
    static final float FALL_LIMIT = -200f;
    /** Half the width and height of the hero's body, in scene units (the 64 unit sprite has transparent margins). */
    static final float HALF_WIDTH = 12f;
    static final float HALF_HEIGHT = 28f;
    /** The longest distance the body moves before the level is tested again. */
    static final float STEP = 1f;
    /** A coin is picked up when the hero is this close to it. */
    static final float COIN_REACH = 30f;
    /** How fast the camera catches up with the hero, per second. */
    static final float CAMERA_RATE = 6f;
    /** Space between sample points when testing the body against the level; below the tile size, so no cell is missed. */
    static final float PROBE_SPACING = 8f;
    /** Pulls the body's far edges in so a body exactly as wide as the cells it fills does not touch the next ones. */
    static final float EDGE_INSET = 0.01f;

    private TilemapComponent level;
    private SpriteAnimatorComponent animator;
    private TextComponent scoreText;
    private Entity camera;
    private final List<Entity> coins = new ArrayList<>();
    private float cameraMinX;
    private float cameraMaxX;
    private float startX;
    private float startY;
    private float x;
    private float y;
    private float velocityY;
    private boolean grounded;
    private int collected;
    private int totalCoins;

    @Override
    public void onStart() {
        super.onStart();
        Entity levelEntity = getScene().findEntityByName("Level");
        if (levelEntity == null || levelEntity.getComponent(TilemapComponent.class) == null) {
            throw new IllegalStateException("PixelHero needs an entity named 'Level' with a Tilemap component");
        }
        level = levelEntity.getComponent(TilemapComponent.class);
        animator = getEntity().getComponent(SpriteAnimatorComponent.class);
        if (animator == null) {
            throw new IllegalStateException("PixelHero needs a Sprite Animator on the hero");
        }
        for (Entity entity : getScene().getAllEntities()) {
            if (entity.getName().startsWith("Coin")) {
                coins.add(entity);
            }
        }
        totalCoins = coins.size();
        Entity text = getScene().findEntityByName("Score Text");
        scoreText = text != null ? text.getComponent(TextComponent.class) : null;
        camera = getScene().findEntityByName("Main Camera");
        Vector3 origin = levelEntity.getTransform().getWorldPosition();
        float width = level.getMap().getCols() * level.getTileWidth() * Math.abs(levelEntity.getTransform().getWorldScale().x);
        // The camera stops half a view from each edge of the level (the view is 16:9 of the level's height)
        float halfView = level.getMap().getRows() * level.getTileHeight() * 16f / 9f / 2f;
        cameraMinX = origin.x + halfView;
        cameraMaxX = Math.max(cameraMinX, origin.x + width - halfView);
        Vector3 position = getTransform().getWorldPosition();
        startX = position.x;
        startY = position.y;
        restart();
    }

    private void restart() {
        x = startX;
        y = startY;
        velocityY = 0f;
        collected = 0;
        for (Entity coin : coins) {
            coin.setActive(true);
        }
        animator.play("idle", true);
        getTransform().setLocalPosition(x, y, getTransform().getLocalPosition().z);
        showScore();
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        float direction = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) direction -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) direction += 1f;
        boolean jump = Gdx.input.isKeyJustPressed(Keys.SPACE) || Gdx.input.isKeyJustPressed(Keys.W) || Gdx.input.isKeyJustPressed(Keys.UP);
        if (jump && grounded) {
            velocityY = JUMP_SPEED;
            AudioSourceComponent.play(getScene(), "jump");
        }
        velocityY = Math.max(velocityY - GRAVITY * deltaTime, -MAX_FALL_SPEED);

        moveHorizontally(direction * RUN_SPEED * deltaTime);
        moveVertically(velocityY * deltaTime);
        getTransform().setLocalPosition(x, y, getTransform().getLocalPosition().z);

        if (direction != 0f) {
            animator.setFlipX(direction < 0f);
        }
        animator.play(direction != 0f ? "walk" : "idle");
        collectCoins();
        followCamera(deltaTime);
        if (y < FALL_LIMIT) {
            AudioSourceComponent.play(getScene(), "hurt");
            x = startX;
            y = startY;
            velocityY = 0f;
            getTransform().setLocalPosition(x, y, getTransform().getLocalPosition().z);
        }
    }

    /** Whether a body centred at (px, py) overlaps a solid cell: sample points along its edges and through it. */
    private boolean blocked(float px, float py) {
        float left = px - HALF_WIDTH;
        float right = px + HALF_WIDTH - EDGE_INSET;
        float bottom = py - HALF_HEIGHT;
        float top = py + HALF_HEIGHT - EDGE_INSET;
        for (float sx = left; ; sx += PROBE_SPACING) {
            float cx = Math.min(sx, right);
            for (float sy = bottom; ; sy += PROBE_SPACING) {
                float cy = Math.min(sy, top);
                if (level.isSolidAt(cx, cy)) {
                    return true;
                }
                if (cy >= top) {
                    break;
                }
            }
            if (cx >= right) {
                return false;
            }
        }
    }

    private void moveHorizontally(float dx) {
        float remaining = dx;
        while (Math.abs(remaining) > 0f) {
            float step = Math.signum(remaining) * Math.min(Math.abs(remaining), STEP);
            if (blocked(x + step, y)) {
                return;
            }
            x += step;
            remaining -= step;
        }
    }

    private void moveVertically(float dy) {
        grounded = false;
        float remaining = dy;
        while (Math.abs(remaining) > 0f) {
            float step = Math.signum(remaining) * Math.min(Math.abs(remaining), STEP);
            if (blocked(x, y + step)) {
                grounded = step < 0f;
                velocityY = 0f;
                return;
            }
            y += step;
            remaining -= step;
        }
        // Standing still on the floor: the body is resting on it when a small step down would hit
        if (dy <= 0f && blocked(x, y - 1f)) {
            grounded = true;
        }
    }

    private void collectCoins() {
        for (Entity coin : coins) {
            if (!coin.isActiveSelf()) {
                continue;
            }
            Vector3 p = coin.getTransform().getWorldPosition();
            if (Math.abs(p.x - x) < COIN_REACH && Math.abs(p.y - y) < COIN_REACH) {
                coin.setActive(false);
                collected++;
                AudioSourceComponent.play(getScene(), "coin");
                showScore();
            }
        }
    }

    private void showScore() {
        if (scoreText != null) {
            scoreText.setText("Coins: " + collected + " / " + totalCoins + (collected == totalCoins && totalCoins > 0 ? "   All found! R restarts" : ""));
        }
    }

    private void followCamera(float deltaTime) {
        if (camera == null) {
            return;
        }
        Vector3 p = camera.getTransform().getLocalPosition();
        float target = Math.min(Math.max(x, cameraMinX), cameraMaxX);
        float blend = Math.min(1f, CAMERA_RATE * deltaTime);
        camera.getTransform().setLocalPosition(p.x + (target - p.x) * blend, p.y, p.z);
    }
}
