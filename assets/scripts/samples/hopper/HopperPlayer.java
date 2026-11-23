package samples.hopper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 3D platform hopper: WASD or the arrow keys move (W is away from the camera), Space jumps. Boxes named
 * "Platform..." and the "Goal" box are solid; landing on top of "Goal" wins. Falling off sends the player back to the
 * start. Collision is axis-aligned boxes written here, so every rule is easy to change.
 */
public class HopperPlayer extends ScriptComponent {
    static final float MOVE_SPEED = 330f;
    static final float JUMP_SPEED = 820f;
    static final float GRAVITY = 2100f;
    static final float MAX_FALL_SPEED = 1400f;
    /** Below this height the player has fallen off. */
    static final float FALL_LIMIT = -600f;
    /** Boxes that only touch do not count as overlapping (protects against rounding while standing on a platform). */
    static final float SKIN = 0.01f;

    /** A solid box: centre and half sizes. */
    record Box(Entity entity, Vector3 centre, Vector3 half) {
        boolean overlaps(Vector3 p, Vector3 h) {
            return Math.abs(p.x - centre.x) < h.x + half.x - SKIN
                && Math.abs(p.y - centre.y) < h.y + half.y - SKIN
                && Math.abs(p.z - centre.z) < h.z + half.z - SKIN;
        }
    }

    private final List<Box> solids = new ArrayList<>();
    private final Vector3 start = new Vector3();
    private final Vector3 position = new Vector3();
    private final Vector3 half = new Vector3();
    private final Vector3 velocity = new Vector3();
    private Box goal;
    private TextComponent fallsText;
    private TextComponent timeText;
    private TextComponent messageText;
    private boolean grounded;
    private boolean finished;
    private int falls;
    private float elapsed;

    @Override
    public void onStart() {
        super.onStart();
        for (Entity entity : getScene().getAllEntities()) {
            if (entity.getName().startsWith("Platform") || entity.getName().equals("Goal")) {
                Box box = boxOf(entity);
                solids.add(box);
                if (entity.getName().equals("Goal")) {
                    goal = box;
                }
            }
        }
        if (goal == null) {
            throw new IllegalStateException("HopperPlayer needs a box named 'Goal'");
        }
        Box self = boxOf(getEntity());
        half.set(self.half());
        start.set(self.centre());
        fallsText = getScene().findEntityByName("Falls Text").getComponent(TextComponent.class);
        timeText = getScene().findEntityByName("Time Text").getComponent(TextComponent.class);
        messageText = getScene().findEntityByName("Message Text").getComponent(TextComponent.class);
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
        elapsed += deltaTime;
        timeText.setText(String.format(Locale.ROOT, "Time: %.1f", elapsed));

        float inputX = 0f;
        float inputZ = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) inputX -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) inputX += 1f;
        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP)) inputZ -= 1f;
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN)) inputZ += 1f;
        float length = (float) Math.sqrt(inputX * inputX + inputZ * inputZ);
        if (length > 0f) {
            inputX /= length;
            inputZ /= length;
        }
        if (grounded && Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            velocity.y = JUMP_SPEED;
            AudioSourceComponent.play(getScene(), "jump");
        }
        velocity.y = Math.max(velocity.y - GRAVITY * deltaTime, -MAX_FALL_SPEED);

        // One axis at a time, so the player slides along walls and lands cleanly
        moveAxis(0, inputX * MOVE_SPEED * deltaTime);
        moveAxis(2, inputZ * MOVE_SPEED * deltaTime);
        grounded = false;
        Box landedOn = moveAxis(1, velocity.y * deltaTime);
        getTransform().setLocalPosition(position);

        if (landedOn == goal) {
            finished = true;
            AudioSourceComponent.play(getScene(), "win");
            messageText.setText(String.format(Locale.ROOT, "Goal! %.1f s with %d falls. Press R to play again", elapsed, falls));
        }
        if (position.y < FALL_LIMIT) {
            falls++;
            AudioSourceComponent.play(getScene(), "hurt");
            fallsText.setText("Falls: " + falls);
            position.set(start);
            velocity.setZero();
        }
    }

    /**
     * Moves along one axis (0 = x, 1 = y, 2 = z) and pushes the player out of any solid it entered.
     *
     * @return the box landed on when moving down onto it, otherwise null
     */
    private Box moveAxis(int axis, float amount) {
        if (amount == 0f) {
            return null;
        }
        add(position, axis, amount);
        Box landedOn = null;
        for (Box box : solids) {
            if (!box.overlaps(position, half)) {
                continue;
            }
            float boxCentre = get(box.centre(), axis);
            float reach = get(box.half(), axis) + get(half, axis);
            set(position, axis, amount > 0f ? boxCentre - reach : boxCentre + reach);
            if (axis == 1) {
                if (amount < 0f) {
                    grounded = true;
                    landedOn = box;
                }
                velocity.y = 0f;
            }
        }
        return landedOn;
    }

    private void restart() {
        position.set(start);
        velocity.setZero();
        falls = 0;
        elapsed = 0f;
        finished = false;
        getTransform().setLocalPosition(position);
        fallsText.setText("Falls: 0");
        messageText.setText("");
    }

    /** Box of an entity's Mesh Primitive at its world position, including the entity's scale. */
    static Box boxOf(Entity entity) {
        MeshPrimitiveComponent mesh = entity.getComponent(MeshPrimitiveComponent.class);
        if (mesh == null) {
            throw new IllegalStateException("'" + entity.getName() + "' needs a Mesh Primitive to have a size");
        }
        Vector3 size = mesh.getSize().scl(entity.getTransform().getWorldScale()).scl(0.5f);
        return new Box(entity, entity.getTransform().getWorldPosition(), size);
    }

    private static float get(Vector3 v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }

    private static void set(Vector3 v, int axis, float value) {
        if (axis == 0) v.x = value; else if (axis == 1) v.y = value; else v.z = value;
    }

    private static void add(Vector3 v, int axis, float amount) {
        set(v, axis, get(v, axis) + amount);
    }
}
