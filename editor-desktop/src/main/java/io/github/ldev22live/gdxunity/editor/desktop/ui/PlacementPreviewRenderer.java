package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformGizmoMath;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;

import java.util.Objects;

/**
 * Draws the Place tool's ghost preview (roadmap A03): a ring on the ground where the chosen object would land, with a post rising
 * from it, green when a click there would place the object and red when it would be refused ({@link LevelSession#placementRefusal}).
 * The ring keeps a constant size on screen. It marks the spot; it is not a preview of the object's shape.
 */
public class PlacementPreviewRenderer {
    private static final Color VALID = new Color(0.30f, 0.95f, 0.45f, 1f);
    private static final Color REFUSED = new Color(1f, 0.30f, 0.25f, 1f);
    /** Ring radius in screen-relative units (scaled like gizmo handles). */
    private static final float RING_RADIUS = 14f;
    /** Height of the post, as a multiple of the ring radius. */
    private static final float POST_HEIGHT = 2.5f;
    private static final int SEGMENTS = 32;

    private final LevelSession level;
    private final Camera camera;
    private final Matrix4 identity = new Matrix4();
    private final Matrix4 previousTransform = new Matrix4();

    public PlacementPreviewRenderer(LevelSession level, Camera camera) {
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
    }

    public void render(ShapeRenderer shapeRenderer) {
        LevelSession.PlacementPreview preview = level.getPlacementPreview();
        if (shapeRenderer == null || preview == null || level.getEditorState().isPlayModeActive()) {
            return;
        }
        Vector3 p = preview.position();
        float scale = camera instanceof PerspectiveCamera3D perspective
            ? TransformGizmoMath.perspectiveScaleFactor(camera.getPosition(), p, perspective.getFieldOfView())
            : camera.getZoom() > 0f ? camera.getZoom() : 1f;
        float radius = RING_RADIUS * scale;
        previousTransform.set(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(identity);
        shapeRenderer.setProjectionMatrix(camera.getCombinedMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(preview.valid() ? VALID : REFUSED);
        float step = MathUtils.PI2 / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float a = i * step;
            float b = a + step;
            shapeRenderer.line(p.x + MathUtils.cos(a) * radius, p.y, p.z + MathUtils.sin(a) * radius,
                p.x + MathUtils.cos(b) * radius, p.y, p.z + MathUtils.sin(b) * radius);
        }
        shapeRenderer.line(p.x, p.y, p.z, p.x, p.y + radius * POST_HEIGHT, p.z);
        if (!preview.valid()) {
            // A cross through the ring, so a refused spot reads without relying on colour alone
            float d = radius * MathUtils.cosDeg(45f);
            shapeRenderer.line(p.x - d, p.y, p.z - d, p.x + d, p.y, p.z + d);
            shapeRenderer.line(p.x - d, p.y, p.z + d, p.x + d, p.y, p.z - d);
        }
        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(previousTransform);
    }
}
