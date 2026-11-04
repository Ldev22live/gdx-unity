package io.github.ldev22live.gdxunity.engine.render.components;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

/**
 * The game's view of the scene (TD-034). While the game runs, the first enabled camera on an active entity is used
 * instead of the editor's view camera; see {@link io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig}.
 *
 * <p>The camera sits at the entity's world position. A perspective camera looks along the entity's local -Z axis
 * with +Y up, so a script turns it with {@code getTransform().setLocalEulerAngles(yaw, pitch, 0)}. An orthographic
 * camera looks down -Z at the XY plane and ignores rotation.
 */
public class CameraComponent extends BaseComponent {

    public enum Projection {
        /** Flat 2D view; {@link #getOrthographicHeight()} world units fit the view height. */
        ORTHOGRAPHIC,
        /** 3D view with {@link #getFieldOfView()}. */
        PERSPECTIVE
    }

    /** Visible world height of an orthographic camera: the 640x480 design size of the 2D samples. */
    public static final float DEFAULT_ORTHOGRAPHIC_HEIGHT = 480f;
    /** Vertical field of view in degrees. */
    public static final float DEFAULT_FIELD_OF_VIEW = 60f;
    /** Near clip distance; scenes use pixel-sized units, so 1 unit is very close. */
    public static final float DEFAULT_NEAR_PLANE = 1f;
    /** Far clip distance, matching the editor's 3D view. */
    public static final float DEFAULT_FAR_PLANE = 50000f;

    private Projection projection;
    private float orthographicHeight = DEFAULT_ORTHOGRAPHIC_HEIGHT;
    private float fieldOfView = DEFAULT_FIELD_OF_VIEW;
    private float nearPlane = DEFAULT_NEAR_PLANE;
    private float farPlane = DEFAULT_FAR_PLANE;

    public CameraComponent() {
        this(Projection.ORTHOGRAPHIC);
    }

    public CameraComponent(Projection projection) {
        setProjection(projection);
    }

    public Projection getProjection() {
        return projection;
    }

    public void setProjection(Projection projection) {
        if (projection == null) {
            throw new IllegalArgumentException("projection cannot be null");
        }
        this.projection = projection;
    }

    public float getOrthographicHeight() {
        return orthographicHeight;
    }

    /** @throws IllegalArgumentException unless the height is positive and finite */
    public void setOrthographicHeight(float orthographicHeight) {
        if (!(orthographicHeight > 0f) || Float.isInfinite(orthographicHeight)) {
            throw new IllegalArgumentException("Orthographic height must be > 0: " + orthographicHeight);
        }
        this.orthographicHeight = orthographicHeight;
    }

    public float getFieldOfView() {
        return fieldOfView;
    }

    /** @throws IllegalArgumentException unless 0 < fieldOfView < 180 */
    public void setFieldOfView(float fieldOfView) {
        if (!(fieldOfView > 0f && fieldOfView < 180f)) {
            throw new IllegalArgumentException("Field of view must be between 0 and 180 degrees: " + fieldOfView);
        }
        this.fieldOfView = fieldOfView;
    }

    public float getNearPlane() {
        return nearPlane;
    }

    public float getFarPlane() {
        return farPlane;
    }

    /** @throws IllegalArgumentException unless 0 < near < far and both are finite */
    public void setClipPlanes(float nearPlane, float farPlane) {
        if (!(nearPlane > 0f) || !(farPlane > nearPlane) || Float.isInfinite(farPlane)) {
            throw new IllegalArgumentException("Clip planes need 0 < near < far: near=" + nearPlane + ", far=" + farPlane);
        }
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
    }
}
