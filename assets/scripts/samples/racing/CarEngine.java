package samples.racing;

import com.badlogic.gdx.math.MathUtils;

/**
 * The engine and gearbox of one car, reduced to what the ear needs: how fast the engine is turning ({@link #getRevs}) and how
 * hard the driver is pressing the pedal ({@link #getThrottle}). Revs follow the car's speed through the current gear;
 * the gearbox changes up near the redline (the throttle is cut for a moment, so the revs fall and the engine pops), changes down
 * as the car slows, and while the car stands still with the pedal down the clutch slips and the engine revs up on its own, as it
 * does on a starting grid.
 *
 * <p>{@link RaceGame} passes both numbers to the car's Engine Sound component every frame. Not a script by itself.
 */
public final class CarEngine {
    /** The speed (units per second) at which each gear reaches the redline. */
    static final float[] GEAR_TOP_SPEEDS = {240f, 420f, 620f, 835f};
    /** Share of the redline the engine idles at; revs 0 to 1 span idle to redline. */
    static final float IDLE_SHARE = 0.14f;
    /** Change up above this share of the rev range (with the pedal down), change down below {@link #DOWN_SHIFT_REVS}. */
    static final float UP_SHIFT_REVS = 0.9f;
    static final float DOWN_SHIFT_REVS = 0.3f;
    /** A down-shift is only made if the engine would then turn slower than this, so the gearbox does not hunt. */
    static final float DOWN_SHIFT_CEILING = 0.8f;
    /** How long the throttle stays cut while changing up, in seconds. */
    static final float SHIFT_TIME = 0.14f;
    /** Revs of a slipping clutch with the pedal fully down in first gear at a standstill. */
    static final float LAUNCH_REVS = 0.55f;
    /** How quickly the revs rise and fall towards where the wheels want them, per second. */
    static final float REV_RISE_RATE = 16f;
    static final float REV_FALL_RATE = 6f;
    /** Pedal position when a car cruises (rivals holding their speed). */
    static final float CRUISE_THROTTLE = 0.3f;

    private int gear;
    private int pendingGear;
    private float shiftLeft;
    private float revs;
    private float throttle;

    /** Back in first gear, idling. */
    public void reset() {
        gear = 0;
        pendingGear = 0;
        shiftLeft = 0f;
        revs = 0f;
        throttle = 0f;
    }

    /** How fast the engine turns now: 0 idling, 1 at the redline. */
    public float getRevs() {
        return revs;
    }

    /** How hard the pedal is pressed as the engine feels it: 0 while changing gear or coasting, up to 1. */
    public float getThrottle() {
        return throttle;
    }

    /** The gear in use, counting from 0 for first. */
    public int getGear() {
        return gear;
    }

    /** Whether a gear change is happening (the throttle is cut). */
    public boolean isShifting() {
        return shiftLeft > 0f;
    }

    /**
     * @param speed    the car's speed in units per second; the sign is ignored
     * @param pedal    how hard the driver presses the throttle, 0 to 1
     * @param deltaTime seconds since the last call
     */
    public void update(float speed, float pedal, float deltaTime) {
        float v = Math.abs(speed);
        throttle = pedal;
        if (shiftLeft > 0f) {
            shiftLeft -= deltaTime;
            throttle = 0f;
            if (shiftLeft <= 0f) {
                gear = pendingGear;
            }
        } else {
            float wheelRevs = revsAt(v, gear);
            if (gear < GEAR_TOP_SPEEDS.length - 1 && wheelRevs >= UP_SHIFT_REVS && pedal > 0f) {
                pendingGear = gear + 1;
                shiftLeft = SHIFT_TIME;
                throttle = 0f;
            } else if (gear > 0 && wheelRevs < DOWN_SHIFT_REVS && revsAt(v, gear - 1) < DOWN_SHIFT_CEILING) {
                gear--;
            }
        }
        int gearForRevs = shiftLeft > 0f ? pendingGear : gear;
        float target = revsAt(v, gearForRevs);
        if (gearForRevs == 0 && shiftLeft <= 0f) {
            target = Math.max(target, LAUNCH_REVS * pedal);
        }
        float rate = target > revs ? REV_RISE_RATE : REV_FALL_RATE;
        revs += (target - revs) * Math.min(1f, rate * deltaTime);
        revs = MathUtils.clamp(revs, 0f, 1f);
    }

    /** Revs the wheels force on the engine at {@code speed} in {@code gear}: idle at a standstill, 1 at the gear's top speed. */
    static float revsAt(float speed, int gear) {
        float share = speed / GEAR_TOP_SPEEDS[gear];
        return MathUtils.clamp((share - IDLE_SHARE) / (1f - IDLE_SHARE), 0f, 1f);
    }
}
