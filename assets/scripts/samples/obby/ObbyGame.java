package samples.obby;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
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
 * An obstacle course in the style of a block-building online game: a blocky character runs and jumps along a course of
 * bright parts above the void, through stages with checkpoints. WASD runs (relative to the camera), the arrow keys turn the
 * camera round the character and tilt it, Space jumps and R starts over.
 *
 * <p>Every part is an entity, and its name says what it does, so the course is built and changed in the editor:
 * <ul>
 *   <li>{@code Platform ...}: solid; {@code Checkpoint n}: solid, and standing on it makes it the place you come back to;</li>
 *   <li>{@code Fade ...}: solid until you have stood on it for a moment, then it drops out and returns later;</li>
 *   <li>{@code Mover X ...}, {@code Mover Y ...}, {@code Mover Z ...}: solid and slide back and forth along that axis,
 *       carrying whoever stands on them;</li>
 *   <li>{@code Kill ...}: touching it sends you back; {@code Spinner ...}: a bar that turns round and does the same;</li>
 *   <li>{@code Pad ...}: solid, and launches you into the air; {@code Coin ...}: collected by touching;
 *       {@code Finish}: solid, and landing on it wins.</li>
 * </ul>
 * The entity "Player" is the character's centre; its limbs hang from pivot entities named "Arm L Pivot", "Arm R Pivot",
 * "Leg L Pivot" and "Leg R Pivot". Put this script on one entity; it also moves the entity named "Main Camera".
 */
public class ObbyGame extends ScriptComponent {
    /** Half sizes of the character's collision box. */
    static final float HALF_WIDTH = 28f;
    static final float HALF_HEIGHT = 78f;
    /** Run speed, jump speed and gravity, in units per second (and squared). */
    static final float RUN_SPEED = 400f;
    static final float JUMP_SPEED = 900f;
    static final float GRAVITY = 2400f;
    static final float MAX_FALL_SPEED = 1800f;
    /** Largest frame time simulated in one step. */
    static final float MAX_STEP = 1f / 30f;
    /** Below this height the character has fallen into the void. */
    static final float FALL_LIMIT = -1000f;
    /** Boxes that only touch do not count as overlapping. */
    static final float SKIN = 0.01f;
    /** Launch speed of a {@code Pad}. */
    static final float PAD_SPEED = 1500f;
    /** How far a part with the name {@code Mover ...} slides each way, how long a full there-and-back takes, in seconds. */
    static final float MOVER_REACH = 260f;
    static final float MOVER_PERIOD = 5f;
    /** How long a {@code Fade} part holds after being stood on, and how long it stays gone, in seconds. */
    static final float FADE_DELAY = 0.8f;
    static final float FADE_GONE_TIME = 3f;
    /** What a part that is about to drop turns into. */
    static final Color FADE_WARNING = new Color(0.95f, 0.3f, 0.25f, 1f);
    /** Spinner turn rate in degrees per second; every second spinner turns the other way. */
    static final float SPIN_RATE = 75f;
    /** Coins turn at this rate, in degrees per second. */
    static final float COIN_SPIN = 180f;
    /** Camera: distance from the character, height of the point it looks at, tilt limits and turn rates (degrees per second). */
    static final float CAMERA_DISTANCE = 620f;
    static final float CAMERA_LOOK_HEIGHT = 40f;
    static final float CAMERA_TURN_RATE = 110f;
    static final float CAMERA_TILT_RATE = 50f;
    static final float CAMERA_MIN_TILT = 5f;
    static final float CAMERA_MAX_TILT = 70f;
    static final float CAMERA_START_TILT = 22f;
    /** How fast the character turns to face where it runs, in degrees per second. */
    static final float TURN_TO_FACE_RATE = 900f;
    /** Limb swing: degrees either way, and swings per second at full speed. */
    static final float SWING_ANGLE = 55f;
    static final float SWING_RATE = 2.2f;

    private enum Kind { PLATFORM, FADE, MOVER, KILL, SPINNER, PAD, CHECKPOINT, COIN, FINISH }

    /** One named part of the course. */
    private static final class Part {
        final Entity entity;
        final Kind kind;
        final Vector3 origin = new Vector3();
        final Vector3 centre = new Vector3();
        final Vector3 half = new Vector3();
        final int axis;
        final int index;
        final Color colour;
        boolean solid;
        boolean taken;
        float timer;
        boolean fading;
        /** Current turn of a spinner, degrees around Y. */
        float angle;

        Part(Entity entity, Kind kind, int axis, int index) {
            this.entity = entity;
            this.kind = kind;
            this.axis = axis;
            this.index = index;
            MeshPrimitiveComponent mesh = entity.getComponent(MeshPrimitiveComponent.class);
            if (mesh == null) {
                throw new IllegalStateException("'" + entity.getName() + "' needs a Mesh Primitive to have a size");
            }
            origin.set(entity.getTransform().getWorldPosition());
            centre.set(origin);
            half.set(mesh.getSize()).scl(entity.getTransform().getWorldScale()).scl(0.5f);
            colour = new Color(mesh.getColor());
            solid = kind == Kind.PLATFORM || kind == Kind.FADE || kind == Kind.MOVER || kind == Kind.PAD
                || kind == Kind.CHECKPOINT || kind == Kind.FINISH;
        }

        boolean overlaps(Vector3 p, float padding) {
            return Math.abs(p.x - centre.x) < HALF_WIDTH + half.x - SKIN + padding
                && Math.abs(p.y - centre.y) < HALF_HEIGHT + half.y - SKIN + padding
                && Math.abs(p.z - centre.z) < HALF_WIDTH + half.z - SKIN + padding;
        }
    }

    private final List<Part> parts = new ArrayList<>();
    private final List<Part> coins = new ArrayList<>();
    private final Vector3 position = new Vector3();
    private final Vector3 velocity = new Vector3();
    private final Vector3 spawn = new Vector3();
    private final Vector3 start = new Vector3();
    private Entity camera;
    private Entity avatar;
    private Entity armLeft;
    private Entity armRight;
    private Entity legLeft;
    private Entity legRight;
    private TextComponent stageText;
    private TextComponent coinsText;
    private TextComponent timeText;
    private TextComponent deathsText;
    private TextComponent messageText;
    private Part support;
    private int stage;
    private int stages;
    private int collected;
    private int deaths;
    private float elapsed;
    private float clock;
    private float heading;
    private float swingPhase;
    private float cameraYaw;
    private float cameraTilt;
    private boolean grounded;
    private boolean running;
    private boolean finished;

    @Override
    public void onStart() {
        super.onStart();
        for (Entity entity : getScene().getAllEntities()) {
            Part part = partOf(entity);
            if (part != null) {
                parts.add(part);
                if (part.kind == Kind.COIN) {
                    coins.add(part);
                }
                if (part.kind == Kind.CHECKPOINT) {
                    stages = Math.max(stages, part.index + 1);
                }
            }
        }
        camera = require("Main Camera");
        avatar = getEntity();
        armLeft = require("Arm L Pivot");
        armRight = require("Arm R Pivot");
        legLeft = require("Leg L Pivot");
        legRight = require("Leg R Pivot");
        stageText = text("Stage Text");
        coinsText = text("Coins Text");
        timeText = text("Time Text");
        deathsText = text("Deaths Text");
        messageText = text("Message Text");
        start.set(avatar.getTransform().getWorldPosition());
        restart();
    }

    private Entity require(String name) {
        Entity entity = getScene().findEntityByName(name);
        if (entity == null) {
            throw new IllegalStateException("ObbyGame needs an entity named '" + name + "'");
        }
        return entity;
    }

    private TextComponent text(String name) {
        Entity entity = require(name);
        if (entity.getComponent(TextComponent.class) == null) {
            throw new IllegalStateException("'" + name + "' needs a Text component");
        }
        return entity.getComponent(TextComponent.class);
    }

    /** The part an entity is, from its name, or null for entities that are not parts. */
    private static Part partOf(Entity entity) {
        String name = entity.getName();
        if (entity.getComponent(MeshPrimitiveComponent.class) == null) {
            return null;
        }
        if (name.startsWith("Platform")) return new Part(entity, Kind.PLATFORM, 0, 0);
        if (name.startsWith("Fade")) return new Part(entity, Kind.FADE, 0, 0);
        if (name.startsWith("Kill")) return new Part(entity, Kind.KILL, 0, 0);
        if (name.startsWith("Pad")) return new Part(entity, Kind.PAD, 0, 0);
        if (name.startsWith("Coin")) return new Part(entity, Kind.COIN, 0, 0);
        if (name.equals("Finish")) return new Part(entity, Kind.FINISH, 0, 0);
        if (name.startsWith("Spinner")) return new Part(entity, Kind.SPINNER, 0, numberOf(name));
        if (name.startsWith("Checkpoint")) return new Part(entity, Kind.CHECKPOINT, 0, numberOf(name));
        if (name.startsWith("Mover")) {
            String[] words = name.split(" ");
            int axis = words.length > 1 && words[1].equals("X") ? 0 : words.length > 1 && words[1].equals("Y") ? 1 : 2;
            return new Part(entity, Kind.MOVER, axis, numberOf(name));
        }
        return null;
    }

    /** The last number in a name, such as 3 in "Checkpoint 3". */
    private static int numberOf(String name) {
        String[] words = name.split(" ");
        try {
            return Integer.parseInt(words[words.length - 1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- Each frame ---

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        float step = Math.min(deltaTime, MAX_STEP);
        steerCamera(step);
        if (!finished) {
            if (running) {
                elapsed += step;
            }
            clock += step;
            Vector3 carried = updateParts(step);
            play(step, carried);
        }
        updateAvatar(step);
        updateHud();
    }

    private void steerCamera(float deltaTime) {
        if (Gdx.input.isKeyPressed(Keys.LEFT)) cameraYaw += CAMERA_TURN_RATE * deltaTime;
        if (Gdx.input.isKeyPressed(Keys.RIGHT)) cameraYaw -= CAMERA_TURN_RATE * deltaTime;
        if (Gdx.input.isKeyPressed(Keys.UP)) cameraTilt -= CAMERA_TILT_RATE * deltaTime;
        if (Gdx.input.isKeyPressed(Keys.DOWN)) cameraTilt += CAMERA_TILT_RATE * deltaTime;
        cameraTilt = MathUtils.clamp(cameraTilt, CAMERA_MIN_TILT, CAMERA_MAX_TILT);
    }

    /** Moves the sliding, dropping and turning parts; returns how far the part the character stands on moved this frame. */
    private Vector3 updateParts(float deltaTime) {
        Vector3 carried = new Vector3();
        for (Part part : parts) {
            switch (part.kind) {
                case MOVER -> {
                    float offset = MOVER_REACH * MathUtils.sin(MathUtils.PI2 * (clock / MOVER_PERIOD + part.index * 0.25f));
                    float before = axisValue(part.centre, part.axis);
                    part.centre.set(part.origin);
                    addAxis(part.centre, part.axis, offset);
                    part.entity.getTransform().setLocalPosition(part.centre);
                    if (part == support) {
                        addAxis(carried, part.axis, axisValue(part.centre, part.axis) - before);
                    }
                }
                case FADE -> updateFade(part, deltaTime);
                case SPINNER -> {
                    part.angle = SPIN_RATE * clock * (part.index % 2 == 0 ? -1f : 1f);
                    part.entity.getTransform().setLocalEulerAngles(part.angle, 0f, 0f);
                }
                case COIN -> {
                    if (!part.taken) {
                        part.entity.getTransform().setLocalEulerAngles(COIN_SPIN * clock, 0f, 0f);
                    }
                }
                default -> { }
            }
        }
        return carried;
    }

    private void updateFade(Part part, float deltaTime) {
        if (!part.fading) {
            return;
        }
        part.timer -= deltaTime;
        if (part.timer > 0f) {
            return;
        }
        if (part.solid) {
            part.solid = false;
            part.entity.setActive(false);
            part.timer = FADE_GONE_TIME;
        } else {
            part.solid = true;
            part.fading = false;
            part.entity.setActive(true);
            part.entity.getComponent(MeshPrimitiveComponent.class).setColor(part.colour);
        }
    }

    private void play(float deltaTime, Vector3 carried) {
        float ahead = 0f;
        float right = 0f;
        if (Gdx.input.isKeyPressed(Keys.W)) ahead += 1f;
        if (Gdx.input.isKeyPressed(Keys.S)) ahead -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D)) right += 1f;
        if (Gdx.input.isKeyPressed(Keys.A)) right -= 1f;
        float length = (float) Math.sqrt(ahead * ahead + right * right);
        if (length > 0f) {
            ahead /= length;
            right /= length;
            running = true;
        }
        float radians = cameraYaw * MathUtils.degreesToRadians;
        float sin = MathUtils.sin(radians);
        float cos = MathUtils.cos(radians);
        // Away from the camera is (-sin, -cos); to its right is (cos, -sin)
        float runX = -sin * ahead + cos * right;
        float runZ = -cos * ahead - sin * right;
        if (length > 0f) {
            heading = approachAngle(heading, MathUtils.atan2(-runX, -runZ) * MathUtils.radiansToDegrees,
                TURN_TO_FACE_RATE * deltaTime);
        }
        swingPhase += (length > 0f && grounded ? SWING_RATE : 0f) * deltaTime;

        position.add(carried);
        if (grounded && Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            velocity.y = JUMP_SPEED;
            AudioSourceComponent.play(getScene(), "jump");
        }
        velocity.y = Math.max(velocity.y - GRAVITY * deltaTime, -MAX_FALL_SPEED);
        moveAxis(0, runX * RUN_SPEED * deltaTime);
        moveAxis(2, runZ * RUN_SPEED * deltaTime);
        grounded = false;
        support = moveAxis(1, velocity.y * deltaTime);
        if (support != null) {
            landedOn(support);
        }
        touch();
    }

    private void landedOn(Part part) {
        switch (part.kind) {
            case FADE -> {
                if (!part.fading) {
                    part.fading = true;
                    part.timer = FADE_DELAY;
                    part.entity.getComponent(MeshPrimitiveComponent.class).setColor(FADE_WARNING);
                }
            }
            case PAD -> {
                velocity.y = PAD_SPEED;
                grounded = false;
                AudioSourceComponent.play(getScene(), "bounce");
            }
            case CHECKPOINT -> {
                if (part.index > stage) {
                    stage = part.index;
                    spawn.set(part.centre.x, part.centre.y + part.half.y + HALF_HEIGHT, part.centre.z);
                    AudioSourceComponent.play(getScene(), "checkpoint");
                }
            }
            case FINISH -> {
                finished = true;
                AudioSourceComponent.play(getScene(), "win");
                messageText.setText(String.format(Locale.ROOT, "Course complete! %.1f s, %d deaths, %d of %d coins. Press R to run it again",
                    elapsed, deaths, collected, coins.size()));
            }
            default -> { }
        }
    }

    /** Hazards and coins: things the character only has to touch. */
    private void touch() {
        for (Part part : parts) {
            if (part.kind == Kind.KILL && part.entity.isActiveSelf() && part.overlaps(position, 0f)) {
                die();
                return;
            }
            if (part.kind == Kind.SPINNER && hitBySpinner(part)) {
                die();
                return;
            }
            if (part.kind == Kind.COIN && !part.taken && part.overlaps(position, 0f)) {
                part.taken = true;
                part.entity.setActive(false);
                collected++;
                AudioSourceComponent.play(getScene(), "coin");
            }
        }
        if (position.y < FALL_LIMIT) {
            die();
        }
    }

    /** Whether the turning bar reaches the character: its position seen from the bar's own turned frame. */
    private boolean hitBySpinner(Part bar) {
        if (Math.abs(position.y - bar.centre.y) >= HALF_HEIGHT + bar.half.y) {
            return false;
        }
        float radians = bar.angle * MathUtils.degreesToRadians;
        float dx = position.x - bar.centre.x;
        float dz = position.z - bar.centre.z;
        float localX = dx * MathUtils.cos(radians) - dz * MathUtils.sin(radians);
        float localZ = dx * MathUtils.sin(radians) + dz * MathUtils.cos(radians);
        return Math.abs(localX) < bar.half.x + HALF_WIDTH && Math.abs(localZ) < bar.half.z + HALF_WIDTH;
    }

    private void die() {
        deaths++;
        AudioSourceComponent.play(getScene(), "hurt");
        position.set(spawn);
        velocity.setZero();
        support = null;
    }

    /**
     * Moves along one axis (0 = x, 1 = y, 2 = z) and pushes the character out of any solid part it entered.
     *
     * @return the part landed on when moving down onto it, otherwise null
     */
    private Part moveAxis(int axis, float amount) {
        if (amount == 0f) {
            return null;
        }
        addAxis(position, axis, amount);
        Part landedOn = null;
        for (Part part : parts) {
            if (!part.solid || !part.overlaps(position, 0f)) {
                continue;
            }
            float reach = axisValue(part.half, axis) + (axis == 1 ? HALF_HEIGHT : HALF_WIDTH);
            float centre = axisValue(part.centre, axis);
            setAxis(position, axis, amount > 0f ? centre - reach : centre + reach);
            if (axis == 1) {
                if (amount < 0f) {
                    grounded = true;
                    landedOn = part;
                }
                velocity.y = 0f;
            }
        }
        return landedOn;
    }

    // --- The character and the camera ---

    private void updateAvatar(float deltaTime) {
        avatar.getTransform().setLocalPosition(position);
        avatar.getTransform().setLocalEulerAngles(heading, 0f, 0f);
        float swing = SWING_ANGLE * MathUtils.sin(swingPhase * MathUtils.PI2);
        // Arms and legs swing against each other; in the air they stay out
        float legs = grounded ? swing : SWING_ANGLE * 0.5f;
        armLeft.getTransform().setLocalEulerAngles(0f, grounded ? -swing : -SWING_ANGLE, 0f);
        armRight.getTransform().setLocalEulerAngles(0f, grounded ? swing : SWING_ANGLE, 0f);
        legLeft.getTransform().setLocalEulerAngles(0f, legs, 0f);
        legRight.getTransform().setLocalEulerAngles(0f, -legs, 0f);

        float yaw = cameraYaw * MathUtils.degreesToRadians;
        float tilt = cameraTilt * MathUtils.degreesToRadians;
        float lookY = position.y + CAMERA_LOOK_HEIGHT;
        float cx = position.x + MathUtils.sin(yaw) * MathUtils.cos(tilt) * CAMERA_DISTANCE;
        float cy = lookY + MathUtils.sin(tilt) * CAMERA_DISTANCE;
        float cz = position.z + MathUtils.cos(yaw) * MathUtils.cos(tilt) * CAMERA_DISTANCE;
        camera.getTransform().setLocalPosition(cx, cy, cz);
        // Looking back at the character: yaw continues round, pitch tilts down by the same angle the camera is above
        camera.getTransform().setLocalEulerAngles(cameraYaw, -cameraTilt, 0f);
    }

    private void updateHud() {
        stageText.setText("Stage " + (stage + 1) + " / " + stages);
        coinsText.setText("Coins " + collected + " / " + coins.size());
        timeText.setText(String.format(Locale.ROOT, "Time %.1f", elapsed));
        deathsText.setText("Deaths " + deaths);
    }

    private void restart() {
        for (Part part : parts) {
            part.centre.set(part.origin);
            part.taken = false;
            part.fading = false;
            part.timer = 0f;
            part.angle = 0f;
            part.entity.getTransform().setLocalPosition(part.origin);
            part.entity.getTransform().setLocalEulerAngles(0f, 0f, 0f);
            part.entity.setActive(true);
            if (part.kind == Kind.FADE) {
                part.solid = true;
                part.entity.getComponent(MeshPrimitiveComponent.class).setColor(part.colour);
            }
        }
        position.set(start);
        spawn.set(start);
        velocity.setZero();
        support = null;
        stage = 0;
        collected = 0;
        deaths = 0;
        elapsed = 0f;
        clock = 0f;
        heading = 0f;
        swingPhase = 0f;
        cameraYaw = 0f;
        cameraTilt = CAMERA_START_TILT;
        grounded = false;
        running = false;
        finished = false;
        messageText.setText("");
        updateAvatar(0f);
        updateHud();
    }

    // --- Small helpers ---

    /** Turns {@code from} towards {@code to} (degrees) by at most {@code step}, the short way round. */
    private static float approachAngle(float from, float to, float step) {
        float difference = ((to - from) % 360f + 540f) % 360f - 180f;
        return from + MathUtils.clamp(difference, -step, step);
    }

    private static float axisValue(Vector3 v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }

    private static void setAxis(Vector3 v, int axis, float value) {
        if (axis == 0) v.x = value; else if (axis == 1) v.y = value; else v.z = value;
    }

    private static void addAxis(Vector3 v, int axis, float amount) {
        setAxis(v, axis, axisValue(v, axis) + amount);
    }
}
