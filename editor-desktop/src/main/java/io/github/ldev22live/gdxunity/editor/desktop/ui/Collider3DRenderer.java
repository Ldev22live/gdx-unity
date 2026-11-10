package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ColliderGeometrySource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshGeometry3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ConvexHullCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsUnits3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ShapeDescriptor3D;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Wireframes of 3D colliders in the 3D view: always for the selection, for every entity while the physics debug
 * overlay is on. Shapes come from {@link PhysicsSystem3D#describe}, so what is drawn is what the simulation
 * uses (including how scale applies to spheres and capsules). Solid colliders are green, triggers cyan. A Character
 * Controller 3D is drawn as its upright capsule in orange: it ignores the entity's rotation and scale (TD-049).
 */
public class Collider3DRenderer {
    private static final Color SOLID_COLOR = new Color(0.35f, 0.9f, 0.4f, 1f);
    private static final Color TRIGGER_COLOR = new Color(0.3f, 0.85f, 0.95f, 1f);
    private static final Color CHARACTER_COLOR = new Color(1f, 0.65f, 0.2f, 1f);
    private static final Color RIG_COLOR = new Color(0.9f, 0.8f, 0.3f, 1f);
    private static final Color JOINT_COLOR = new Color(0.95f, 0.45f, 0.85f, 1f);
    /** Length of the drawn joint axis and the radius of the hinge's limit arc, in scene units. */
    private static final float JOINT_GIZMO_SIZE = 60f;
    /** Half the edge of the cross drawn at a joint's anchor. */
    private static final float ANCHOR_CROSS = 8f;
    /** Line segments of a hinge's limit arc. */
    private static final int ARC_SEGMENTS = 16;
    /** Line segments per full circle. */
    private static final int CIRCLE_SEGMENTS = 32;
    /** A collider mesh with more triangles than this is drawn with every n-th triangle only, to keep the view responsive. */
    private static final int MAX_DRAWN_TRIANGLES = 3000;
    /** Half the edge of the cross drawn at each point of a convex hull. */
    private static final float HULL_POINT_CROSS = 3f;

    private final EditorState editorState;
    private final Camera camera;
    private final BooleanSupplier showAll;
    private final Matrix4 identity = new Matrix4();
    private final Matrix4 bodyMatrix = new Matrix4();
    /** The mesh read for each mesh or hull collider, kept while the entity shows the same model or primitive. */
    private final Map<UUID, CachedMesh> meshes = new HashMap<>();

    /** @param signature what the mesh was read from; @param geometry the mesh, or null if it could not be read */
    private record CachedMesh(String signature, MeshGeometry3D geometry) {}

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
                    drawCollider(shapeRenderer, scene, entity, collider);
                }
            }
            CharacterController3DComponent character = entity.getComponent(CharacterController3DComponent.class);
            if (character != null && character.isEnabled()) {
                drawCharacter(shapeRenderer, entity.getTransform(), character);
            }
            CameraRigComponent rig = entity.getComponent(CameraRigComponent.class);
            if (rig != null && rig.isEnabled()) {
                drawRigLink(shapeRenderer, scene, entity, rig);
            }
            Joint3DComponent joint = entity.getComponent(Joint3DComponent.class);
            if (joint != null && joint.isEnabled()) {
                drawJoint(shapeRenderer, scene, entity, joint);
            }
        }
        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(previousTransform);
    }

    private void drawCollider(ShapeRenderer sr, Scene scene, Entity entity, Collider3DComponent collider) {
        Transform transform = entity.getTransform();
        MeshGeometry3D geometry = null;
        if (collider instanceof ConvexHullCollider3DComponent || collider instanceof MeshCollider3DComponent) {
            geometry = meshOf(scene, entity);
            if (geometry == null) {
                return;
            }
        }
        ShapeDescriptor3D shape = PhysicsSystem3D.describe(collider, transform.getWorldScale(), geometry);
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
            case HULL -> drawHullPoints(sr, shape.points(), u);
            case MESH -> drawTriangles(sr, shape.points(), shape.indices(), u);
        }
    }

    /**
     * The mesh a mesh or hull collider is built from, read once per model or primitive and kept; null when the entity shows none
     * or the scene cannot read it (such an entity draws nothing, and play mode reports why).
     */
    private MeshGeometry3D meshOf(Scene scene, Entity entity) {
        ColliderGeometrySource source = scene.getColliderGeometry();
        String signature = signatureOf(entity);
        if (source == null || signature == null) {
            return null;
        }
        CachedMesh cached = meshes.get(entity.getId());
        if (cached == null || !cached.signature().equals(signature)) {
            MeshGeometry3D geometry;
            try {
                geometry = source.geometryOf(entity);
            } catch (IllegalStateException e) {
                // Shown by Play as the refusal with the entity's name; the editor just has nothing to draw
                geometry = null;
            }
            cached = new CachedMesh(signature, geometry);
            meshes.put(entity.getId(), cached);
        }
        return cached.geometry();
    }

    private static String signatureOf(Entity entity) {
        ModelComponent model = entity.getComponent(ModelComponent.class);
        if (model != null && model.isEnabled()) {
            return "model:" + model.getModelPath() + ":" + model.getImportScale();
        }
        MeshPrimitiveComponent primitive = entity.getComponent(MeshPrimitiveComponent.class);
        if (primitive != null && primitive.isEnabled()) {
            return "primitive:" + primitive.getShape() + ":" + primitive.getSize();
        }
        return null;
    }

    /** A small cross at each point the hull is built around. */
    private void drawHullPoints(ShapeRenderer sr, float[] points, float unitsPerMeter) {
        for (int i = 0; i < points.length; i += 3) {
            float x = points[i] * unitsPerMeter;
            float y = points[i + 1] * unitsPerMeter;
            float z = points[i + 2] * unitsPerMeter;
            sr.line(point(x - HULL_POINT_CROSS, y, z), point(x + HULL_POINT_CROSS, y, z));
            sr.line(point(x, y - HULL_POINT_CROSS, z), point(x, y + HULL_POINT_CROSS, z));
            sr.line(point(x, y, z - HULL_POINT_CROSS), point(x, y, z + HULL_POINT_CROSS));
        }
    }

    /** The edges of the mesh's triangles; every n-th triangle when there are very many. */
    private void drawTriangles(ShapeRenderer sr, float[] points, int[] indices, float unitsPerMeter) {
        int triangles = indices.length / 3;
        int step = Math.max(1, (triangles + MAX_DRAWN_TRIANGLES - 1) / MAX_DRAWN_TRIANGLES);
        for (int t = 0; t < triangles; t += step) {
            Vector3 a = vertex(points, indices[t * 3], unitsPerMeter);
            Vector3 b = vertex(points, indices[t * 3 + 1], unitsPerMeter);
            Vector3 c = vertex(points, indices[t * 3 + 2], unitsPerMeter);
            sr.line(a, b);
            sr.line(b, c);
            sr.line(c, a);
        }
    }

    private Vector3 vertex(float[] points, int index, float unitsPerMeter) {
        return point(points[index * 3] * unitsPerMeter, points[index * 3 + 1] * unitsPerMeter, points[index * 3 + 2] * unitsPerMeter);
    }

    /** A line from a Camera Rig's entity to the point it looks at (target plus look-at height), when the target exists (TD-050). */
    private void drawRigLink(ShapeRenderer sr, Scene scene, Entity entity, CameraRigComponent rig) {
        Entity target = rig.getTargetName().isEmpty() ? null : scene.findEntityByName(rig.getTargetName());
        if (target == null) {
            return;
        }
        Vector3 from = entity.getTransform().getWorldPosition();
        Vector3 to = target.getTransform().getWorldPosition().add(0f, rig.getPivotHeight(), 0f);
        sr.setColor(RIG_COLOR);
        sr.line(from, to);
    }

    /**
     * A Joint 3D (TD-051): a cross at the anchor, the hinge or slider axis, the hinge's limit arc or the slider's travel, and a
     * line to the connected entity (or to the world point of a rope) - at the pose the entity has now, which is the rest pose the
     * joint will get when physics starts.
     */
    private void drawJoint(ShapeRenderer sr, Scene scene, Entity entity, Joint3DComponent joint) {
        Transform transform = entity.getTransform();
        Vector3 anchor = joint.getAnchor().mul(transform.getWorldMatrix());
        sr.setColor(JOINT_COLOR);
        sr.line(anchor.x - ANCHOR_CROSS, anchor.y, anchor.z, anchor.x + ANCHOR_CROSS, anchor.y, anchor.z);
        sr.line(anchor.x, anchor.y - ANCHOR_CROSS, anchor.z, anchor.x, anchor.y + ANCHOR_CROSS, anchor.z);
        sr.line(anchor.x, anchor.y, anchor.z - ANCHOR_CROSS, anchor.x, anchor.y, anchor.z + ANCHOR_CROSS);
        Entity connected = joint.getConnectedName().isEmpty() ? null : scene.findEntityByName(joint.getConnectedName());
        if (joint.getType() == Joint3DComponent.Type.DISTANCE) {
            Vector3 other = connected != null ? joint.getConnectedAnchor().mul(connected.getTransform().getWorldMatrix())
                : joint.getConnectedAnchor();
            sr.line(anchor, other);
        } else if (connected != null) {
            sr.line(anchor, connected.getTransform().getWorldPosition());
        }
        boolean hinge = joint.getType() == Joint3DComponent.Type.HINGE;
        if (!hinge && joint.getType() != Joint3DComponent.Type.SLIDER) {
            return;
        }
        Vector3 axis = transform.getWorldRotation().transform(joint.getAxis()).nor();
        sr.line(new Vector3(anchor).mulAdd(axis, -JOINT_GIZMO_SIZE), new Vector3(anchor).mulAdd(axis, JOINT_GIZMO_SIZE));
        if (!joint.isLimitsEnabled()) {
            return;
        }
        if (hinge) {
            drawHingeArc(sr, anchor, axis, joint.getLowerLimit(), joint.getUpperLimit());
        } else {
            // Thicker part of the line: the stretch the slider can travel
            Vector3 low = new Vector3(anchor).mulAdd(axis, joint.getLowerLimit());
            Vector3 high = new Vector3(anchor).mulAdd(axis, joint.getUpperLimit());
            sr.line(low, high);
            Vector3 side = new Vector3(axis).crs(Math.abs(axis.y) < 0.9f ? Vector3.Y : Vector3.X).nor().scl(ANCHOR_CROSS);
            sr.line(new Vector3(low).sub(side), new Vector3(low).add(side));
            sr.line(new Vector3(high).sub(side), new Vector3(high).add(side));
        }
    }

    /** The angles a hinge may reach, as an arc around its axis from the direction the entity starts in. */
    private void drawHingeArc(ShapeRenderer sr, Vector3 anchor, Vector3 axis, float lowerDegrees, float upperDegrees) {
        Vector3 reference = new Vector3(axis).crs(Math.abs(axis.x) < 0.9f ? Vector3.X : Vector3.Y).nor().scl(JOINT_GIZMO_SIZE);
        Vector3 previous = null;
        for (int i = 0; i <= ARC_SEGMENTS; i++) {
            float degrees = lowerDegrees + (upperDegrees - lowerDegrees) * i / ARC_SEGMENTS;
            Vector3 point = new Vector3(reference).rotate(axis, degrees).add(anchor);
            if (previous != null) {
                sr.line(previous, point);
            }
            previous = point;
        }
        Vector3 start = new Vector3(reference).add(anchor);
        sr.line(anchor, start);
    }

    private void drawCharacter(ShapeRenderer sr, Transform transform, CharacterController3DComponent character) {
        // Upright at the entity's position, the way the simulation places the capsule
        bodyMatrix.idt().translate(transform.getWorldPosition());
        sr.setColor(CHARACTER_COLOR);
        drawCapsule(sr, character.getRadius(), character.getHeight() * 0.5f);
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
