package samples.fighter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

/**
 * One fighter on the stage: position, jumping, blocking, attacks and getting hit. The fighter moves only along X
 * (the side view) and always faces the opponent. Its entity is the body box; children named "Arm (name)" and
 * "Leg (name)" are moved to show punches and kicks.
 *
 * <p>Not a script by itself: {@link FighterGame} creates two and calls {@link #control} and {@link #update} once per
 * frame. Who is in control (keys or the computer) makes no difference here.
 */
public final class Fighter {
    /** A move's timing (seconds before it can hit, while it can hit, and afterwards), reach and effect on a hit. */
    public record Move(String name, float startup, float active, float recovery, float reach, int damage,
                       int blockedDamage, float hitStun, float knockback) {
        float total() {
            return startup + active + recovery;
        }
    }

    /** Fast and short: hard to punish. */
    public static final Move PUNCH = new Move("Punch", 0.08f, 0.1f, 0.15f, 75f, 6, 1, 0.25f, 220f);
    /** Slow and long: hurts, but a miss leaves the kicker open. */
    public static final Move KICK = new Move("Kick", 0.2f, 0.12f, 0.32f, 100f, 12, 2, 0.4f, 420f);

    public enum State { IDLE, BLOCK, ATTACK, HIT_STUN, KNOCKED_OUT }

    public static final int MAX_HEALTH = 100;
    /** Body size; the entity's centre is half the height above the feet. */
    public static final float WIDTH = 50f;
    public static final float HEIGHT = 110f;
    static final float WALK_SPEED = 260f;
    /** Walking backwards while blocking is slower. */
    static final float BLOCK_WALK_SPEED = 110f;
    static final float JUMP_SPEED = 720f;
    static final float GRAVITY = 2200f;
    /** How quickly knockback slides to a stop, in units per second squared. */
    static final float KNOCKBACK_FRICTION = 1800f;
    /** How long the body shows white after a hit. */
    static final float FLASH_TIME = 0.1f;
    /** The body is drawn this much darker while blocking. */
    static final float BLOCK_SHADE = 0.6f;
    /** Arm and leg positions relative to the body centre, before the facing is applied (+X is towards the opponent). */
    static final float ARM_REST_X = 22f;
    static final float ARM_REST_Y = 18f;
    static final float ARM_GUARD_Y = 40f;
    static final float LEG_REST_X = 12f;
    static final float LEG_REST_Y = -38f;
    static final float LEG_KICK_Y = -18f;
    /** How far past the body edge a limb reaches at full extension, relative to the move's reach. */
    static final float LIMB_HALF_LENGTH = 20f;

    public final String name;
    final Entity body;
    final Entity arm;
    final Entity leg;
    private final MeshPrimitiveComponent bodyMesh;
    private final Color color;
    private final Color blockColor;
    private final float startX;

    public float x;
    /** Height of the feet above the floor. */
    public float y;
    float velocityY;
    float knockbackSpeed;
    /** 1 when facing +X, -1 when facing -X. */
    public float facing = 1f;
    public int health;
    public State state;
    public Move move;
    /** Time since the current move started, or the hit stun left. */
    float timer;
    /** Whether the current move has already hit, so one move hits at most once. */
    boolean moveHit;
    private float flash;

    public Fighter(Scene scene, String name) {
        this.name = name;
        body = required(scene, name);
        arm = required(scene, "Arm (" + name + ")");
        leg = required(scene, "Leg (" + name + ")");
        bodyMesh = body.getComponent(MeshPrimitiveComponent.class);
        if (bodyMesh == null) {
            throw new IllegalStateException("'" + name + "' needs a Mesh Primitive (a box)");
        }
        color = new Color(bodyMesh.getColor());
        blockColor = new Color(color).mul(BLOCK_SHADE, BLOCK_SHADE, BLOCK_SHADE, 1f);
        startX = body.getTransform().getWorldPosition().x;
    }

    private static Entity required(Scene scene, String name) {
        Entity entity = scene.findEntityByName(name);
        if (entity == null) {
            throw new IllegalStateException("The fighter sample needs an entity named '" + name + "'");
        }
        return entity;
    }

    /** Back to the starting spot with full health, ready for a new round. */
    public void reset() {
        x = startX;
        y = 0f;
        velocityY = 0f;
        knockbackSpeed = 0f;
        health = MAX_HEALTH;
        state = State.IDLE;
        move = null;
        timer = 0f;
        flash = 0f;
        apply();
    }

    public boolean onGround() {
        return y <= 0f;
    }

    /** True while blocking or in a move or hit stun; such a fighter ignores walking input. */
    public boolean busy() {
        return state == State.ATTACK || state == State.HIT_STUN || state == State.KNOCKED_OUT;
    }

    /** True while the current move can hit. */
    public boolean hitting() {
        return state == State.ATTACK && !moveHit && timer >= move.startup() && timer < move.startup() + move.active();
    }

    /** True while the current move is winding up and has not hit yet; the computer reacts to this. */
    public boolean windingUp() {
        return state == State.ATTACK && timer < move.startup();
    }

    /**
     * One frame of input.
     *
     * @param walk  -1 left, 1 right, 0 still
     * @param jump  start a jump (only on the ground)
     * @param block hold to block; blocking only works on the ground and against attacks from the front
     * @param attack a move to start, or null
     */
    public void control(float walk, boolean jump, boolean block, Move attack, float deltaTime) {
        if (busy()) {
            return;
        }
        if (attack != null) {
            state = State.ATTACK;
            move = attack;
            timer = 0f;
            moveHit = false;
            return;
        }
        state = block && onGround() ? State.BLOCK : State.IDLE;
        float speed = state == State.BLOCK ? BLOCK_WALK_SPEED : WALK_SPEED;
        x += walk * speed * deltaTime;
        if (jump && onGround() && state != State.BLOCK) {
            velocityY = JUMP_SPEED;
        }
    }

    /** Gravity, knockback, move timing, and drawing the body, arm and leg. */
    public void update(float deltaTime) {
        velocityY -= GRAVITY * deltaTime;
        y = Math.max(0f, y + velocityY * deltaTime);
        if (onGround()) {
            velocityY = 0f;
        }
        if (knockbackSpeed != 0f) {
            x += knockbackSpeed * deltaTime;
            float slowed = Math.abs(knockbackSpeed) - KNOCKBACK_FRICTION * deltaTime;
            knockbackSpeed = slowed <= 0f ? 0f : Math.signum(knockbackSpeed) * slowed;
        }
        timer += deltaTime;
        if (state == State.ATTACK && timer >= move.total()) {
            state = State.IDLE;
            move = null;
        } else if (state == State.HIT_STUN && timer >= 0f) {
            state = State.IDLE;
        }
        flash -= deltaTime;
        apply();
    }

    /**
     * Applies a hit from {@code attacker}'s current move. A fighter blocking on the ground takes the move's blocked
     * damage and half the knockback; otherwise full damage, knockback and hit stun.
     *
     * @return true when the hit was blocked
     */
    public boolean takeHit(Fighter attacker) {
        Move hit = attacker.move;
        attacker.moveHit = true;
        boolean blocked = state == State.BLOCK;
        health = Math.max(0, health - (blocked ? hit.blockedDamage() : hit.damage()));
        knockbackSpeed = attacker.facing * hit.knockback() * (blocked ? 0.5f : 1f);
        flash = FLASH_TIME;
        if (health == 0) {
            state = State.KNOCKED_OUT;
            move = null;
        } else if (!blocked) {
            state = State.HIT_STUN;
            move = null;
            // The timer counts up from minus the stun; stun ends when it reaches zero
            timer = -hit.hitStun();
        }
        return blocked;
    }

    /** Distance from this fighter's body edge to the far end of its current move. */
    public float reach() {
        return move == null ? 0f : move.reach();
    }

    void apply() {
        body.getTransform().setLocalPosition(x, y + HEIGHT / 2f, 0f);
        bodyMesh.setColor(flash > 0f ? Color.WHITE : state == State.BLOCK ? blockColor : color);
        float armX = ARM_REST_X;
        float armY = state == State.BLOCK ? ARM_GUARD_Y : ARM_REST_Y;
        float legX = LEG_REST_X;
        float legY = LEG_REST_Y;
        if (state == State.ATTACK) {
            float extension = extension();
            if (move == PUNCH) {
                armX = MathUtils.lerp(ARM_REST_X, WIDTH / 2f + move.reach() - LIMB_HALF_LENGTH, extension);
            } else {
                legX = MathUtils.lerp(LEG_REST_X, WIDTH / 2f + move.reach() - LIMB_HALF_LENGTH, extension);
                legY = MathUtils.lerp(LEG_REST_Y, LEG_KICK_Y, extension);
            }
        }
        arm.getTransform().setLocalPosition(armX * facing, armY, 0f);
        leg.getTransform().setLocalPosition(legX * facing, legY, 0f);
    }

    /** 0 to 1: how far the limb is stretched out; full while the move can hit, easing back in recovery. */
    private float extension() {
        if (timer < move.startup()) {
            return timer / move.startup();
        }
        float recoveryStart = move.startup() + move.active();
        if (timer < recoveryStart) {
            return 1f;
        }
        return 1f - MathUtils.clamp((timer - recoveryStart) / move.recovery(), 0f, 1f);
    }
}
