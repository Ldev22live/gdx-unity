package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;

import java.util.Objects;

/**
 * Ground grid for the 3D scene view: lines on the XZ plane (y = 0) with the X axis in red and the Z axis in
 * blue. Line spacing adapts to the camera height so the grid stays readable when zooming out.
 */
public class ViewportGridRenderer3D {

    private static final Color MINOR_COLOR = new Color(0.32f, 0.34f, 0.38f, 0.55f);
    private static final Color MAJOR_COLOR = new Color(0.42f, 0.44f, 0.50f, 0.85f);
    private static final Color AXIS_X_COLOR = new Color(0.85f, 0.25f, 0.25f, 1f);
    private static final Color AXIS_Z_COLOR = new Color(0.25f, 0.45f, 0.95f, 1f);

    /** Every this-many lines is drawn as a major line. */
    private static final int MAJOR_LINE_INTERVAL = 10;
    /** Lines drawn on each side of the camera, in multiples of the spacing. */
    private static final int LINES_PER_SIDE = 60;
    /** Smallest grid spacing in world units. */
    private static final float MIN_SPACING = 1f;
    /** Spacing targets about this many grid cells across the camera height. */
    private static final float CELLS_PER_HEIGHT = 4f;

    private final Camera camera;
    private final Matrix4 identity = new Matrix4();

    public ViewportGridRenderer3D(Camera camera) {
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
    }

    /**
     * Grid spacing for a camera at the given height above the ground: a power of ten roughly a quarter of
     * the height, never below {@link #MIN_SPACING}.
     */
    static float gridSpacing(float cameraHeight) {
        float target = Math.max(MIN_SPACING, Math.abs(cameraHeight) / CELLS_PER_HEIGHT);
        return (float) Math.pow(10, Math.floor(Math.log10(target)));
    }

    public void render(ShapeRenderer shapeRenderer) {
        Objects.requireNonNull(shapeRenderer, "shapeRenderer cannot be null");
        Vector3 cameraPosition = camera.getPosition();
        float spacing = gridSpacing(cameraPosition.y);
        float extent = spacing * LINES_PER_SIDE;
        long centerX = Math.round(cameraPosition.x / spacing);
        long centerZ = Math.round(cameraPosition.z / spacing);
        float minX = (centerX - LINES_PER_SIDE) * spacing;
        float maxX = minX + 2f * extent;
        float minZ = (centerZ - LINES_PER_SIDE) * spacing;
        float maxZ = minZ + 2f * extent;

        Matrix4 previousTransform = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(identity);
        shapeRenderer.setProjectionMatrix(camera.getCombinedMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        for (long i = centerX - LINES_PER_SIDE; i <= centerX + LINES_PER_SIDE; i++) {
            float x = i * spacing;
            shapeRenderer.setColor(i == 0 ? AXIS_Z_COLOR : (i % MAJOR_LINE_INTERVAL == 0 ? MAJOR_COLOR : MINOR_COLOR));
            shapeRenderer.line(x, 0f, minZ, x, 0f, maxZ);
        }
        for (long i = centerZ - LINES_PER_SIDE; i <= centerZ + LINES_PER_SIDE; i++) {
            float z = i * spacing;
            shapeRenderer.setColor(i == 0 ? AXIS_X_COLOR : (i % MAJOR_LINE_INTERVAL == 0 ? MAJOR_COLOR : MINOR_COLOR));
            shapeRenderer.line(minX, 0f, z, maxX, 0f, z);
        }

        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(previousTransform);
    }
}
