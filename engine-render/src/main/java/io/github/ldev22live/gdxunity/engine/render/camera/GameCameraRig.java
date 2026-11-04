package io.github.ldev22live.gdxunity.engine.render.camera;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.List;
import java.util.Objects;

/**
 * Turns a scene's {@link CameraComponent} into a camera the renderers can use (TD-034). It owns one orthographic and
 * one perspective camera, separate from any editor camera, and repositions the matching one from the component and
 * its entity each frame. Works without a graphics context, so it can be unit-tested.
 */
public final class GameCameraRig {
    /** Local axis a perspective camera looks along. */
    private static final Vector3 LOCAL_FORWARD = new Vector3(0f, 0f, -1f);
    /** Local axis that is up for a perspective camera. */
    private static final Vector3 LOCAL_UP = new Vector3(0f, 1f, 0f);

    private final OrthographicCamera2D orthographic = OrthographicCamera2D.createScreenFilling(0f, 0f);
    private final PerspectiveCamera3D perspective = new PerspectiveCamera3D(CameraComponent.DEFAULT_FIELD_OF_VIEW,
        1f, 1f, CameraComponent.DEFAULT_NEAR_PLANE, CameraComponent.DEFAULT_FAR_PLANE);
    private final Vector3 position = new Vector3();
    private final Quaternion rotation = new Quaternion();
    private final Vector3 axis = new Vector3();
    private int viewHeight = 1;

    /**
     * The scene's camera: the first enabled {@link CameraComponent} on an entity that is active in the hierarchy,
     * in entity order; null when there is none.
     */
    public static CameraComponent findActiveCamera(Scene scene) {
        if (scene == null) {
            return null;
        }
        List<Entity> entities = scene.getAllEntities();
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (!entity.isActiveInHierarchy()) {
                continue;
            }
            List<Component> components = entity.getAllComponents();
            for (int j = 0; j < components.size(); j++) {
                if (components.get(j) instanceof CameraComponent camera && camera.isEnabled()) {
                    return camera;
                }
            }
        }
        return null;
    }

    /** Sets the size of the view the game is drawn into, in pixels. */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("View size must be positive: " + width + "x" + height);
        }
        viewHeight = height;
        orthographic.resize(width, height);
        perspective.resize(width, height);
    }

    /**
     * Places the camera for {@code camera}'s projection at its entity's world transform and returns it.
     *
     * @throws IllegalArgumentException if the component is not attached to an entity
     */
    public Camera apply(CameraComponent camera) {
        Objects.requireNonNull(camera, "camera cannot be null");
        Entity entity = camera.getEntity();
        if (entity == null) {
            throw new IllegalArgumentException("CameraComponent is not attached to an entity");
        }
        Transform transform = entity.getTransform();
        position.set(transform.getWorldPosition());
        if (camera.getProjection() == CameraComponent.Projection.ORTHOGRAPHIC) {
            orthographic.setPosition(position.x, position.y, 0f);
            // A screen-filling camera shows viewHeight * zoom world units vertically
            orthographic.setZoom(camera.getOrthographicHeight() / viewHeight);
            orthographic.update();
            return orthographic;
        }
        rotation.set(transform.getWorldRotation());
        perspective.setFieldOfView(camera.getFieldOfView());
        // Widen first so the near plane is always below the far plane while both change
        perspective.setFarPlane(Math.max(camera.getFarPlane(), perspective.getNearPlane() + 1f));
        perspective.setNearPlane(camera.getNearPlane());
        perspective.setFarPlane(camera.getFarPlane());
        perspective.setPosition(position);
        perspective.setDirection(rotation.transform(axis.set(LOCAL_FORWARD)));
        perspective.setUp(rotation.transform(axis.set(LOCAL_UP)));
        perspective.update();
        return perspective;
    }
}
