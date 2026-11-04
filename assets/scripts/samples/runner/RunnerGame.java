package samples.runner;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Rules of the lane runner: blocks come down the lanes towards the "Player", faster and faster; touching one ends
 * the run. Lane markers (entities whose name starts with "Marker") stream past to show the speed. R restarts.
 *
 * <p>Blocks are pooled: created once, then reused when they pass the player.
 */
public class RunnerGame extends ScriptComponent {
    static final int BLOCK_POOL = 12;
    /** Edge length of a block. */
    static final float BLOCK_SIZE = 90f;
    /** Where blocks appear (far ahead) and where they are recycled (behind the camera). */
    static final float SPAWN_Z = -3600f;
    static final float RECYCLE_Z = 500f;
    /** World speed at the start, its gain per second and its limit, in units per second. */
    static final float START_SPEED = 900f;
    static final float SPEED_GAIN = 30f;
    static final float MAX_SPEED = 2400f;
    /** Distance between blocks along the track, before randomness. */
    static final float BLOCK_SPACING = 520f;
    /** Lane marker spacing; markers wrap around after the last one. */
    static final float MARKER_SPACING = 400f;
    /** Half the player's size: the player is a 50-unit cube. */
    static final float PLAYER_HALF_SIZE = 25f;
    /** Units per displayed metre. */
    static final float UNITS_PER_METRE = 100f;
    /** Fixed seed: the same course every run, which also keeps tests repeatable. */
    static final long RANDOM_SEED = 7L;

    private final List<Entity> blocks = new ArrayList<>();
    private final List<Entity> markers = new ArrayList<>();
    private Random random;
    private Entity player;
    private TextComponent distanceText;
    private TextComponent messageText;
    private float speed;
    private float distance;
    private float nextBlockIn;
    private boolean gameOver;

    @Override
    public void onStart() {
        super.onStart();
        player = getScene().findEntityByName("Player");
        distanceText = getScene().findEntityByName("Distance Text").getComponent(TextComponent.class);
        messageText = getScene().findEntityByName("Message Text").getComponent(TextComponent.class);
        for (Entity entity : getScene().getAllEntities()) {
            if (entity.getName().startsWith("Marker")) {
                markers.add(entity);
            }
        }
        for (int i = 0; i < BLOCK_POOL; i++) {
            Entity block = getScene().createEntity("Block " + i);
            block.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX,
                BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE, new Color(0.9f, 0.25f, 0.25f, 1f)));
            block.setActive(false);
            blocks.add(block);
        }
        restart();
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        if (gameOver) {
            return;
        }
        speed = Math.min(MAX_SPEED, speed + SPEED_GAIN * deltaTime);
        float travel = speed * deltaTime;
        distance += travel;
        distanceText.setText(String.format(Locale.ROOT, "Distance: %d m", (int) (distance / UNITS_PER_METRE)));

        nextBlockIn -= travel;
        if (nextBlockIn <= 0f) {
            spawnBlock();
            nextBlockIn = BLOCK_SPACING * (0.6f + random.nextFloat());
        }
        Vector3 playerPosition = player.getTransform().getWorldPosition();
        for (Entity block : blocks) {
            if (!block.isActiveSelf()) {
                continue;
            }
            Vector3 position = block.getTransform().getLocalPosition();
            position.z += travel;
            block.getTransform().setLocalPosition(position);
            if (position.z > RECYCLE_Z) {
                block.setActive(false);
            } else if (touches(playerPosition, position)) {
                gameOver = true;
                messageText.setText(String.format(Locale.ROOT, "Crashed after %d m. Press R to run again",
                    (int) (distance / UNITS_PER_METRE)));
            }
        }
        float wrap = MARKER_SPACING * markers.size();
        for (Entity marker : markers) {
            Vector3 position = marker.getTransform().getLocalPosition();
            position.z += travel;
            if (position.z > RECYCLE_Z) {
                position.z -= wrap;
            }
            marker.getTransform().setLocalPosition(position);
        }
    }

    private boolean touches(Vector3 playerPosition, Vector3 blockPosition) {
        float reach = PLAYER_HALF_SIZE + BLOCK_SIZE / 2f;
        return Math.abs(playerPosition.x - blockPosition.x) < reach
            && Math.abs(playerPosition.z - blockPosition.z) < reach
            && playerPosition.y - PLAYER_HALF_SIZE < blockPosition.y + BLOCK_SIZE / 2f;
    }

    private void spawnBlock() {
        for (Entity block : blocks) {
            if (!block.isActiveSelf()) {
                float laneX = RunnerPlayer.LANES[random.nextInt(RunnerPlayer.LANES.length)];
                block.getTransform().setLocalPosition(laneX, BLOCK_SIZE / 2f, SPAWN_Z);
                block.setActive(true);
                return;
            }
        }
    }

    private void restart() {
        for (Entity block : blocks) {
            block.setActive(false);
        }
        random = new Random(RANDOM_SEED);
        speed = START_SPEED;
        distance = 0f;
        nextBlockIn = BLOCK_SPACING;
        gameOver = false;
        messageText.setText("");
        distanceText.setText("Distance: 0 m");
    }
}
