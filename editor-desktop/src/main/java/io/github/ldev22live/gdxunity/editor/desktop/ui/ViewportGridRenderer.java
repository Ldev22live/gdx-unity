package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;

import java.util.Objects;

/**
 * Renders an infinite 2D world-space grid aligned to camera zoom and position.
 * Features stable grid spacing with minor and major lines that adapt as the camera zooms.
 */
public class ViewportGridRenderer {
    private final Camera camera;
    private boolean enabled = true;
    private float baseGridSize = 32.0f;

    private static final Color MINOR_GRID_COLOR = new Color(0.18f, 0.20f, 0.24f, 0.6f);
    private static final Color MAJOR_GRID_COLOR = new Color(0.24f, 0.27f, 0.32f, 0.8f);
    private static final Color AXIS_X_COLOR = new Color(0.6f, 0.25f, 0.25f, 0.9f);
    private static final Color AXIS_Y_COLOR = new Color(0.25f, 0.6f, 0.25f, 0.9f);

    public ViewportGridRenderer(Camera camera) {
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getBaseGridSize() {
        return baseGridSize;
    }

    public void setBaseGridSize(float baseGridSize) {
        this.baseGridSize = Math.max(8.0f, baseGridSize);
    }

    /**
     * Renders grid lines in world coordinates using camera projection.
     */
    public void render(ShapeRenderer shapeRenderer, float zoom) {
        if (!enabled || shapeRenderer == null || camera == null) {
            return;
        }

        // Determine dynamic step based on zoom so lines never get too dense or sparse
        float effectiveZoom = (zoom <= 0f) ? 1.0f : zoom;
        float spacing = baseGridSize;
        while (spacing / effectiveZoom < 16.0f) {
            spacing *= 2.0f;
        }
        while (spacing / effectiveZoom > 128.0f) {
            spacing /= 2.0f;
        }

        Vector3 camPos = camera.getPosition();
        float viewW = (camera.getViewport() != null) ? camera.getViewport().getWorldWidth() : 640f;
        float viewH = (camera.getViewport() != null) ? camera.getViewport().getWorldHeight() : 480f;
        float halfW = (viewW * effectiveZoom) * 0.65f;
        float halfH = (viewH * effectiveZoom) * 0.65f;

        float startX = (float) (Math.floor((camPos.x - halfW) / spacing) * spacing);
        float endX = (float) (Math.ceil((camPos.x + halfW) / spacing) * spacing);
        float startY = (float) (Math.floor((camPos.y - halfH) / spacing) * spacing);
        float endY = (float) (Math.ceil((camPos.y + halfH) / spacing) * spacing);

        Matrix4 origTransform = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(new Matrix4());
        shapeRenderer.setProjectionMatrix(camera.getCombinedMatrix());

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // 1. Draw vertical grid lines
        for (float x = startX; x <= endX; x += spacing) {
            boolean isMajor = (Math.abs(x) % (spacing * 5.0f) < 0.001f);
            if (Math.abs(x) < 0.001f) {
                shapeRenderer.setColor(AXIS_Y_COLOR);
            } else {
                shapeRenderer.setColor(isMajor ? MAJOR_GRID_COLOR : MINOR_GRID_COLOR);
            }
            shapeRenderer.line(x, startY, x, endY);
        }

        // 2. Draw horizontal grid lines
        for (float y = startY; y <= endY; y += spacing) {
            boolean isMajor = (Math.abs(y) % (spacing * 5.0f) < 0.001f);
            if (Math.abs(y) < 0.001f) {
                shapeRenderer.setColor(AXIS_X_COLOR);
            } else {
                shapeRenderer.setColor(isMajor ? MAJOR_GRID_COLOR : MINOR_GRID_COLOR);
            }
            shapeRenderer.line(startX, y, endX, y);
        }

        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(origTransform);
    }
}
