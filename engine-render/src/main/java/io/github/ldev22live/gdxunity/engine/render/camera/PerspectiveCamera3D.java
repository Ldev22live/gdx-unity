package io.github.ldev22live.gdxunity.engine.render.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Objects;

/**
 * 3D Perspective Camera backed by LibGDX {@link PerspectiveCamera}.
 * Provides configurable FOV, near/far planes, and viewport handling for 3D rendering.
 */
public class PerspectiveCamera3D implements Camera {
    private final PerspectiveCamera gdxCamera;
    private final Viewport viewport;

    private float fieldOfView = 67f;
    private float nearPlane = 0.1f;
    private float farPlane = 100f;

    public PerspectiveCamera3D() {
        this(67f, 0.1f, 100f);
    }

    public PerspectiveCamera3D(float fieldOfView, float nearPlane, float farPlane) {
        this(fieldOfView,
            (Gdx.graphics != null && Gdx.graphics.getWidth() > 0) ? Gdx.graphics.getWidth() : 800f,
            (Gdx.graphics != null && Gdx.graphics.getHeight() > 0) ? Gdx.graphics.getHeight() : 600f,
            nearPlane, farPlane);
    }

    public PerspectiveCamera3D(float fieldOfView, float viewportWidth, float viewportHeight, float nearPlane, float farPlane) {
        if (fieldOfView <= 0 || fieldOfView >= 180) {
            throw new IllegalArgumentException("Field of view must be between 0 and 180 degrees, got: " + fieldOfView);
        }
        if (nearPlane <= 0) {
            throw new IllegalArgumentException("Near plane must be positive, got: " + nearPlane);
        }
        if (farPlane <= nearPlane) {
            throw new IllegalArgumentException("Far plane must be greater than near plane, got: near=" + nearPlane + ", far=" + farPlane);
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive, got: " + viewportWidth + "x" + viewportHeight);
        }

        this.fieldOfView = fieldOfView;
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
        this.gdxCamera = new PerspectiveCamera(fieldOfView, viewportWidth, viewportHeight);
        this.gdxCamera.near = nearPlane;
        this.gdxCamera.far = farPlane;

        // Use ScreenViewport for flexible 3D viewport handling
        this.viewport = new com.badlogic.gdx.utils.viewport.ScreenViewport(gdxCamera);

        // Position camera at default offset looking down -Z axis
        this.gdxCamera.position.set(0f, 0f, 10f);
        this.gdxCamera.direction.set(0f, 0f, -1f);
        this.gdxCamera.up.set(0f, 1f, 0f);
        this.gdxCamera.update();

        if (Gdx.graphics != null && Gdx.graphics.getWidth() > 0 && Gdx.graphics.getHeight() > 0) {
            this.viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        } else {
            this.viewport.setWorldSize(viewportWidth, viewportHeight);
            this.viewport.setScreenSize((int) viewportWidth, (int) viewportHeight);
        }
    }

    @Override
    public Vector3 getPosition() {
        return new Vector3(gdxCamera.position);
    }

    @Override
    public void setPosition(float x, float y, float z) {
        gdxCamera.position.set(x, y, z);
    }

    @Override
    public void setPosition(Vector3 position) {
        Objects.requireNonNull(position, "position cannot be null");
        setPosition(position.x, position.y, position.z);
    }

    @Override
    public void translate(float x, float y, float z) {
        gdxCamera.translate(x, y, z);
    }

    @Override
    public float getZoom() {
        // Perspective cameras don't have zoom in the same sense as orthographic
        // Return 1.0f as a neutral value
        return 1.0f;
    }

    @Override
    public void setZoom(float zoom) {
        // Perspective cameras don't support zoom in the same way
        // This is a no-op for perspective cameras
        // FOV can be adjusted instead via setFieldOfView()
    }

    @Override
    public Matrix4 getCombinedMatrix() {
        return new Matrix4(gdxCamera.combined);
    }

    @Override
    public com.badlogic.gdx.graphics.Camera getGdxCamera() {
        return gdxCamera;
    }

    @Override
    public Viewport getViewport() {
        return viewport;
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            if (Gdx.graphics != null) {
                viewport.update(width, height, true);
            } else {
                viewport.setWorldSize(width, height);
                viewport.setScreenSize(width, height);
                gdxCamera.viewportWidth = width;
                gdxCamera.viewportHeight = height;
                gdxCamera.update();
            }
        }
    }

    @Override
    public void update() {
        gdxCamera.update();
    }

    /**
     * Gets the field of view in degrees.
     */
    public float getFieldOfView() {
        return fieldOfView;
    }

    /**
     * Sets the field of view in degrees.
     */
    public void setFieldOfView(float fieldOfView) {
        if (fieldOfView <= 0 || fieldOfView >= 180) {
            throw new IllegalArgumentException("Field of view must be between 0 and 180 degrees, got: " + fieldOfView);
        }
        this.fieldOfView = fieldOfView;
        this.gdxCamera.fieldOfView = fieldOfView;
    }

    /**
     * Gets the near clipping plane distance.
     */
    public float getNearPlane() {
        return nearPlane;
    }

    /**
     * Sets the near clipping plane distance.
     */
    public void setNearPlane(float nearPlane) {
        if (nearPlane <= 0) {
            throw new IllegalArgumentException("Near plane must be positive, got: " + nearPlane);
        }
        if (nearPlane >= farPlane) {
            throw new IllegalArgumentException("Near plane must be less than far plane, got: near=" + nearPlane + ", far=" + farPlane);
        }
        this.nearPlane = nearPlane;
        this.gdxCamera.near = nearPlane;
    }

    /**
     * Gets the far clipping plane distance.
     */
    public float getFarPlane() {
        return farPlane;
    }

    /**
     * Sets the far clipping plane distance.
     */
    public void setFarPlane(float farPlane) {
        if (farPlane <= nearPlane) {
            throw new IllegalArgumentException("Far plane must be greater than near plane, got: near=" + nearPlane + ", far=" + farPlane);
        }
        this.farPlane = farPlane;
        this.gdxCamera.far = farPlane;
    }

    /**
     * Gets the camera's direction vector (where it's looking).
     */
    public Vector3 getDirection() {
        return new Vector3(gdxCamera.direction);
    }

    /**
     * Sets the camera's direction vector.
     */
    public void setDirection(Vector3 direction) {
        Objects.requireNonNull(direction, "direction cannot be null");
        gdxCamera.direction.set(direction).nor();
    }

    /**
     * Gets the camera's up vector.
     */
    public Vector3 getUp() {
        return new Vector3(gdxCamera.up);
    }

    /**
     * Sets the camera's up vector.
     */
    public void setUp(Vector3 up) {
        Objects.requireNonNull(up, "up cannot be null");
        gdxCamera.up.set(up).nor();
    }

    /**
     * Rotates the camera by the given Euler angles in degrees (yaw around Y, pitch around camera right axis, roll around direction).
     */
     public void rotate(float yaw, float pitch, float roll) {
         if (yaw != 0f) {
             gdxCamera.rotate(Vector3.Y, yaw);
         }
         if (pitch != 0f) {
             Vector3 right = new Vector3(gdxCamera.direction).crs(gdxCamera.up).nor();
             gdxCamera.rotate(right, pitch);
         }
         if (roll != 0f) {
             gdxCamera.rotate(gdxCamera.direction, roll);
         }
     }

    /**
     * Rotates the camera around a specific axis by angle in degrees.
     */
    public void rotate(Vector3 axis, float angle) {
        Objects.requireNonNull(axis, "axis cannot be null");
        gdxCamera.rotate(axis, angle);
    }

    /**
     * Rotates the camera around a target point.
     */
    public void rotateAround(Vector3 point, Vector3 axis, float angle) {
        Objects.requireNonNull(point, "point cannot be null");
        Objects.requireNonNull(axis, "axis cannot be null");
        gdxCamera.rotateAround(point, axis, angle);
    }

    /**
     * Looks at a target position from the current camera position.
     */
    public void lookAt(Vector3 target) {
        Objects.requireNonNull(target, "target cannot be null");
        gdxCamera.lookAt(target);
    }

    /**
     * Looks at a target position from the current camera position.
     */
    public void lookAt(float x, float y, float z) {
        gdxCamera.lookAt(x, y, z);
    }
}
