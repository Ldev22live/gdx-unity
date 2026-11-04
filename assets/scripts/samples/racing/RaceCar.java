package samples.racing;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * One car in the race: where it is, which way it points, how fast it goes and how far round the circuit it has got.
 * The player's car is driven by the keys ({@link #drive}); rivals steer towards a point ahead of them on the
 * track ({@link #steerTowards}). Handling is arcade style: the car always goes where it points, with no sliding.
 *
 * <p>Not a script by itself: {@link RaceGame} creates one per car entity and moves the entity every frame.
 */
public final class RaceCar {
    /** Height of the car entity's centre above the ground: half the body height plus the road surface. */
    public static final float RIDE_HEIGHT = 16f;
    /** Player acceleration and braking, in units per second squared. */
    static final float ACCELERATION = 520f;
    static final float BRAKING = 1300f;
    /** Speed lost per second with neither key held. */
    static final float COAST_DRAG = 260f;
    /** Top speed on the road, top speed on grass and top speed backwards, in units per second. */
    static final float MAX_SPEED = 820f;
    static final float GRASS_MAX_SPEED = 300f;
    static final float MAX_REVERSE_SPEED = 220f;
    /** How quickly grass slows a car that arrives faster than {@link #GRASS_MAX_SPEED}. */
    static final float GRASS_DRAG = 1100f;
    /** Steering rate at or above {@link #FULL_STEER_SPEED}, in degrees per second; slower cars turn less. */
    static final float TURN_RATE = 115f;
    static final float FULL_STEER_SPEED = 250f;
    /** Rival steering rate and acceleration. */
    static final float RIVAL_TURN_RATE = 150f;
    static final float RIVAL_ACCELERATION = 380f;
    /** How far past a piece's edge a car may be and still count as driving along it (cutting a corner a little). */
    static final float PROGRESS_MARGIN = 1.6f;

    public final Entity entity;
    private final float startX;
    private final float startZ;
    private final float startHeading;

    public float x;
    public float z;
    /** Degrees around Y; 0 points along -Z. */
    public float heading;
    /** Units per second; negative while reversing. */
    public float speed;
    /** Completed laps, the road piece the car is on and how far along it (0 to 1). */
    public int lap;
    public int segment;
    public float along;
    /** Order of finishing (1 for the winner), or 0 while still racing. */
    public int finishPlace;

    public RaceCar(Entity entity) {
        this.entity = entity;
        startX = entity.getTransform().getWorldPosition().x;
        startZ = entity.getTransform().getWorldPosition().z;
        startHeading = entity.getTransform().getWorldRotation().getYaw();
    }

    /** Back on the grid, standing still, no laps done. */
    public void reset(RaceTrack track) {
        x = startX;
        z = startZ;
        heading = startHeading;
        speed = 0f;
        lap = 0;
        segment = 0;
        along = track.segment(0).along(x, z);
        finishPlace = 0;
        apply();
    }

    /**
     * Player driving.
     *
     * @param throttle 1 to accelerate, -1 to brake and then reverse, 0 to coast
     * @param steer    1 to turn left, -1 to turn right
     */
    public void drive(float throttle, float steer, boolean onRoad, float deltaTime) {
        if (throttle > 0f) {
            speed += (speed < 0f ? BRAKING : ACCELERATION) * deltaTime;
        } else if (throttle < 0f) {
            speed -= (speed > 0f ? BRAKING : ACCELERATION) * deltaTime;
        } else {
            speed = approach(speed, 0f, COAST_DRAG * deltaTime);
        }
        float limit = onRoad ? MAX_SPEED : GRASS_MAX_SPEED;
        if (speed > limit) {
            speed = Math.max(limit, speed - GRASS_DRAG * deltaTime);
        }
        speed = Math.max(speed, -MAX_REVERSE_SPEED);
        // Reversing turns the other way, like a real car
        heading += steer * TURN_RATE * deltaTime * MathUtils.clamp(speed / FULL_STEER_SPEED, -1f, 1f);
        move(deltaTime);
    }

    /** Rival driving: turn towards the target point and speed up or slow down towards {@code targetSpeed}. */
    public void steerTowards(Vector2 target, float targetSpeed, float deltaTime) {
        float desired = MathUtils.atan2(-(target.x - x), -(target.y - z)) * MathUtils.radiansToDegrees;
        float turn = wrapDegrees(desired - heading);
        heading += MathUtils.clamp(turn, -RIVAL_TURN_RATE * deltaTime, RIVAL_TURN_RATE * deltaTime);
        speed = approach(speed, targetSpeed, RIVAL_ACCELERATION * deltaTime);
        move(deltaTime);
    }

    /**
     * Moves the car on to the next road piece once it is driving along it, and counts a lap when it reaches the first
     * piece again. Pieces must be driven in order, so skipping part of the circuit does not count.
     *
     * @return true when this call completed a lap
     */
    public boolean updateProgress(RaceTrack track) {
        int next = (segment + 1) % track.size();
        RaceTrack.Segment nextSegment = track.segment(next);
        float nextAlong = nextSegment.along(x, z);
        boolean lapDone = false;
        if (nextAlong >= 0f && nextAlong <= 1f
            && Math.abs(nextSegment.across(x, z)) <= nextSegment.halfWidth * PROGRESS_MARGIN) {
            segment = next;
            if (next == 0) {
                lap++;
                lapDone = true;
            }
        }
        along = track.segment(segment).along(x, z);
        return lapDone;
    }

    /** Distance round the race in road pieces: laps times pieces, plus pieces done this lap. Higher is further ahead. */
    public float progress(RaceTrack track) {
        return lap * track.size() + segment + MathUtils.clamp(along, 0f, 1f);
    }

    /** Copies position and heading to the car entity. */
    public void apply() {
        entity.getTransform().setLocalPosition(x, RIDE_HEIGHT, z);
        entity.getTransform().setLocalEulerAngles(heading, 0f, 0f);
    }

    private void move(float deltaTime) {
        float radians = heading * MathUtils.degreesToRadians;
        x -= MathUtils.sin(radians) * speed * deltaTime;
        z -= MathUtils.cos(radians) * speed * deltaTime;
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    /** The same angle in the range -180 to 180 degrees. */
    static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped > 180f) {
            wrapped -= 360f;
        } else if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }
}
