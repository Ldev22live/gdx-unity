package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.viewport.OrthographicViewCamera;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewKind;

import java.util.Objects;

/**
 * The grid of an axis view (TD-039): lines on the plane square to the view direction, through the origin, spaced in
 * powers of ten to suit the zoom, with the two axes that lie in the plane drawn in their colours (X red, Y green,
 * Z blue).
 */
public final class AxisGridRenderer {
    private static final Color MINOR_COLOR = new Color(0.32f, 0.34f, 0.38f, 0.55f);
    private static final Color MAJOR_COLOR = new Color(0.42f, 0.44f, 0.50f, 0.85f);
    private static final Color AXIS_X_COLOR = new Color(0.85f, 0.25f, 0.25f, 1f);
    private static final Color AXIS_Y_COLOR = new Color(0.25f, 0.8f, 0.3f, 1f);
    private static final Color AXIS_Z_COLOR = new Color(0.25f, 0.45f, 0.95f, 1f);
    private static final int MAJOR_LINE_INTERVAL = 10;
    /** Lines are drawn this many times further out than the visible height, so panning shows no edge. */
    private static final float EXTENT_IN_HEIGHTS = 6f;
    /** Never draw more lines per direction than this, however far the user zooms out. */
    private static final int MAX_LINES = 400;

    private final OrthographicViewCamera view;
    private final Matrix4 identity = new Matrix4();

    /** Creates the grid for one axis-view pane. */
    public AxisGridRenderer(OrthographicViewCamera view) {
        this.view = Objects.requireNonNull(view, "view cannot be null");
    }

    /** Draws the grid. */
    public void render(ShapeRenderer shapeRenderer) {
        Objects.requireNonNull(shapeRenderer, "shapeRenderer cannot be null");
        ViewKind kind = view.getKind();
        Vector3 focus = view.getFocus();
        float spacing = ViewportGridRenderer3D.gridSpacing(view.getVisibleHeight());
        float extent = view.getVisibleHeight() * EXTENT_IN_HEIGHTS;
        int lines = Math.min(MAX_LINES, Math.max(10, (int) (extent / spacing)));

        // The two scene axes that span the screen: "across" and "up" in scene coordinates
        int across = kind == ViewKind.X_AXIS ? 2 : 0;
        int up = kind == ViewKind.Y_AXIS ? 2 : 1;
        float[] center = {focus.x, focus.y, focus.z};

        Matrix4 previous = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(identity);
        shapeRenderer.setProjectionMatrix(view.getCamera().getCombinedMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        long centerAcross = Math.round(center[across] / spacing);
        long centerUp = Math.round(center[up] / spacing);
        float acrossMin = (centerAcross - lines) * spacing;
        float acrossMax = (centerAcross + lines) * spacing;
        float upMin = (centerUp - lines) * spacing;
        float upMax = (centerUp + lines) * spacing;
        for (long i = centerAcross - lines; i <= centerAcross + lines; i++) {
            shapeRenderer.setColor(i == 0 ? axisColor(up) : (i % MAJOR_LINE_INTERVAL == 0 ? MAJOR_COLOR : MINOR_COLOR));
            line(shapeRenderer, across, up, i * spacing, upMin, i * spacing, upMax);
        }
        for (long i = centerUp - lines; i <= centerUp + lines; i++) {
            shapeRenderer.setColor(i == 0 ? axisColor(across) : (i % MAJOR_LINE_INTERVAL == 0 ? MAJOR_COLOR : MINOR_COLOR));
            line(shapeRenderer, across, up, acrossMin, i * spacing, acrossMax, i * spacing);
        }
        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(previous);
    }

    /** The colour of the scene axis a line lying along it stands for. */
    private static Color axisColor(int axis) {
        return axis == 0 ? AXIS_X_COLOR : (axis == 1 ? AXIS_Y_COLOR : AXIS_Z_COLOR);
    }

    /** Draws a line given in the (across, up) coordinates of the view as a line in scene coordinates. */
    private static void line(ShapeRenderer sr, int across, int up, float a0, float u0, float a1, float u1) {
        float[] from = new float[3];
        float[] to = new float[3];
        from[across] = a0;
        from[up] = u0;
        to[across] = a1;
        to[up] = u1;
        sr.line(from[0], from[1], from[2], to[0], to[1], to[2]);
    }
}
