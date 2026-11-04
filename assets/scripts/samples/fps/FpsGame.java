package samples.fps;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A first-person arena shooter. The "Player" entity is the camera: WASD walks and strafes, the arrow keys turn and
 * look up and down, Space fires balls. Drones (entities whose name starts with "Enemy") come in {@link #WAVE_SIZES waves}
 * from the "Spawn" points, chase the player and hit when close. Walls, crates and pillars block movement and balls
 * (see {@link Arena}). Clear every wave to win; R restarts.
 *
 * <p>The positions of the player and the drones live in their entities' transforms, so moving them in the editor (or
 * in a test) is all it takes to place them. Put this script on one entity; the player and drones need no scripts.
 */
public class FpsGame extends ScriptComponent {
    /** Walking speed in units per second; a drone is a 40-unit box. */
    static final float MOVE_SPEED = 320f;
    /** Turning and looking speed in degrees per second, and how far up or down the player can look. */
    static final float TURN_RATE = 150f;
    static final float LOOK_RATE = 90f;
    static final float MAX_PITCH = 60f;
    /** Half the player's footprint, for collision with walls and drones. */
    static final float PLAYER_RADIUS = 20f;
    static final int PLAYER_HEALTH = 100;
    /** Seconds between shots while Space is held. */
    static final float FIRE_INTERVAL = 0.2f;
    /** Ball speed in units per second, how far a ball flies before it disappears, and its radius and colour. */
    static final float BALL_SPEED = 1500f;
    static final float BALL_RANGE = 3000f;
    static final float BALL_RADIUS = 5f;
    static final Color BALL_COLOR = new Color(0.35f, 0.9f, 1f, 1f);
    /** Balls made when the game starts and reused; enough for every ball that can be in flight at once. */
    static final int BALL_POOL = 16;
    /** How long the muzzle flash and the impact spark stay visible, in seconds. */
    static final float FLASH_TIME = 0.06f;
    static final float SPARK_TIME = 0.12f;
    /** Gun kick towards the player per shot, in units, and how fast it settles (per second). */
    static final float RECOIL = 6f;
    static final float RECOIL_RECOVERY = 10f;
    /** Drones in each wave; the scene needs at least as many "Enemy" entities as the largest wave. */
    static final int[] WAVE_SIZES = {3, 5, 8};
    /** Drone speed in each wave, in units per second. */
    static final float[] WAVE_SPEEDS = {130f, 160f, 190f};
    /** Hits a drone takes, its footprint, the distance it attacks from, damage per hit and seconds between hits. */
    static final int ENEMY_HEALTH = 3;
    static final float ENEMY_RADIUS = 22f;
    static final float ATTACK_RANGE = 60f;
    static final int ATTACK_DAMAGE = 10;
    static final float ATTACK_INTERVAL = 0.8f;
    /**
     * Drones hover at about the player's eye height (60), so shots need no aiming up or down, and bob up and
     * down by this much (radians per second for the rate).
     */
    static final float ENEMY_HEIGHT = 50f;
    static final float BOB_HEIGHT = 6f;
    static final float BOB_RATE = 4f;
    /** A drone that moves less than this share of its step is blocked and side-steps instead. */
    static final float BLOCKED_SHARE = 0.3f;
    /** How long a drone shows white after being hit. */
    static final float HIT_FLASH_TIME = 0.08f;
    /** Pause before each wave, in seconds, with its name on screen. */
    static final float WAVE_DELAY = 2f;
    /** Drones that spawn at the same point are spread this far apart. */
    static final float SPAWN_SPREAD = 60f;
    static final String START_MESSAGE = "Press Space to start";

    private enum State { WAITING, BETWEEN_WAVES, FIGHTING, WON, LOST }

    /** One drone: its entity holds the position; this holds the rest. */
    private static final class Enemy {
        final Entity entity;
        final MeshPrimitiveComponent body;
        final Color color;
        final int index;
        int health;
        float attackCooldown;
        float flash;

        Enemy(Entity entity, int index) {
            this.entity = entity;
            this.index = index;
            body = entity.getComponent(MeshPrimitiveComponent.class);
            if (body == null) {
                throw new IllegalStateException("'" + entity.getName() + "' needs a Mesh Primitive (a box)");
            }
            color = new Color(body.getColor());
        }
    }

    /** One ball in flight: its entity shows it; this holds its movement. */
    private static final class Ball {
        final Entity entity;
        final Vector3 position = new Vector3();
        final Vector3 velocity = new Vector3();
        float life;

        Ball(Entity entity) {
            this.entity = entity;
        }
    }

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Ball> balls = new ArrayList<>();
    private final List<Vector3> spawns = new ArrayList<>();
    private final Vector3 position = new Vector3();
    private final Vector3 step = new Vector3();
    private final Vector3 forward = new Vector3();
    private final Vector3 origin = new Vector3();
    private final Vector3 direction = new Vector3();
    private final Vector3 enemyHalfSize = new Vector3();
    private final Vector3 gunRest = new Vector3();
    private Arena arena;
    private Entity player;
    private Entity gun;
    private Entity muzzleFlash;
    private Entity spark;
    private Vector3 playerStart;
    private float startYaw;
    private TextComponent healthText;
    private TextComponent waveText;
    private TextComponent enemiesText;
    private TextComponent messageText;
    private State state;
    private float yaw;
    private float pitch;
    private int health;
    private int wave;
    private float waveDelay;
    private float fireCooldown;
    private float flashLeft;
    private float sparkLeft;
    private float recoil;
    private float time;
    private float elapsed;

    @Override
    public void onStart() {
        super.onStart();
        arena = Arena.fromScene(getScene());
        player = required("Player");
        gun = required("Gun (Player)");
        muzzleFlash = required("Muzzle Flash (Player)");
        spark = required("Impact Spark");
        gunRest.set(gun.getTransform().getLocalPosition());
        playerStart = player.getTransform().getWorldPosition();
        startYaw = player.getTransform().getWorldRotation().getYaw();
        List<Entity> enemyEntities = new ArrayList<>();
        for (Entity entity : getScene().getAllEntities()) {
            if (entity.getName().startsWith("Enemy")) {
                enemyEntities.add(entity);
            } else if (entity.getName().startsWith("Spawn")) {
                spawns.add(entity.getTransform().getWorldPosition());
            }
        }
        enemyEntities.sort(Comparator.comparing(Entity::getName));
        int largestWave = 0;
        for (int size : WAVE_SIZES) {
            largestWave = Math.max(largestWave, size);
        }
        if (enemyEntities.size() < largestWave || spawns.isEmpty()) {
            throw new IllegalStateException("FpsGame needs " + largestWave + " 'Enemy' entities and at least one "
                + "'Spawn' point; found " + enemyEntities.size() + " and " + spawns.size());
        }
        for (int i = 0; i < enemyEntities.size(); i++) {
            enemies.add(new Enemy(enemyEntities.get(i), i));
        }
        for (int i = 0; i < BALL_POOL; i++) {
            Entity ball = getScene().createEntity("Ball " + (i + 1));
            ball.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.SPHERE,
                BALL_RADIUS * 2f, BALL_RADIUS * 2f, BALL_RADIUS * 2f, BALL_COLOR));
            ball.setActive(false);
            balls.add(new Ball(ball));
        }
        healthText = text("Health Text");
        waveText = text("Wave Text");
        enemiesText = text("Enemies Text");
        messageText = text("Message Text");
        restart();
    }

    private Entity required(String name) {
        Entity entity = getScene().findEntityByName(name);
        if (entity == null) {
            throw new IllegalStateException("FpsGame needs an entity named '" + name + "'");
        }
        return entity;
    }

    private TextComponent text(String name) {
        TextComponent text = required(name).getComponent(TextComponent.class);
        if (text == null) {
            throw new IllegalStateException("'" + name + "' needs a Text component");
        }
        return text;
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        time += deltaTime;
        if (state == State.WAITING && Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            state = State.BETWEEN_WAVES;
            beginWaveDelay();
            // The same press must not also fire
            fireCooldown = FIRE_INTERVAL;
        }
        if (state == State.BETWEEN_WAVES || state == State.FIGHTING) {
            elapsed += deltaTime;
        }
        if (state == State.BETWEEN_WAVES) {
            waveDelay -= deltaTime;
            if (waveDelay <= 0f) {
                spawnWave();
            }
        }
        if (state != State.LOST && state != State.WON) {
            movePlayer(deltaTime);
            shoot(deltaTime);
        }
        updateBalls(deltaTime);
        updateEnemies(deltaTime);
        updateEffects(deltaTime);
        updateHud();
    }

    private void movePlayer(float deltaTime) {
        if (Gdx.input.isKeyPressed(Keys.LEFT)) yaw += TURN_RATE * deltaTime;
        if (Gdx.input.isKeyPressed(Keys.RIGHT)) yaw -= TURN_RATE * deltaTime;
        if (Gdx.input.isKeyPressed(Keys.UP)) pitch += LOOK_RATE * deltaTime;
        if (Gdx.input.isKeyPressed(Keys.DOWN)) pitch -= LOOK_RATE * deltaTime;
        pitch = MathUtils.clamp(pitch, -MAX_PITCH, MAX_PITCH);

        float ahead = 0f;
        float right = 0f;
        if (Gdx.input.isKeyPressed(Keys.W)) ahead += 1f;
        if (Gdx.input.isKeyPressed(Keys.S)) ahead -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D)) right += 1f;
        if (Gdx.input.isKeyPressed(Keys.A)) right -= 1f;
        // Walking stays level: forward on the floor is (-sin yaw, -cos yaw), right is (cos yaw, -sin yaw)
        float sin = MathUtils.sinDeg(yaw);
        float cos = MathUtils.cosDeg(yaw);
        step.set(-sin * ahead + cos * right, 0f, -cos * ahead - sin * right);
        if (!step.isZero()) {
            step.nor().scl(MOVE_SPEED * deltaTime);
        }
        position.set(player.getTransform().getLocalPosition());
        arena.move(position, step.x, step.z, PLAYER_RADIUS);
        player.getTransform().setLocalPosition(position);
        player.getTransform().setLocalEulerAngles(yaw, pitch, 0f);
    }

    private void shoot(float deltaTime) {
        fireCooldown -= deltaTime;
        if (state == State.WAITING || !Gdx.input.isKeyPressed(Keys.SPACE) || fireCooldown > 0f) {
            return;
        }
        fireCooldown = FIRE_INTERVAL;
        flashLeft = FLASH_TIME;
        recoil = RECOIL;
        // Find what the crosshair points at and send the ball from the muzzle towards that point, so it lands under
        // the crosshair even though the gun sits below and to the right of the eye
        origin.set(player.getTransform().getWorldPosition());
        float cosPitch = MathUtils.cosDeg(pitch);
        // Forward (0, 0, -1) turned by pitch, then yaw
        forward.set(-cosPitch * MathUtils.sinDeg(yaw), MathUtils.sinDeg(pitch), -cosPitch * MathUtils.cosDeg(yaw));
        float aim = Math.min(Math.min(arena.raycast(origin, forward), nearestEnemy(origin, forward)), BALL_RANGE);
        Vector3 muzzle = muzzleFlash.getTransform().getWorldPosition();
        Ball ball = nextBall();
        ball.position.set(muzzle);
        ball.velocity.set(origin.mulAdd(forward, aim)).sub(muzzle).nor().scl(BALL_SPEED);
        ball.life = BALL_RANGE / BALL_SPEED;
        ball.entity.getTransform().setLocalPosition(ball.position);
        ball.entity.setActive(true);
    }

    /** A free ball from the pool, or the one with the least flight left when all are in use. */
    private Ball nextBall() {
        Ball oldest = balls.get(0);
        for (Ball ball : balls) {
            if (!ball.entity.isActiveSelf()) {
                return ball;
            }
            if (ball.life < oldest.life) {
                oldest = ball;
            }
        }
        return oldest;
    }

    /** Distance along the unit ray to the nearest active drone, or {@link Arena#NO_HIT}. */
    private float nearestEnemy(Vector3 from, Vector3 along) {
        float nearest = Arena.NO_HIT;
        for (Enemy enemy : enemies) {
            if (enemy.entity.isActiveSelf()) {
                enemyHalfSize.set(enemy.body.getSize()).scl(0.5f);
                nearest = Math.min(nearest,
                    Arena.rayBox(from, along, enemy.entity.getTransform().getWorldPosition(), enemyHalfSize));
            }
        }
        return nearest;
    }

    /**
     * Moves the balls in flight. Each frame's movement is tested as a ray, so a fast ball cannot skip through a thin
     * wall or a drone between two frames. A ball stops at the first solid box, drone or floor it reaches.
     */
    private void updateBalls(float deltaTime) {
        for (Ball ball : balls) {
            if (!ball.entity.isActiveSelf()) {
                continue;
            }
            ball.life -= deltaTime;
            float stepLength = BALL_SPEED * deltaTime;
            direction.set(ball.velocity).nor();
            float nearest = arena.raycast(ball.position, direction);
            if (direction.y < 0f) {
                nearest = Math.min(nearest, (ball.position.y - BALL_RADIUS) / -direction.y);
            }
            Enemy target = null;
            for (Enemy enemy : enemies) {
                if (!enemy.entity.isActiveSelf()) {
                    continue;
                }
                // Growing the drone's box by the ball's radius makes the ray test act like a ball test
                enemyHalfSize.set(enemy.body.getSize()).scl(0.5f).add(BALL_RADIUS);
                float t = Arena.rayBox(ball.position, direction, enemy.entity.getTransform().getWorldPosition(),
                    enemyHalfSize);
                if (t < nearest) {
                    nearest = t;
                    target = enemy;
                }
            }
            if (nearest <= stepLength) {
                ball.position.mulAdd(direction, nearest);
                ball.entity.setActive(false);
                spark.getTransform().setLocalPosition(ball.position);
                spark.setActive(true);
                sparkLeft = SPARK_TIME;
                if (target != null) {
                    hit(target);
                }
            } else if (ball.life <= 0f) {
                ball.entity.setActive(false);
            } else {
                ball.position.mulAdd(direction, stepLength);
                ball.entity.getTransform().setLocalPosition(ball.position);
            }
        }
    }

    private void hit(Enemy target) {
        target.health--;
        target.flash = HIT_FLASH_TIME;
        if (target.health > 0) {
            return;
        }
        target.entity.setActive(false);
        if (state == State.FIGHTING && aliveEnemies() == 0) {
            if (wave == WAVE_SIZES.length) {
                state = State.WON;
                messageText.setText(String.format(Locale.ROOT,
                    "Arena cleared in %.1f s with %d health left!  Press R to play again", elapsed, health));
            } else {
                state = State.BETWEEN_WAVES;
                beginWaveDelay();
            }
        }
    }

    private void updateEnemies(float deltaTime) {
        Vector3 playerPosition = player.getTransform().getWorldPosition();
        float speed = wave > 0 ? WAVE_SPEEDS[wave - 1] : 0f;
        for (Enemy enemy : enemies) {
            if (!enemy.entity.isActiveSelf()) {
                continue;
            }
            position.set(enemy.entity.getTransform().getLocalPosition());
            float dx = playerPosition.x - position.x;
            float dz = playerPosition.z - position.z;
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            enemy.attackCooldown -= deltaTime;
            if (state == State.FIGHTING && distance > ATTACK_RANGE) {
                float stepLength = speed * deltaTime;
                float startX = position.x;
                float startZ = position.z;
                arena.move(position, dx / distance * stepLength, dz / distance * stepLength, ENEMY_RADIUS);
                float moved = Vector3.len(position.x - startX, 0f, position.z - startZ);
                if (moved < stepLength * BLOCKED_SHARE) {
                    // Blocked by cover: side-step around it; even and odd drones go opposite ways
                    float side = enemy.index % 2 == 0 ? 1f : -1f;
                    arena.move(position, -dz / distance * stepLength * side, dx / distance * stepLength * side,
                        ENEMY_RADIUS);
                }
            } else if (state == State.FIGHTING && enemy.attackCooldown <= 0f) {
                enemy.attackCooldown = ATTACK_INTERVAL;
                health = Math.max(0, health - ATTACK_DAMAGE);
                if (health == 0) {
                    state = State.LOST;
                    messageText.setText("You were overwhelmed on wave " + wave + ".  Press R to try again");
                }
            }
            separate(enemy);
            position.y = ENEMY_HEIGHT + MathUtils.sin(time * BOB_RATE + enemy.index) * BOB_HEIGHT;
            enemy.entity.getTransform().setLocalPosition(position);
            // Face the player: the drone's front is its -Z side, where its eye is
            enemy.entity.getTransform().setLocalEulerAngles(MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees, 0f, 0f);
            enemy.flash -= deltaTime;
            enemy.body.setColor(enemy.flash > 0f ? Color.WHITE : enemy.color);
        }
    }

    /** Keeps drones from stacking inside each other or inside the player. */
    private void separate(Enemy enemy) {
        for (Enemy other : enemies) {
            if (other == enemy || !other.entity.isActiveSelf()) {
                continue;
            }
            Vector3 otherPosition = other.entity.getTransform().getLocalPosition();
            pushApart(otherPosition.x, otherPosition.z, ENEMY_RADIUS * 2f);
        }
        Vector3 playerPosition = player.getTransform().getWorldPosition();
        pushApart(playerPosition.x, playerPosition.z, ENEMY_RADIUS + PLAYER_RADIUS);
    }

    /** Moves {@link #position} (a drone, without going through walls) until it is {@code minimum} away from (x, z). */
    private void pushApart(float x, float z, float minimum) {
        float dx = position.x - x;
        float dz = position.z - z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance >= minimum || distance == 0f) {
            return;
        }
        float push = (minimum - distance) / distance;
        arena.move(position, dx * push, dz * push, ENEMY_RADIUS);
    }

    private void updateEffects(float deltaTime) {
        flashLeft -= deltaTime;
        muzzleFlash.setActive(flashLeft > 0f);
        sparkLeft -= deltaTime;
        if (sparkLeft <= 0f) {
            spark.setActive(false);
        }
        recoil = Math.max(0f, recoil - recoil * RECOIL_RECOVERY * deltaTime);
        gun.getTransform().setLocalPosition(gunRest.x, gunRest.y, gunRest.z + recoil);
    }

    private void beginWaveDelay() {
        waveDelay = WAVE_DELAY;
        messageText.setText("Wave " + (wave + 1) + " of " + WAVE_SIZES.length);
    }

    private void spawnWave() {
        wave++;
        state = State.FIGHTING;
        messageText.setText("");
        for (int i = 0; i < WAVE_SIZES[wave - 1]; i++) {
            Enemy enemy = enemies.get(i);
            Vector3 spawn = spawns.get(i % spawns.size());
            float spread = (i / spawns.size()) * SPAWN_SPREAD;
            enemy.entity.getTransform().setLocalPosition(spawn.x + spread, ENEMY_HEIGHT, spawn.z + spread);
            enemy.health = ENEMY_HEALTH;
            enemy.attackCooldown = ATTACK_INTERVAL;
            enemy.flash = 0f;
            enemy.entity.setActive(true);
        }
    }

    private int aliveEnemies() {
        int alive = 0;
        for (Enemy enemy : enemies) {
            if (enemy.entity.isActiveSelf()) {
                alive++;
            }
        }
        return alive;
    }

    private void updateHud() {
        healthText.setText("Health " + health);
        waveText.setText("Wave " + Math.max(wave, 1) + " / " + WAVE_SIZES.length);
        enemiesText.setText("Drones left " + aliveEnemies());
    }

    private void restart() {
        player.getTransform().setLocalPosition(playerStart);
        yaw = startYaw;
        pitch = 0f;
        player.getTransform().setLocalEulerAngles(yaw, pitch, 0f);
        for (Enemy enemy : enemies) {
            enemy.entity.setActive(false);
            enemy.body.setColor(enemy.color);
        }
        state = State.WAITING;
        health = PLAYER_HEALTH;
        wave = 0;
        elapsed = 0f;
        fireCooldown = 0f;
        flashLeft = 0f;
        sparkLeft = 0f;
        recoil = 0f;
        spark.setActive(false);
        muzzleFlash.setActive(false);
        for (Ball ball : balls) {
            ball.entity.setActive(false);
        }
        messageText.setText(START_MESSAGE);
        updateHud();
    }
}
