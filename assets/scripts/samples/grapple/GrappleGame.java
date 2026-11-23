package samples.grapple;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import samples.common.SideBody;

import java.util.ArrayList;
import java.util.List;

/**
 * A 2.5D grappling and physics-puzzle level. Jump, then hold J (or Shift) to hook onto the nearest "Anchor..." above you in
 * range and swing; A/D pump the swing; let go to fly. Push the "Crate..." onto the "Plate" to raise the "Gate".
 * "Platform..." boxes and the gate are solid; touching a "Checkpoint..." sets where you return after a fall.
 *
 * <p>The swing is a pendulum: gravity and input change the velocity, then the position is pulled back onto the
 * rope's circle and the outward speed removed. Collision still uses {@link SideBody}, so swinging into a wall stops.
 */
public class GrappleGame extends ScriptComponent {
    static final float RUN_SPEED = 300f;
    static final float JUMP_SPEED = 780f;
    static final float GRAVITY = 2000f;
    static final float FALL_LIMIT = -500f;
    /** Longest rope; anchors farther away cannot be hooked. */
    static final float GRAPPLE_RANGE = 480f;
    /** Sideways acceleration from A/D while swinging. */
    static final float SWING_PUMP = 900f;
    /** How far the gate rises when the plate is pressed, and its speed. */
    static final float GATE_LIFT = 320f;
    static final float GATE_SPEED = 260f;

    private final List<SideBody.Solid> solids = new ArrayList<>();
    private final List<SideBody.Solid> anchors = new ArrayList<>();
    private final List<SideBody.Solid> checkpoints = new ArrayList<>();
    private final Vector3 swing = new Vector3();
    private SideBody body;
    private SideBody crate;
    private Entity crateEntity;
    private float crateStartX;
    private float crateStartY;
    private SideBody.Solid plate;
    private SideBody.Solid gate;
    private float gateBaseY;
    private SideBody.Solid goal;
    private Entity player;
    private Entity rope;
    private TextComponent statusText;
    private TextComponent messageText;
    private SideBody.Solid hooked;
    private float ropeLength;
    private float startX;
    private float startY;
    private float respawnX;
    private float respawnY;
    private boolean finished;

    @Override
    public void onStart() {
        super.onStart();
        solids.addAll(SideBody.solidsNamed(getScene(), "Platform", "Gate"));
        anchors.addAll(SideBody.solidsNamed(getScene(), "Anchor"));
        checkpoints.addAll(SideBody.solidsNamed(getScene(), "Checkpoint"));
        for (SideBody.Solid solid : solids) {
            if (solid.entity.getName().equals("Gate")) {
                gate = solid;
            }
        }
        gateBaseY = gate.y;
        crateEntity = getScene().findEntityByName("Crate");
        SideBody.Solid crateSolid = new SideBody.Solid(crateEntity);
        crate = new SideBody(crateSolid.x, crateSolid.y, crateSolid.halfWidth, crateSolid.halfHeight);
        crate.self = crateSolid;
        crateStartX = crate.x;
        crateStartY = crate.y;
        solids.add(crateSolid);
        plate = new SideBody.Solid(getScene().findEntityByName("Plate"));
        goal = new SideBody.Solid(getScene().findEntityByName("Goal"));
        player = getScene().findEntityByName("Player");
        SideBody.Solid playerBox = new SideBody.Solid(player);
        body = new SideBody(playerBox.x, playerBox.y, playerBox.halfWidth, playerBox.halfHeight);
        startX = playerBox.x;
        startY = playerBox.y;
        rope = getScene().findEntityByName("Rope");
        statusText = getScene().findEntityByName("Status Text").getComponent(TextComponent.class);
        messageText = getScene().findEntityByName("Message Text").getComponent(TextComponent.class);
        restart();
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        updateGate(deltaTime);
        if (finished) {
            return;
        }
        float direction = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) direction -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) direction += 1f;
        boolean grappleHeld = Gdx.input.isKeyPressed(Keys.J) || Gdx.input.isKeyPressed(Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT);

        if (grappleHeld && hooked == null && !body.grounded) {
            hooked = nearestAnchor();
            if (hooked != null) {
                ropeLength = Vector3.len(body.x - hooked.x, body.y - hooked.y, 0f);
                AudioSourceComponent.play(getScene(), "grapple");
            }
        }
        if (!grappleHeld) {
            hooked = null;
        }

        if (hooked != null) {
            swingStep(direction, deltaTime);
        } else {
            body.velocityX = direction * RUN_SPEED;
            if (body.grounded && (Gdx.input.isKeyJustPressed(Keys.SPACE) || Gdx.input.isKeyJustPressed(Keys.W)
                || Gdx.input.isKeyJustPressed(Keys.UP))) {
                body.velocityY = JUMP_SPEED;
                AudioSourceComponent.play(getScene(), "jump");
            }
            body.step(deltaTime, GRAVITY, solids, this::pushCrate);
        }
        stepCrate(deltaTime);
        body.moveEntity(player, 0f);
        updateRope();

        for (SideBody.Solid checkpoint : checkpoints) {
            if (body.touches(checkpoint.x, checkpoint.y, checkpoint.halfWidth, checkpoint.halfHeight)) {
                respawnX = checkpoint.x;
                respawnY = checkpoint.y + 20f;
            }
        }
        if (body.y < FALL_LIMIT) {
            hooked = null;
            AudioSourceComponent.play(getScene(), "hurt");
            body.x = respawnX;
            body.y = respawnY;
            body.velocityX = 0f;
            body.velocityY = 0f;
        }
        if (body.touches(goal.x, goal.y, goal.halfWidth, goal.halfHeight)) {
            finished = true;
            AudioSourceComponent.play(getScene(), "win");
            messageText.setText("You crossed the gorge! Press R to play again");
        }
    }

    /** Pendulum motion around the hooked anchor, then collision through {@link SideBody}. */
    private void swingStep(float direction, float deltaTime) {
        float vx = body.velocityX + direction * SWING_PUMP * deltaTime;
        float vy = body.velocityY - GRAVITY * deltaTime;
        // Where the player would go, pulled back onto the rope's circle if the rope is taut
        swing.set(body.x + vx * deltaTime - hooked.x, body.y + vy * deltaTime - hooked.y, 0f);
        if (swing.len() > ropeLength) {
            swing.setLength(ropeLength);
        }
        float targetX = hooked.x + swing.x;
        float targetY = hooked.y + swing.y;
        body.velocityX = (targetX - body.x) / deltaTime;
        body.velocityY = (targetY - body.y) / deltaTime;
        body.moveX(targetX - body.x, solids, null);
        body.moveY(targetY - body.y, solids);
    }

    private SideBody.Solid nearestAnchor() {
        SideBody.Solid best = null;
        float bestDistance = GRAPPLE_RANGE;
        for (SideBody.Solid anchor : anchors) {
            float distance = Vector3.len(body.x - anchor.x, body.y - anchor.y, 0f);
            if (anchor.y > body.y && distance < bestDistance) {
                best = anchor;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Shows the rope as a thin box stretched from the player to the anchor. */
    private void updateRope() {
        rope.setActive(hooked != null);
        if (hooked == null) {
            return;
        }
        float dx = hooked.x - body.x;
        float dy = hooked.y - body.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        rope.getTransform().setLocalPosition(body.x + dx / 2f, body.y + dy / 2f, 0f);
        // The rope box is 1 unit tall along its local Y axis; roll it towards the anchor and stretch it
        rope.getTransform().setLocalEulerAngles(0f, 0f, MathUtils.atan2(-dx, dy) * MathUtils.radiansToDegrees);
        rope.getTransform().setLocalScale(1f, length, 1f);
    }

    /** The plate is pressed while the player or the crate rests on it; the gate rises while pressed. */
    private void updateGate(float deltaTime) {
        boolean pressed = restsOn(body, plate) || restsOn(crate, plate);
        float target = gateBaseY + (pressed ? GATE_LIFT : 0f);
        float y = gate.entity.getTransform().getLocalPosition().y;
        float step = GATE_SPEED * deltaTime;
        y = Math.abs(target - y) <= step ? target : y + Math.signum(target - y) * step;
        gate.entity.getTransform().setLocalPosition(gate.x, y, 0f);
        gate.refresh();
        statusText.setText(pressed ? "Gate: open" : "Gate: closed");
    }

    private static boolean restsOn(SideBody body, SideBody.Solid plate) {
        return Math.abs(body.x - plate.x) < plate.halfWidth
            && Math.abs((body.y - body.halfHeight) - (plate.y + plate.halfHeight)) < 30f;
    }

    private boolean pushCrate(SideBody.Solid solid, float amount) {
        if (solid != crate.self) {
            return false;
        }
        crate.moveX(amount, solids, null);
        solid.x = crate.x;
        crateEntity.getTransform().setLocalPosition(crate.x, crate.y, 0f);
        return true;
    }

    private void stepCrate(float deltaTime) {
        crate.velocityX = 0f;
        crate.step(deltaTime, GRAVITY, solids, null);
        if (crate.y < FALL_LIMIT) {
            crate.x = crateStartX;
            crate.y = crateStartY;
            crate.velocityY = 0f;
        }
        crate.moveEntity(crateEntity, 0f);
        crate.self.refresh();
    }

    private void restart() {
        body.x = startX;
        body.y = startY;
        body.velocityX = 0f;
        body.velocityY = 0f;
        respawnX = startX;
        respawnY = startY;
        crate.x = crateStartX;
        crate.y = crateStartY;
        crate.velocityY = 0f;
        crate.moveEntity(crateEntity, 0f);
        crate.self.refresh();
        hooked = null;
        finished = false;
        body.moveEntity(player, 0f);
        updateRope();
        messageText.setText("");
    }
}
