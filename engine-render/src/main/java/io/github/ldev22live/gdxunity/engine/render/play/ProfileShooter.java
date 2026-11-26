package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.play.GameTestProfile;
import io.github.ldev22live.gdxunity.engine.scene.play.InputAction;
import io.github.ldev22live.gdxunity.engine.scene.play.InputMapping;
import io.github.ldev22live.gdxunity.engine.scene.play.InputSource;
import io.github.ldev22live.gdxunity.engine.scene.play.RuntimeOnlyComponent;

import java.util.Objects;
import java.util.Optional;

/**
 * The shot a genre profile puts on the player of the Play copy (roadmap M3b.7): once per press of the fire key, a ray up to the
 * profile's range. First-person Shooter + 3D shoots from the game camera along its view
 * ({@link GameTestProfile.ShotSetup.Origin#CAMERA_VIEW}); Third-person Shooter + 3D from the player, at its aim height, level along
 * the way it faces ({@link GameTestProfile.ShotSetup.Origin#PLAYER_FACING}). The player's own shape is passed through. A dynamic
 * rigid body it hits gains the profile's knock speed along the shot (an impulse of that times its mass); anything else hit stops
 * the shot and is only reported. Never saved.
 */
public class ProfileShooter extends BaseComponent implements RuntimeOnlyComponent {
    /** How far the ray moves on after passing through the player's own shape. */
    private static final float SKIP_STEP = 10f;
    /** How often the ray may pass through the player's own shape. */
    private static final int MAX_PLAYER_SKIPS = 16;

    private final GameTestProfile.ShotSetup shot;
    private final InputMapping keys;
    private final InputSource input;
    private final Vector3 origin = new Vector3();
    private final Vector3 direction = new Vector3();
    private boolean fireHeld;
    private int shots;
    private int knocks;
    private Entity lastHit;

    public ProfileShooter(GameTestProfile.ShotSetup shot, InputMapping keys, InputSource input) {
        this.shot = Objects.requireNonNull(shot, "shot cannot be null");
        this.keys = Objects.requireNonNull(keys, "keys cannot be null");
        this.input = Objects.requireNonNull(input, "input cannot be null");
    }

    /**
     * @throws IllegalStateException when the scene has no 3D physics or no game camera to shoot from
     */
    @Override
    public void onUpdate(float deltaTime) {
        boolean firePressed = keys.isActive(InputAction.FIRE, input);
        boolean fire = firePressed && !fireHeld;
        fireHeld = firePressed;
        if (!fire) {
            return;
        }
        Scene scene = getEntity().getScene();
        PhysicsSystem3D physics = scene != null ? scene.getPhysics3D() : null;
        if (physics == null) {
            throw new IllegalStateException("'" + getEntity().getName() + "' shoots with its genre profile, which needs 3D physics");
        }
        if (!aim(scene)) {
            return;
        }
        shots++;
        lastHit = null;
        float left = shot.range();
        for (int skips = 0; skips <= MAX_PLAYER_SKIPS && left > 0f; skips++) {
            Optional<PhysicsSystem3D.RaycastHit> hit = physics.raycast(origin, direction, left, PhysicsLayers3D.ALL_LAYERS);
            if (hit.isEmpty()) {
                return;
            }
            Entity entity = hit.get().entity();
            if (entity != getEntity()) {
                lastHit = entity;
                if (physics.isDynamic(entity)) {
                    physics.addImpulse(entity, new Vector3(direction).scl(shot.knockSpeed() * physics.getMass(entity)));
                    knocks++;
                }
                return;
            }
            // The shot starts inside the player (the eyes or the chest are in its capsule): carry on from just beyond that point
            left -= hit.get().distance() + SKIP_STEP;
            origin.set(hit.get().point()).mulAdd(direction, SKIP_STEP);
        }
    }

    /**
     * Sets {@link #origin} and {@link #direction} for the profile's {@link GameTestProfile.ShotSetup.Origin}.
     *
     * @return false when the player faces straight up or down, so it has no level direction to shoot along (nothing is fired)
     * @throws IllegalStateException when a shot along the camera's view finds no game camera
     */
    private boolean aim(Scene scene) {
        if (shot.origin() == GameTestProfile.ShotSetup.Origin.CAMERA_VIEW) {
            CameraComponent camera = GameCameraRig.findActiveCamera(scene);
            if (camera == null) {
                throw new IllegalStateException("'" + getEntity().getName() + "' shoots along the game camera's view, and the scene "
                    + "has no camera");
            }
            origin.set(camera.getEntity().getTransform().getWorldPosition());
            camera.getEntity().getTransform().getWorldRotation().transform(direction.set(0f, 0f, -1f)).nor();
            return true;
        }
        getEntity().getTransform().getWorldRotation().transform(direction.set(0f, 0f, -1f));
        direction.y = 0f;
        if (direction.isZero(1e-6f)) {
            return false;
        }
        direction.nor();
        origin.set(getEntity().getTransform().getWorldPosition()).add(0f, shot.aimHeight(), 0f);
        return true;
    }

    /** How many shots were fired since Play began. */
    public int getShots() {
        return shots;
    }

    /** How many shots knocked a dynamic body. */
    public int getKnocks() {
        return knocks;
    }

    /** What the last shot hit, or empty when it hit nothing (or nothing has been fired). */
    public Optional<Entity> getLastHit() {
        return Optional.ofNullable(lastHit);
    }
}
