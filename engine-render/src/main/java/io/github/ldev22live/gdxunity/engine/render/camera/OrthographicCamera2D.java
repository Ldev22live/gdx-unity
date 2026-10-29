package io.github.ldev22live.gdxunity.engine.render.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Objects;

/**
 * Standard 2D Orthographic Camera backed by LibGDX {@link OrthographicCamera} and {@link FitViewport}.
 * Automatically preserves the configured virtual aspect ratio when the viewport is resized.
 *
 * <p>{@link #createScreenFilling(float, float)} creates the editor variant (TD-024): a {@link ScreenViewport}
 * that always fills its render target at one world unit per pixel when zoom is 1.
 */
public class OrthographicCamera2D implements Camera {
    private final float virtualWidth;
    private final float virtualHeight;
    private final OrthographicCamera gdxCamera;
    private final Viewport viewport;
    private final boolean screenFilling;

    /**
     * Depth range in front of and behind the screen-filling editor camera, so 3D primitives centered on
     * z = 0 are not clipped by the near plane (orthographic cameras allow a negative near plane).
     */
    public static final float EDITOR_DEPTH_RANGE = 20000f;

    public OrthographicCamera2D() {
        this(640f, 480f);
    }

    public OrthographicCamera2D(float virtualWidth, float virtualHeight) {
        this(virtualWidth, virtualHeight, false);
    }

    /**
     * Creates a camera whose viewport always fills its render target (TD-024), centered on the given world
     * position. Used by the editor scene view; games keep the fixed virtual resolution.
     */
    public static OrthographicCamera2D createScreenFilling(float centerX, float centerY) {
        // The virtual size only seeds the initial viewport; a screen-filling camera takes its size from resize()
        OrthographicCamera2D camera = new OrthographicCamera2D(1f, 1f, true);
        camera.gdxCamera.near = -EDITOR_DEPTH_RANGE;
        camera.gdxCamera.far = EDITOR_DEPTH_RANGE;
        camera.setPosition(centerX, centerY, 0f);
        camera.update();
        return camera;
    }

    private OrthographicCamera2D(float virtualWidth, float virtualHeight, boolean screenFilling) {
        if (virtualWidth <= 0 || virtualHeight <= 0) {
            throw new IllegalArgumentException("Virtual dimensions must be positive, got: " + virtualWidth + "x" + virtualHeight);
        }
        this.virtualWidth = virtualWidth;
        this.virtualHeight = virtualHeight;
        this.screenFilling = screenFilling;
        this.gdxCamera = new OrthographicCamera();
        this.viewport = screenFilling ? new ScreenViewport(gdxCamera) : new FitViewport(virtualWidth, virtualHeight, gdxCamera);

        // Center camera in the virtual viewport (0,0 to virtualWidth, virtualHeight)
        this.gdxCamera.position.set(virtualWidth / 2f, virtualHeight / 2f, 0f);
        this.gdxCamera.update();

        if (Gdx.graphics != null) {
            this.viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);
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
        return gdxCamera.zoom;
    }

    @Override
    public void setZoom(float zoom) {
        if (zoom <= 0) {
            throw new IllegalArgumentException("Zoom must be positive, got: " + zoom);
        }
        gdxCamera.zoom = zoom;
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
                viewport.update(width, height, false);
            } else if (screenFilling) {
                viewport.setWorldSize(width, height);
                viewport.setScreenSize(width, height);
                gdxCamera.viewportWidth = width;
                gdxCamera.viewportHeight = height;
            } else {
                viewport.setWorldSize(virtualWidth, virtualHeight);
                viewport.setScreenSize(width, height);
            }
        }
    }

    @Override
    public void update() {
        gdxCamera.update();
    }

    /** True for the editor variant created by {@link #createScreenFilling(float, float)}. */
    public boolean isScreenFilling() {
        return screenFilling;
    }

    public float getVirtualWidth() {
        return virtualWidth;
    }

    public float getVirtualHeight() {
        return virtualHeight;
    }
}
