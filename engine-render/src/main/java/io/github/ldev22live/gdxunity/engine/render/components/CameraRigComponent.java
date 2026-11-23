package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;

import java.util.Objects;
import java.util.Optional;

/**
 * Moves and turns the entity it is on, normally one with a {@link CameraComponent}, to follow another entity (TD-050).
 * It only runs while the game plays, in the scene update, after physics, so it sees the pose a character or body just reached.
 * Keep the entity at the scene's root: the rig sets its position as a world position.
 * <ul>
 *   <li>{@link Mode#FOLLOW}: stays at {@link #getFollowOffset()} from the target and looks at the target plus
 *       {@link #getPivotHeight()}; {@link #getFollowRate()} smooths the movement, the same at any frame rate.</li>
 *   <li>{@link Mode#ORBIT}: third person. Sits {@link #getDistance()} from the pivot (target plus pivot height) in the direction
 *       opposite to where {@link #getYaw()} and {@link #getPitch()} look, and looks at the pivot. A script turns it with
 *       {@link #rotate}. With {@link #isOcclusionEnabled()} the camera is pulled in front of walls using the 3D physics.</li>
 *   <li>{@link Mode#FIRST_PERSON}: sits at the target plus {@link #getEyeHeight()}, looks where the target faces turned by
 *       {@link #getYaw()}, tilted by {@link #getPitch()}.</li>
 * </ul>
 * The rig reads no input. The target is found by entity name when play starts; a name that matches nothing is an error naming
 * the rig's entity, an empty name leaves the rig idle. Angles are degrees: yaw 0 looks along -Z, positive yaw turns left; positive
 * pitch looks up. Lengths are scene units.
 */
public class CameraRigComponent extends BaseComponent {

    public enum Mode {
        FOLLOW,
        ORBIT,
        FIRST_PERSON
    }

    /** Behind and above a character, like the chase camera of the character sample. */
    public static final Vector3 DEFAULT_FOLLOW_OFFSET = new Vector3(0f, 420f, 560f);
    /** Per second; at 6 the camera closes about 63% of the gap in a sixth of a second. */
    public static final float DEFAULT_FOLLOW_RATE = 6f;
    /** Height above the target's position that the camera looks at: roughly a character's head. */
    public static final float DEFAULT_PIVOT_HEIGHT = 80f;
    public static final float DEFAULT_EYE_HEIGHT = 70f;
    public static final float DEFAULT_DISTANCE = 400f;
    public static final float DEFAULT_MIN_DISTANCE = 100f;
    public static final float DEFAULT_MAX_DISTANCE = 1200f;
    public static final float DEFAULT_PITCH = -20f;
    public static final float DEFAULT_MIN_PITCH = -80f;
    public static final float DEFAULT_MAX_PITCH = 60f;
    /** How far in front of a wall the occluded camera stops. */
    public static final float DEFAULT_OCCLUSION_MARGIN = 20f;
    /**
     * The occlusion ray starts at the pivot, usually inside the target's own shape, which it then reports at distance 0. It steps
     * forward by {@link #SKIP_STEP} and tries again, at most this often: a target wider than about 2 x 16 x 10 units across the
     * ray cannot be left, and then the camera is not pulled in.
     */
    private static final int MAX_TARGET_SKIPS = 16;
    /** How far the occlusion ray moves on after hitting the target itself. */
    private static final float SKIP_STEP = 10f;

    private Mode mode = Mode.FOLLOW;
    private String targetName = "";
    private final Vector3 followOffset = new Vector3(DEFAULT_FOLLOW_OFFSET);
    private float followRate = DEFAULT_FOLLOW_RATE;
    private float pivotHeight = DEFAULT_PIVOT_HEIGHT;
    private float eyeHeight = DEFAULT_EYE_HEIGHT;
    private float yaw;
    private float pitch = DEFAULT_PITCH;
    private float minPitch = DEFAULT_MIN_PITCH;
    private float maxPitch = DEFAULT_MAX_PITCH;
    private float distance = DEFAULT_DISTANCE;
    private float minDistance = DEFAULT_MIN_DISTANCE;
    private float maxDistance = DEFAULT_MAX_DISTANCE;
    private boolean occlusionEnabled;
    private float occlusionMargin = DEFAULT_OCCLUSION_MARGIN;
    private int occlusionMask = PhysicsLayers3D.ALL_LAYERS;

    private Entity target;
    private boolean placed;
    private final Vector3 tmpPivot = new Vector3();
    private final Vector3 tmpWanted = new Vector3();
    private final Vector3 tmpDirection = new Vector3();
    private final Vector3 tmpCurrent = new Vector3();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode cannot be null");
    }

    /** Name of the entity to follow; empty for none. */
    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName == null ? "" : targetName.trim();
        this.target = null;
        this.placed = false;
    }

    /** Offset from the target in {@link Mode#FOLLOW}, world space. */
    public Vector3 getFollowOffset() {
        return new Vector3(followOffset);
    }

    public void setFollowOffset(Vector3 offset) {
        Objects.requireNonNull(offset, "followOffset cannot be null");
        requireFinite(offset.x, "followOffset.x");
        requireFinite(offset.y, "followOffset.y");
        requireFinite(offset.z, "followOffset.z");
        this.followOffset.set(offset);
    }

    /** Smoothing of {@link Mode#FOLLOW} per second; 0 follows rigidly. */
    public float getFollowRate() {
        return followRate;
    }

    public void setFollowRate(float followRate) {
        requireFinite(followRate, "followRate");
        if (followRate < 0f) {
            throw new IllegalArgumentException("followRate cannot be negative: " + followRate);
        }
        this.followRate = followRate;
    }

    public float getPivotHeight() {
        return pivotHeight;
    }

    public void setPivotHeight(float pivotHeight) {
        requireFinite(pivotHeight, "pivotHeight");
        this.pivotHeight = pivotHeight;
    }

    public float getEyeHeight() {
        return eyeHeight;
    }

    public void setEyeHeight(float eyeHeight) {
        requireFinite(eyeHeight, "eyeHeight");
        this.eyeHeight = eyeHeight;
    }

    public float getYaw() {
        return yaw;
    }

    /** Degrees; wrapped to -180..180. */
    public void setYaw(float yaw) {
        requireFinite(yaw, "yaw");
        this.yaw = wrapDegrees(yaw);
    }

    public float getPitch() {
        return pitch;
    }

    /** Degrees; held between {@link #getMinPitch()} and {@link #getMaxPitch()}. */
    public void setPitch(float pitch) {
        requireFinite(pitch, "pitch");
        this.pitch = MathUtils.clamp(pitch, minPitch, maxPitch);
    }

    /** Turns by the given amounts; the way a script applies mouse or stick movement. */
    public void rotate(float yawDegrees, float pitchDegrees) {
        setYaw(yaw + yawDegrees);
        setPitch(pitch + pitchDegrees);
    }

    public float getMinPitch() {
        return minPitch;
    }

    public float getMaxPitch() {
        return maxPitch;
    }

    /** Both between -89 and 89, minimum not above maximum; the current pitch is brought inside. */
    public void setPitchLimits(float minPitch, float maxPitch) {
        requireFinite(minPitch, "minPitch");
        requireFinite(maxPitch, "maxPitch");
        if (minPitch < -89f || maxPitch > 89f || minPitch > maxPitch) {
            throw new IllegalArgumentException("Pitch limits must satisfy -89 <= min <= max <= 89: " + minPitch + ", " + maxPitch);
        }
        this.minPitch = minPitch;
        this.maxPitch = maxPitch;
        this.pitch = MathUtils.clamp(pitch, minPitch, maxPitch);
    }

    public float getDistance() {
        return distance;
    }

    /** Held between {@link #getMinDistance()} and {@link #getMaxDistance()}. */
    public void setDistance(float distance) {
        requireFinite(distance, "distance");
        this.distance = MathUtils.clamp(distance, minDistance, maxDistance);
    }

    public float getMinDistance() {
        return minDistance;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public void setDistanceLimits(float minDistance, float maxDistance) {
        requireFinite(minDistance, "minDistance");
        requireFinite(maxDistance, "maxDistance");
        if (minDistance <= 0f || minDistance > maxDistance) {
            throw new IllegalArgumentException("Distance limits must satisfy 0 < min <= max: " + minDistance + ", " + maxDistance);
        }
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.distance = MathUtils.clamp(distance, minDistance, maxDistance);
    }

    public boolean isOcclusionEnabled() {
        return occlusionEnabled;
    }

    public void setOcclusionEnabled(boolean occlusionEnabled) {
        this.occlusionEnabled = occlusionEnabled;
    }

    public float getOcclusionMargin() {
        return occlusionMargin;
    }

    public void setOcclusionMargin(float occlusionMargin) {
        requireFinite(occlusionMargin, "occlusionMargin");
        if (occlusionMargin < 0f) {
            throw new IllegalArgumentException("occlusionMargin cannot be negative: " + occlusionMargin);
        }
        this.occlusionMargin = occlusionMargin;
    }

    /** Layers that pull the camera in, see {@link PhysicsLayers3D}. */
    public int getOcclusionMask() {
        return occlusionMask;
    }

    public void setOcclusionMask(int occlusionMask) {
        this.occlusionMask = occlusionMask & PhysicsLayers3D.ALL_LAYERS;
    }

    /** The entity being followed, once play has started and a target name is set; otherwise null. */
    public Entity getTarget() {
        return target;
    }

    @Override
    public void onStart() {
        Entity owner = getEntity();
        target = null;
        placed = false;
        if (!targetName.isEmpty()) {
            Scene scene = owner.getScene();
            target = scene == null ? null : scene.findEntityByName(targetName);
            if (target == null) {
                throw new IllegalStateException("Camera Rig on " + owner.getName() + " follows '" + targetName
                    + "' but no entity has that name. Fix the target name in the Details panel.");
            }
        }
        super.onStart();
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (target == null) {
            return;
        }
        Entity owner = getEntity();
        Vector3 targetPosition = target.getTransform().getWorldPosition();
        switch (mode) {
            case FOLLOW -> updateFollow(owner, targetPosition, deltaTime);
            case ORBIT -> updateOrbit(owner, targetPosition);
            case FIRST_PERSON -> updateFirstPerson(owner, targetPosition);
        }
        placed = true;
    }

    private void updateFollow(Entity owner, Vector3 targetPosition, float deltaTime) {
        tmpWanted.set(targetPosition).add(followOffset);
        tmpCurrent.set(owner.getTransform().getWorldPosition());
        if (placed && followRate > 0f) {
            tmpCurrent.lerp(tmpWanted, 1f - (float) Math.exp(-followRate * deltaTime));
        } else {
            tmpCurrent.set(tmpWanted);
        }
        owner.getTransform().setLocalPosition(tmpCurrent);
        tmpPivot.set(targetPosition.x, targetPosition.y + pivotHeight, targetPosition.z);
        tmpDirection.set(tmpPivot).sub(tmpCurrent);
        if (tmpDirection.len2() > 1e-6f) {
            float yawDegrees = yawOf(tmpDirection);
            float pitchDegrees = pitchOf(tmpDirection);
            owner.getTransform().setLocalEulerAngles(yawDegrees, pitchDegrees, 0f);
        }
    }

    private void updateOrbit(Entity owner, Vector3 targetPosition) {
        tmpPivot.set(targetPosition.x, targetPosition.y + pivotHeight, targetPosition.z);
        forward(yaw, pitch, tmpDirection);
        float length = distance;
        if (occlusionEnabled) {
            length = occludedDistance(owner, tmpPivot, tmpDirection, distance);
        }
        tmpWanted.set(tmpPivot).mulAdd(tmpDirection, -length);
        owner.getTransform().setLocalPosition(tmpWanted);
        owner.getTransform().setLocalEulerAngles(yaw, pitch, 0f);
    }

    private void updateFirstPerson(Entity owner, Vector3 targetPosition) {
        tmpWanted.set(targetPosition.x, targetPosition.y + eyeHeight, targetPosition.z);
        owner.getTransform().setLocalPosition(tmpWanted);
        forward(0f, 0f, tmpDirection);
        target.getTransform().getWorldRotation().transform(tmpDirection);
        float facing = yawOf(tmpDirection);
        owner.getTransform().setLocalEulerAngles(wrapDegrees(facing + yaw), pitch, 0f);
    }

    /**
     * How far back from the pivot the camera may sit: the wanted distance, or the distance to the first thing in the way (that
     * is not the target) minus the margin, never below 0.
     */
    private float occludedDistance(Entity owner, Vector3 pivot, Vector3 forward, float wanted) {
        PhysicsSystem3D physics = owner.getScene() == null ? null : owner.getScene().getPhysics3D();
        if (physics == null) {
            return wanted;
        }
        Vector3 back = new Vector3(forward).scl(-1f);
        Vector3 origin = new Vector3(pivot);
        float travelled = 0f;
        for (int skips = 0; skips <= MAX_TARGET_SKIPS; skips++) {
            Optional<PhysicsSystem3D.RaycastHit> hit = physics.raycast(origin, back, wanted - travelled + occlusionMargin, occlusionMask);
            if (hit.isEmpty()) {
                return wanted;
            }
            PhysicsSystem3D.RaycastHit found = hit.get();
            if (found.entity() == target || found.entity() == owner) {
                // The ray started inside the target (or hit it on the way out): carry on from just beyond that point
                travelled += found.distance() + SKIP_STEP;
                origin.set(found.point()).mulAdd(back, SKIP_STEP);
                if (travelled >= wanted) {
                    return wanted;
                }
                continue;
            }
            return Math.max(0f, Math.min(wanted, travelled + found.distance() - occlusionMargin));
        }
        return wanted;
    }

    /** Unit vector a camera with this yaw and pitch looks along. */
    public static Vector3 forward(float yawDegrees, float pitchDegrees, Vector3 out) {
        float yawRadians = yawDegrees * MathUtils.degreesToRadians;
        float pitchRadians = pitchDegrees * MathUtils.degreesToRadians;
        float horizontal = MathUtils.cos(pitchRadians);
        return out.set(-MathUtils.sin(yawRadians) * horizontal, MathUtils.sin(pitchRadians), -MathUtils.cos(yawRadians) * horizontal);
    }

    /** Yaw in degrees of a direction, the inverse of {@link #forward} for the horizontal part. */
    public static float yawOf(Vector3 direction) {
        return MathUtils.atan2(-direction.x, -direction.z) * MathUtils.radiansToDegrees;
    }

    /** Pitch in degrees of a direction; positive looks up. */
    public static float pitchOf(Vector3 direction) {
        float horizontal = (float) Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return MathUtils.atan2(direction.y, horizontal) * MathUtils.radiansToDegrees;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped > 180f) {
            wrapped -= 360f;
        } else if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number: " + value);
        }
    }
}
