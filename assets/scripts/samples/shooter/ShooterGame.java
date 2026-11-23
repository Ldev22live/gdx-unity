package samples.shooter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rules of the space shooter: Space fires from the entity named "Ship", enemies arrive from the top faster and faster,
 * shots destroy them, enemies that reach the ship cost a life. Stars scroll down for a sense of speed.
 *
 * <p>Bullets and enemies are pooled: created once in {@link #onStart()} and switched on and off, which keeps the frame
 * time steady. That is the pattern to copy for anything spawned often.
 */
public class ShooterGame extends ScriptComponent {
    /** Pool sizes: the most bullets and enemies on screen at once. */
    static final int BULLET_POOL = 24;
    static final int ENEMY_POOL = 16;
    /** Seconds between shots while Space is held. */
    static final float FIRE_INTERVAL = 0.18f;
    static final float BULLET_SPEED = 620f;
    static final float BULLET_WIDTH = 4f;
    static final float BULLET_HEIGHT = 14f;
    static final float ENEMY_WIDTH = 30f;
    static final float ENEMY_HEIGHT = 22f;
    /** Enemy fall speed at the start and the extra speed per second of play. */
    static final float ENEMY_SPEED = 90f;
    static final float ENEMY_SPEED_GAIN = 2.5f;
    /** Sideways sway of enemies: amplitude in units and angular speed in radians per second. */
    static final float SWAY_AMPLITUDE = 60f;
    static final float SWAY_RATE = 2f;
    /** Seconds between enemies at the start, the shortest interval and the reduction per enemy. */
    static final float SPAWN_INTERVAL = 1.2f;
    static final float MIN_SPAWN_INTERVAL = 0.35f;
    static final float SPAWN_SPEED_UP = 0.03f;
    /** Star scroll speed. */
    static final float STAR_SPEED = 120f;
    /** Top and bottom of the 480-unit-high playfield, with room for objects to enter and leave. */
    static final float SPAWN_Y = 510f;
    static final float OFF_SCREEN_Y = -30f;
    static final int ENEMY_POINTS = 100;
    static final int START_LIVES = 3;
    /** Fixed seed: the same enemy pattern every game, which also keeps tests repeatable. */
    static final long RANDOM_SEED = 42L;

    private final List<Entity> bullets = new ArrayList<>();
    private final List<Entity> enemies = new ArrayList<>();
    private final List<Entity> stars = new ArrayList<>();
    /** Spawn x and spawn time per enemy, indexed like {@link #enemies}, for the sway. */
    private final float[] enemyBaseX = new float[ENEMY_POOL];
    private final float[] enemyAge = new float[ENEMY_POOL];
    private Random random;
    private Entity ship;
    private float shipHalfWidth;
    private float shipHalfHeight;
    private TextComponent scoreText;
    private TextComponent livesText;
    private TextComponent messageText;

    private float fireCooldown;
    private float spawnTimer;
    private float spawnInterval;
    private float playTime;
    private int score;
    private int lives;
    private boolean gameOver;

    @Override
    public void onStart() {
        super.onStart();
        ship = getScene().findEntityByName("Ship");
        ShapeRendererComponent shipShape = ship.getComponent(ShapeRendererComponent.class);
        shipHalfWidth = shipShape.getWidth() / 2f;
        shipHalfHeight = shipShape.getHeight() / 2f;
        scoreText = getScene().findEntityByName("Score Text").getComponent(TextComponent.class);
        livesText = getScene().findEntityByName("Lives Text").getComponent(TextComponent.class);
        messageText = getScene().findEntityByName("Message Text").getComponent(TextComponent.class);
        for (Entity entity : getScene().getAllEntities()) {
            if (entity.getName().startsWith("Star")) {
                stars.add(entity);
            }
        }
        for (int i = 0; i < BULLET_POOL; i++) {
            bullets.add(createPooled("Bullet " + i, BULLET_WIDTH, BULLET_HEIGHT, new Color(1f, 0.92f, 0.3f, 1f)));
        }
        for (int i = 0; i < ENEMY_POOL; i++) {
            enemies.add(createPooled("Enemy " + i, ENEMY_WIDTH, ENEMY_HEIGHT, new Color(0.95f, 0.3f, 0.35f, 1f)));
        }
        restart();
    }

    /** Creates an inactive entity with a rectangle; the pool switches it on when needed. */
    private Entity createPooled(String name, float width, float height, Color color) {
        Entity entity = getScene().createEntity(name);
        entity.addComponent(new ShapeRendererComponent(width, height, color));
        entity.setActive(false);
        return entity;
    }

    @Override
    public void onUpdate(float deltaTime) {
        scrollStars(deltaTime);
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        if (gameOver) {
            return;
        }
        playTime += deltaTime;
        fireCooldown -= deltaTime;
        if (Gdx.input.isKeyPressed(Keys.SPACE) && fireCooldown <= 0f) {
            Vector3 shipPosition = ship.getTransform().getWorldPosition();
            activate(bullets, shipPosition.x, shipPosition.y + shipHalfHeight);
            fireCooldown = FIRE_INTERVAL;
            AudioSourceComponent.play(getScene(), "shoot");
        }
        spawnTimer -= deltaTime;
        if (spawnTimer <= 0f) {
            int index = activate(enemies, 40f + random.nextFloat() * 560f, SPAWN_Y);
            if (index >= 0) {
                enemyBaseX[index] = enemies.get(index).getTransform().getLocalPosition().x;
                enemyAge[index] = 0f;
            }
            spawnInterval = Math.max(MIN_SPAWN_INTERVAL, spawnInterval - SPAWN_SPEED_UP);
            spawnTimer = spawnInterval;
        }
        moveBullets(deltaTime);
        moveEnemies(deltaTime);
        resolveHits();
    }

    private void moveBullets(float deltaTime) {
        for (Entity bullet : bullets) {
            if (bullet.isActiveSelf()) {
                Vector3 position = bullet.getTransform().getLocalPosition();
                bullet.getTransform().setLocalPosition(position.x, position.y + BULLET_SPEED * deltaTime, 0f);
                if (position.y > SPAWN_Y) {
                    bullet.setActive(false);
                }
            }
        }
    }

    private void moveEnemies(float deltaTime) {
        float speed = ENEMY_SPEED + ENEMY_SPEED_GAIN * playTime;
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (!enemy.isActiveSelf()) {
                continue;
            }
            enemyAge[i] += deltaTime;
            Vector3 position = enemy.getTransform().getLocalPosition();
            float x = MathUtils.clamp(enemyBaseX[i] + MathUtils.sin(enemyAge[i] * SWAY_RATE) * SWAY_AMPLITUDE, 20f, 620f);
            enemy.getTransform().setLocalPosition(x, position.y - speed * deltaTime, 0f);
            if (position.y < OFF_SCREEN_Y) {
                enemy.setActive(false);
            }
        }
    }

    private void resolveHits() {
        Vector3 shipPosition = ship.getTransform().getWorldPosition();
        for (Entity enemy : enemies) {
            if (!enemy.isActiveSelf()) {
                continue;
            }
            Vector3 enemyPosition = enemy.getTransform().getLocalPosition();
            for (Entity bullet : bullets) {
                if (bullet.isActiveSelf() && overlaps(bullet.getTransform().getLocalPosition(), BULLET_WIDTH / 2f,
                    BULLET_HEIGHT / 2f, enemyPosition, ENEMY_WIDTH / 2f, ENEMY_HEIGHT / 2f)) {
                    bullet.setActive(false);
                    enemy.setActive(false);
                    score += ENEMY_POINTS;
                    AudioSourceComponent.play(getScene(), "explode");
                    updateTexts();
                    break;
                }
            }
            if (enemy.isActiveSelf()
                && overlaps(shipPosition, shipHalfWidth, shipHalfHeight, enemyPosition, ENEMY_WIDTH / 2f, ENEMY_HEIGHT / 2f)) {
                enemy.setActive(false);
                lives--;
                AudioSourceComponent.play(getScene(), "hurt");
                updateTexts();
                if (lives <= 0) {
                    gameOver = true;
                    AudioSourceComponent.play(getScene(), "lose");
                    messageText.setText("Game over. Score " + score + ". Press R to play again");
                }
            }
        }
    }

    private void scrollStars(float deltaTime) {
        for (Entity star : stars) {
            Vector3 position = star.getTransform().getLocalPosition();
            float y = position.y - STAR_SPEED * deltaTime;
            star.getTransform().setLocalPosition(position.x, y < 0f ? y + 480f : y, position.z);
        }
    }

    /** Switches on the first inactive entity of {@code pool} at (x, y); returns its index, or -1 if all are in use. */
    private static int activate(List<Entity> pool, float x, float y) {
        for (int i = 0; i < pool.size(); i++) {
            Entity entity = pool.get(i);
            if (!entity.isActiveSelf()) {
                entity.getTransform().setLocalPosition(x, y, 0f);
                entity.setActive(true);
                return i;
            }
        }
        return -1;
    }

    private static boolean overlaps(Vector3 a, float aHalfWidth, float aHalfHeight, Vector3 b, float bHalfWidth, float bHalfHeight) {
        return Math.abs(a.x - b.x) < aHalfWidth + bHalfWidth && Math.abs(a.y - b.y) < aHalfHeight + bHalfHeight;
    }

    private void restart() {
        for (Entity entity : bullets) {
            entity.setActive(false);
        }
        for (Entity entity : enemies) {
            entity.setActive(false);
        }
        random = new Random(RANDOM_SEED);
        score = 0;
        lives = START_LIVES;
        playTime = 0f;
        fireCooldown = 0f;
        spawnInterval = SPAWN_INTERVAL;
        spawnTimer = SPAWN_INTERVAL;
        gameOver = false;
        messageText.setText("");
        updateTexts();
    }

    private void updateTexts() {
        scoreText.setText("Score: " + score);
        livesText.setText("Lives: " + lives);
    }
}
