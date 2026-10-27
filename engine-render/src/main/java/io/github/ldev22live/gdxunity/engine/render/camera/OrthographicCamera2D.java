package io.github.ldev22live.gdxunity.engine.render.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Objects;

/**
 * Standard 2D Orthographic Camera backed by LibGDX {@link OrthographicCamera} and {@link FitViewport}.
 * Automatically preserves the configured virtual aspect ratio when the viewport is resized.
 */
public class OrthographicCamera2D implements Camera {
    private final float virtualWidth;
    private final float virtualHeight;
    private final OrthographicCamera gdxCamera;
    private final Viewport viewport;

    public OrthographicCamera2D() {
        this(640f, 480f);
    }

    public OrthographicCamera2D(float virtualWidth, float virtualHeight) {
        if (virtualWidth <= 0 || virtualHeight <= 0) {
            throw new IllegalArgumentException("Virtual dimensions must be positive, got: " + virtualWidth + "x" + virtualHeight);
        }
        this.virtualWidth = virtualWidth;
        this.virtualHeight = virtualHeight;
        this.gdxCamera = new OrthographicCamera();
        this.viewport = new FitViewport(virtualWidth, virtualHeight, gdxCamera);

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

    public float getVirtualWidth() {
        return virtualWidth;
    }

    public float getVirtualHeight() {
        return virtualHeight;
    }
}
