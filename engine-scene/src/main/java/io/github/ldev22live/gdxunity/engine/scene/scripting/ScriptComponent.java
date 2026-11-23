package io.github.ldev22live.gdxunity.engine.scene.scripting;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;

/**
 * Shared base class for user scripts authored in Java or Kotlin.
 *
 * Provides lifecycle callbacks:
 * - {@link #onCreate()}: Invoked once upon instantiation/attachment.
 * - {@link #onStart()}: Invoked once when simulation starts or entity becomes active.
 * - {@link #onUpdate(float)}: Invoked each frame during active Play Mode.
 * - {@link #onFixedUpdate(float)}: Invoked for fixed-step physics/simulation loops if supported.
 * - {@link #onEnable()}: Invoked whenever script component or entity is enabled.
 * - {@link #onDisable()}: Invoked whenever script component or entity is disabled.
 * - {@link #onDestroy()}: Invoked when component is removed or scene/entity is destroyed.
 * - {@link #onCollisionEnter2D(Entity)}, {@link #onCollisionExit2D(Entity)}, {@link #onTriggerEnter2D(Entity)},
 *   {@link #onTriggerExit2D(Entity)}: 2D physics contacts of this entity.
 * - {@link #onCollisionEnter3D(Entity)}, {@link #onCollisionExit3D(Entity)}, {@link #onTriggerEnter3D(Entity)},
 *   {@link #onTriggerExit3D(Entity)}: 3D physics contacts of this entity (TD-048).
 * - {@link #setJointMotor3D}, {@link #getJointPosition3D}: the hinge or slider joint of this entity (TD-051).
 */
public abstract class ScriptComponent extends BaseComponent {

    /**
     * Called once when the script is attached and initialized.
     */
    public void onCreate() {
        // Optional override
    }

    /**
     * Called once before the script's first update frame during Play Mode.
     */
    @Override
    public void onStart() {
        super.onStart();
    }

    /**
     * Called each frame during Play Mode if both entity and component are active and enabled.
     *
     * @param deltaTime frame delta time in seconds
     */
    @Override
    public void onUpdate(float deltaTime) {
        // Optional override
    }

    /**
     * Optional fixed update hook for physics and deterministic simulation steps.
     *
     * @param fixedDeltaTime fixed timestep in seconds
     */
    public void onFixedUpdate(float fixedDeltaTime) {
        // Optional override
    }

    /**
     * Called when the script or entity becomes enabled.
     */
    @Override
    public void onEnable() {
        super.onEnable();
    }

    /**
     * Called when the script or entity becomes disabled.
     */
    @Override
    public void onDisable() {
        super.onDisable();
    }

    /**
     * Called when the script instance is being removed or destroyed.
     */
    public void onDestroy() {
        // Optional override
    }

    @Override
    public void onDetach() {
        try {
            onDestroy();
        } finally {
            super.onDetach();
        }
    }

    // --- 2D physics ---

    /**
     * Called after a 2D physics step in which this entity's solid collider started touching {@code other}'s. Both entities are alive;
     * destroying either is safe. Scripts of an entity whose script component is disabled are not called.
     */
    public void onCollisionEnter2D(Entity other) {
        // Optional override
    }

    /** Called when the two solid 2D colliders stopped touching, or one of the entities was deactivated or removed. */
    public void onCollisionExit2D(Entity other) {
        // Optional override
    }

    /** Called when {@code other} entered this entity's sensor collider (a 2D collider with Sensor on), or this entity entered {@code other}'s. */
    public void onTriggerEnter2D(Entity other) {
        // Optional override
    }

    /** Called when {@code other} left the sensor overlap. */
    public void onTriggerExit2D(Entity other) {
        // Optional override
    }

    // --- 3D physics (TD-048) ---

    /**
     * Called after a physics step in which this entity's solid collider started touching {@code other}'s. The entity
     * and {@code other} are both alive; destroying either is safe (their bodies are removed before the next step).
     * Scripts of an entity whose script component is disabled are not called.
     */
    public void onCollisionEnter3D(Entity other) {
        // Optional override
    }

    /** Called when the two solid colliders stopped touching, or one of the entities was deactivated or removed. */
    public void onCollisionExit3D(Entity other) {
        // Optional override
    }

    /** Called when {@code other} entered this entity's trigger collider, or this entity entered {@code other}'s. */
    public void onTriggerEnter3D(Entity other) {
        // Optional override
    }

    /** Called when {@code other} left the trigger overlap. */
    public void onTriggerExit3D(Entity other) {
        // Optional override
    }

    /**
     * The scene's 3D physics, or null when the scene has none running (no enabled 3D collider on an active entity at the
     * start of play). Raycasts, forces, impulses and velocities are on {@link PhysicsSystem3D}; positions and forces are in
     * scene units (100 per meter).
     */
    public PhysicsSystem3D getPhysics3D() {
        Scene scene = getScene();
        return scene != null ? scene.getPhysics3D() : null;
    }

    /**
     * Like {@link #getPhysics3D()}, but fails with a message that says what to add when the scene has no 3D physics.
     *
     * @throws IllegalStateException when the scene runs no 3D physics
     */
    public PhysicsSystem3D requirePhysics3D() {
        PhysicsSystem3D physics = getPhysics3D();
        if (physics == null) {
            throw new IllegalStateException("This scene runs no 3D physics. Add a 3D collider (Add Component > Physics 3D) to an "
                + "entity that is active when play starts.");
        }
        return physics;
    }

    /**
     * Casts a ray against every layer and returns the closest hit, or null. Convenience for generated graph scripts.
     *
     * @param direction need not be normalized
     * @throws IllegalStateException    when the scene runs no 3D physics
     * @throws IllegalArgumentException for a zero direction or a distance that is not positive
     */
    public PhysicsSystem3D.RaycastHit raycast3D(Vector3 origin, Vector3 direction, float maxDistance) {
        return requirePhysics3D().raycast(origin, direction, maxDistance, PhysicsLayers3D.ALL_LAYERS).orElse(null);
    }

    /**
     * Sets the speed this entity's character walks at, in scene units per second along world X and Z, until it is called
     * again (TD-049). The Y component is ignored.
     *
     * @throws IllegalStateException when the scene runs no 3D physics or this entity has no Character Controller 3D yet
     */
    public void moveCharacter3D(Vector3 velocity) {
        requirePhysics3D().moveCharacter(requireEntity("moveCharacter3D"), velocity);
    }

    /**
     * Makes this entity's character jump at {@code speed} scene units per second, if it stands on ground.
     *
     * @return whether it jumped
     */
    public boolean jumpCharacter3D(float speed) {
        return requirePhysics3D().jumpCharacter(requireEntity("jumpCharacter3D"), speed);
    }

    /**
     * Puts this entity's character at {@code position} (scene units, the capsule's center) at once, for a respawn. Its speed is
     * cleared. This is the only way to move a character other than walking it.
     */
    public void teleportCharacter3D(Vector3 position) {
        requirePhysics3D().teleportCharacter(requireEntity("teleportCharacter3D"), position);
    }

    /** Whether this entity's character stands on ground it can walk on. */
    public boolean isGrounded3D() {
        return requirePhysics3D().isCharacterGrounded(requireEntity("isGrounded3D"));
    }

    /** How fast this entity's character moved in its last physics step, in scene units per second. */
    public Vector3 getCharacterVelocity3D() {
        return requirePhysics3D().getCharacterVelocity(requireEntity("getCharacterVelocity3D"));
    }

    // --- Joints (TD-051) ---

    /**
     * Changes the motor of this entity's Joint 3D (a hinge or slider) while the game runs. {@code target} is degrees per second
     * or degrees (hinge), scene units per second or scene units (slider), depending on {@code mode}.
     *
     * @throws IllegalStateException when the scene runs no 3D physics, this entity has no joint yet, or the joint has no motor
     */
    public void setJointMotor3D(Joint3DComponent.MotorMode mode, float target) {
        requirePhysics3D().setJointMotor(requireEntity("setJointMotor3D"), mode, target);
    }

    /** How far this entity's hinge has turned (degrees) or slider has slid (scene units) from where it started. */
    public float getJointPosition3D() {
        return requirePhysics3D().getJointPosition(requireEntity("getJointPosition3D"));
    }

    private Entity requireEntity(String operation) {
        Entity entity = getEntity();
        if (entity == null) {
            throw new IllegalStateException(operation + ": the script is not attached to an entity");
        }
        return entity;
    }

    // --- Convenience Accessors for User Scripts ---

    public Scene getScene() {
        Entity entity = getEntity();
        return entity != null ? entity.getScene() : null;
    }

    public Transform getTransform() {
        Entity entity = getEntity();
        return entity != null ? entity.getTransform() : null;
    }

    public <T extends io.github.ldev22live.gdxunity.engine.scene.Component> T getComponent(Class<T> componentClass) {
        Entity entity = getEntity();
        return entity != null ? entity.getComponent(componentClass) : null;
    }
}
