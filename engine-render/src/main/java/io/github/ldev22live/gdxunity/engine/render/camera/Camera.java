package io.github.ldev22live.gdxunity.engine.render.camera;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Camera abstraction providing view and projection matrices, position control, and viewport integration.
 */
public interface Camera {
    /**
     * Gets the camera's world position.
     */
    Vector3 getPosition();

    /**
     * Sets the camera's position.
     */
    void setPosition(float x, float y, float z);

    /**
     * Sets the camera's position.
     */
    void setPosition(Vector3 position);

    /**
     * Moves the camera relative to its current position.
     */
    void translate(float x, float y, float z);

    /**
     * Gets the camera zoom factor (for orthographic cameras, 1.0 = normal).
     */
    float getZoom();

    /**
     * Sets the camera zoom factor.
     */
    void setZoom(float zoom);

    /**
     * Gets the combined view and projection matrix.
     */
    Matrix4 getCombinedMatrix();

    /**
     * Gets the underlying LibGDX camera instance.
     */
    com.badlogic.gdx.graphics.Camera getGdxCamera();

    /**
     * Gets the viewport associated with this camera.
     */
    Viewport getViewport();

    /**
     * Updates viewport dimensions on presentation surface resize.
     */
    void resize(int width, int height);

    /**
     * Recalculates matrices if dirty.
     */
    void update();
}
