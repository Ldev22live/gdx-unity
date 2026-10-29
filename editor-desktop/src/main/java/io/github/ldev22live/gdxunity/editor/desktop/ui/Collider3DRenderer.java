package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsUnits3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ShapeDescriptor3D;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Wireframes of 3D colliders in the 3D view: always for the selection, for every entity while the physics debug
 * overlay is on. Shapes come from {@link PhysicsSystem3D#describe}, so what is drawn is what the simulation
 * uses (including how scale applies to spheres and capsules). Solid colliders are green, triggers cyan.
 */
public class Collider3DRenderer {
    private static final Color SOLID_COLOR = new Color(0.35f, 0.9f, 0.4f, 1f);
    private static final Color TRIGGER_COLOR = new Color(0.3f, 0.85f, 0.95f, 1f);
    /** Line segments per full circle. */
    private static final int CIRCLE_SEGMENTS = 32;

    private final EditorState editorState;
    private final Camera camera;
    private final BooleanSupplier showAll;
    private final Matrix4 identity = new Matrix4();
    private final Matrix4 bodyMatrix = new Matrix4();

    /** @param showAll true when every collider should be drawn, not only the selection's */
    public Collider3DRenderer(EditorState editorState, Camera camera, BooleanSupplier showAll) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
        this.showAll = Objects.requireNonNull(showAll, "showAll cannot be null");
    }

    public void render(ShapeRenderer shapeRenderer) {
        Scene scene = editorState.getActiveScene();
        if (shapeRenderer == null || scene == null) {
            return;
        }
        boolean all = showAll.getAsBoolean();
        if (!all && editorState.getSelectedEntityIds().isEmpty()) {
            return;
        }
        Matrix4 previousTransform = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(identity);
        shapeRenderer.setProjectionMatrix(camera.getCombinedMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Entity entity : scene.getAllEntities()) {
            if (!all && !editorState.isSelected(entity.getId())) {
                continue;
            }
            for (Collider3DComponent collider : entity.getComponents(Collider3DComponent.class)) {
                if (collider.isEnabled()) {
                    drawCollider(shapeRenderer, entity.getTransform(), collider);
                }
            }
        }
        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(previousTransform);
    }

    private void drawCollider(ShapeRenderer sr, Transform transform, Collider3DComponent collider) {
        ShapeDescriptor3D shape = PhysicsSystem3D.describe(collider, transform.getWorldScale());
        float u = PhysicsUnits3D.UNITS_PER_METER;
        // Body space: the entity's world position and rotation without scale (scale is already in the shape)
        bodyMatrix.idt().translate(transform.getWorldPosition()).rotate(transform.getWorldRotation())
            .translate(new Vector3(shape.offset()).scl(u));
        sr.setColor(collider.isTrigger() ? TRIGGER_COLOR : SOLID_COLOR);
        switch (shape.kind()) {
            case BOX -> drawBox(sr, new Vector3(shape.halfExtents()).scl(u));
            case SPHERE -> {
                float r = shape.radius() * u;
                drawCircle(sr, r, 0f, Axis.XY);
                drawCircle(sr, r, 0f, Axis.XZ);
                drawCircle(sr, r, 0f, Axis.YZ);
            }
            case CAPSULE -> drawCapsule(sr, shape.radius() * u, shape.halfHeight() * u);
        }
    }

    private void drawBox(ShapeRenderer sr, Vector3 half) {
        Vector3[] corners = new Vector3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = point((i & 1) == 0 ? -half.x : half.x, (i & 2) == 0 ? -half.y : half.y, (i & 4) == 0 ? -half.z : half.z);
        }
        // Corners whose indices differ in exactly one bit share an edge
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit < 8; bit <<= 1) {
                if ((i & bit) == 0) {
                    sr.line(corners[i], corners[i | bit]);
                }
            }
        }
    }

    private void drawCapsule(ShapeRenderer sr, float radius, float halfHeight) {
        drawCircle(sr, radius, halfHeight, Axis.XZ);
        drawCircle(sr, radius, -halfHeight, Axis.XZ);
        float[][] sides = {{radius, 0f}, {-radius, 0f}, {0f, radius}, {0f, -radius}};
        for (float[] side : sides) {
            sr.line(point(side[0], -halfHeight, side[1]), point(side[0], halfHeight, side[1]));
        }
        // Half circles closing the ends, in the XY and ZY planes
        for (int cap = -1; cap <= 1; cap += 2) {
            for (int i = 0; i < CIRCLE_SEGMENTS / 2; i++) {
                float a0 = MathUtils.PI * i / (CIRCLE_SEGMENTS / 2f);
                float a1 = MathUtils.PI * (i + 1) / (CIRCLE_SEGMENTS / 2f);
                float y0 = cap * (halfHeight + MathUtils.sin(a0) * radius);
                float y1 = cap * (halfHeight + MathUtils.sin(a1) * radius);
                sr.line(point(MathUtils.cos(a0) * radius, y0, 0f), point(MathUtils.cos(a1) * radius, y1, 0f));
                sr.line(point(0f, y0, MathUtils.cos(a0) * radius), point(0f, y1, MathUtils.cos(a1) * radius));
            }
        }
    }

    private enum Axis { XY, XZ, YZ }

    /** Circle of {@code radius} in a body-space plane, shifted by {@code height} along the third axis. */
    private void drawCircle(ShapeRenderer sr, float radius, float height, Axis plane) {
        Vector3 previous = null;
        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            float angle = MathUtils.PI2 * i / CIRCLE_SEGMENTS;
            float a = MathUtils.cos(angle) * radius;
            float b = MathUtils.sin(angle) * radius;
            Vector3 current = switch (plane) {
                case XY -> point(a, b, height);
                case XZ -> point(a, height, b);
                case YZ -> point(height, a, b);
            };
            if (previous != null) {
                sr.line(previous, current);
            }
            previous = current;
        }
    }

    private Vector3 point(float x, float y, float z) {
        return new Vector3(x, y, z).mul(bodyMatrix);
    }
}
