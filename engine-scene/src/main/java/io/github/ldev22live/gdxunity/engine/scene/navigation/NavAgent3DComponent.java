package io.github.ldev22live.gdxunity.engine.scene.navigation;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Walks its entity along a path found on the scene's navigation (roadmap M6.4, TD-092), shown in the editor as <b>Nav Agent</b>.
 * {@link #moveTo} finds a path around the obstacles inside a {@link NavMeshBoundsComponent}; while playing, the agent then follows
 * it at {@link #getSpeed()} and stops within {@link #getStoppingDistance()} of the destination.
 *
 * <ul>
 *   <li>With a <b>Character Controller 3D</b> and running 3D physics, the agent walks through physics
 *       ({@link PhysicsSystem3D#moveCharacter}): gravity, slopes and collisions with moving bodies still apply.</li>
 *   <li>Without one, it moves its transform directly and stands on the baked floor; the entity's origin is its feet.</li>
 * </ul>
 * Facing: with {@link #isFaceMovement()} the entity turns (yaw only) so its +Z axis points where it walks. Behaviour graphs drive it
 * with the Navigation nodes (Move To, Stop Moving, Has Arrived); scripts call the same methods. Nothing about a walk is saved: the
 * scene file holds only the settings.
 */
public class NavAgent3DComponent extends BaseComponent {
    /** What the agent is doing. */
    public enum Status {
        /** Not asked to go anywhere, or stopped. */
        IDLE,
        /** Following a path. */
        MOVING,
        /** Reached its destination. */
        ARRIVED,
        /** Asked to go somewhere it has no way to. */
        UNREACHABLE
    }

    /** Default walking speed: 3 m/s. */
    public static final float DEFAULT_SPEED = 300f;
    /** Default distance from the destination that counts as there. */
    public static final float DEFAULT_STOPPING_DISTANCE = 25f;
    /** Fastest an agent may walk, in world units per second (100 m/s). */
    public static final float MAX_SPEED = 10000f;
    /** Distance, in world units, at which a waypoint before the last counts as passed. */
    static final float WAYPOINT_REACHED = 5f;

    private float speed = DEFAULT_SPEED;
    private float stoppingDistance = DEFAULT_STOPPING_DISTANCE;
    private boolean faceMovement = true;

    private Status status = Status.IDLE;
    private final List<Vector3> path = new ArrayList<>();
    private int next;
    private final Vector3 destination = new Vector3();
    private final Vector3 position = new Vector3();
    private final Vector3 velocity = new Vector3();

    /** Walking speed in world units per second. */
    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        if (!(speed > 0f && speed <= MAX_SPEED)) {
            throw new IllegalArgumentException("Speed must be above 0 and at most " + MAX_SPEED + ": " + speed);
        }
        this.speed = speed;
    }

    /** Distance from the destination, in world units, at which the agent stops. */
    public float getStoppingDistance() {
        return stoppingDistance;
    }

    public void setStoppingDistance(float distance) {
        if (!(distance >= 0f) || !Float.isFinite(distance)) {
            throw new IllegalArgumentException("Stopping distance must be 0 or more: " + distance);
        }
        this.stoppingDistance = distance;
    }

    /** Whether the entity turns to face where it walks. */
    public boolean isFaceMovement() {
        return faceMovement;
    }

    public void setFaceMovement(boolean faceMovement) {
        this.faceMovement = faceMovement;
    }

    /** A new component with the same settings, enabled like this one; the walk in progress is not copied. */
    public NavAgent3DComponent copy() {
        NavAgent3DComponent copy = new NavAgent3DComponent();
        copy.set(this);
        return copy;
    }

    /** Takes every setting of {@code other}; enabled state included. */
    public void set(NavAgent3DComponent other) {
        Objects.requireNonNull(other, "other cannot be null");
        speed = other.speed;
        stoppingDistance = other.stoppingDistance;
        faceMovement = other.faceMovement;
        setEnabled(other.isEnabled());
    }

    /**
     * Finds a path from where the entity stands to {@code target} and starts walking it.
     *
     * @return true when the agent walks (or is already there); false when there is no way, and the status is then
     *         {@link Status#UNREACHABLE}
     * @throws IllegalStateException when the component is not on an entity in a scene
     */
    public boolean moveTo(Vector3 target) {
        Objects.requireNonNull(target, "target cannot be null");
        Entity entity = requireEntity();
        destination.set(target);
        path.clear();
        next = 0;
        entity.getTransform().getWorldPosition(position);
        if (horizontalDistance(position, target) <= stoppingDistance) {
            arrive(entity);
            return true;
        }
        List<Vector3> found = NavigationSystem3D.of(entity.getScene()).findPath(position, target);
        if (found.isEmpty()) {
            halt(entity);
            status = Status.UNREACHABLE;
            return false;
        }
        for (Vector3 waypoint : found) {
            path.add(new Vector3(waypoint));
        }
        status = Status.MOVING;
        return true;
    }

    /** Stops where it is; the status becomes {@link Status#IDLE}. */
    public void stop() {
        path.clear();
        next = 0;
        if (getEntity() != null) {
            halt(getEntity());
        }
        status = Status.IDLE;
    }

    public Status getStatus() {
        return status;
    }

    /** Whether the agent reached the destination of its last {@link #moveTo}. */
    public boolean hasArrived() {
        return status == Status.ARRIVED;
    }

    /** Whether the agent is following a path. */
    public boolean isMoving() {
        return status == Status.MOVING;
    }

    /** The waypoints of the current walk, the floor points it turns at (copies); empty when not walking. */
    public List<Vector3> getPath() {
        List<Vector3> copy = new ArrayList<>(path.size());
        for (Vector3 waypoint : path) {
            copy.add(new Vector3(waypoint));
        }
        return copy;
    }

    /** Where the last {@link #moveTo} asked to go (a copy). */
    public Vector3 getDestination() {
        return new Vector3(destination);
    }

    @Override
    public void onUpdate(float deltaTime) {
        Entity entity = getEntity();
        if (status != Status.MOVING || entity == null || !(deltaTime > 0f)) {
            return;
        }
        Transform transform = entity.getTransform();
        transform.getWorldPosition(position);
        boolean last = next == path.size() - 1;
        Vector3 waypoint = path.get(next);
        float distance = horizontalDistance(position, waypoint);
        if (distance <= (last ? stoppingDistance : WAYPOINT_REACHED)) {
            if (last) {
                arrive(entity);
                return;
            }
            next++;
            waypoint = path.get(next);
            distance = horizontalDistance(position, waypoint);
        }
        if (distance <= 0f) {
            return;
        }
        float dx = (waypoint.x - position.x) / distance;
        float dz = (waypoint.z - position.z) / distance;
        if (faceMovement) {
            transform.setLocalEulerAngles((float) Math.toDegrees(Math.atan2(dx, dz)), 0f, 0f);
        }
        PhysicsSystem3D physics = character(entity);
        if (physics != null) {
            physics.moveCharacter(entity, velocity.set(dx * speed, 0f, dz * speed));
            return;
        }
        float step = Math.min(speed * deltaTime, distance);
        float t = step / distance;
        position.set(position.x + dx * step, position.y + (waypoint.y - position.y) * t, position.z + dz * step);
        setWorldPosition(transform, position);
    }

    @Override
    public void onDisable() {
        stop();
    }

    // --- For behaviour graphs and generated scripts ---

    /** {@link #moveTo} on {@code entity}'s agent. @throws IllegalStateException when it has none */
    public static boolean moveTo(Entity entity, Vector3 target) {
        return of(entity).moveTo(target);
    }

    /** {@link #stop} on {@code entity}'s agent. @throws IllegalStateException when it has none */
    public static void stop(Entity entity) {
        of(entity).stop();
    }

    /** {@link #hasArrived} of {@code entity}'s agent. @throws IllegalStateException when it has none */
    public static boolean hasArrived(Entity entity) {
        return of(entity).hasArrived();
    }

    /** The agent on {@code entity}. @throws IllegalStateException when it has none */
    public static NavAgent3DComponent of(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        NavAgent3DComponent agent = entity.getComponent(NavAgent3DComponent.class);
        if (agent == null) {
            throw new IllegalStateException(entity.getName() + " has no Nav Agent. Add one (Add Component > Navigation > Nav Agent) to "
                + "let it find its way.");
        }
        return agent;
    }

    private Entity requireEntity() {
        Entity entity = getEntity();
        if (entity == null || entity.getScene() == null) {
            throw new IllegalStateException("A Nav Agent must be on an entity in a scene to walk");
        }
        return entity;
    }

    private void arrive(Entity entity) {
        path.clear();
        next = 0;
        halt(entity);
        status = Status.ARRIVED;
    }

    /** Ends a physics walk: a character keeps the last speed it was given until told otherwise. */
    private void halt(Entity entity) {
        PhysicsSystem3D physics = character(entity);
        if (physics != null) {
            physics.moveCharacter(entity, velocity.setZero());
        }
    }

    /** The scene's physics when {@code entity} walks through it as a character, else null. */
    private static PhysicsSystem3D character(Entity entity) {
        Scene scene = entity.getScene();
        PhysicsSystem3D physics = scene == null ? null : scene.getPhysics3D();
        CharacterController3DComponent controller = entity.getComponent(CharacterController3DComponent.class);
        return physics != null && controller != null && controller.isEnabled() && physics.hasBody(entity) ? physics : null;
    }

    private static float horizontalDistance(Vector3 a, Vector3 b) {
        float dx = b.x - a.x;
        float dz = b.z - a.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** Moves {@code transform} so its world position becomes {@code world}, whatever its parent is. */
    private static void setWorldPosition(Transform transform, Vector3 world) {
        Transform parent = transform.getParent();
        if (parent == null) {
            transform.setLocalPosition(world);
            return;
        }
        Matrix4 inverse = parent.getWorldMatrix();
        if (Math.abs(inverse.det()) < 1e-12f) {
            return;
        }
        transform.setLocalPosition(new Vector3(world).mul(inverse.inv()));
    }
}
