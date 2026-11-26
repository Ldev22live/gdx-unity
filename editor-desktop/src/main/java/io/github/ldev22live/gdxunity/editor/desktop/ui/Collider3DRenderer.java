package io.github.ldev22live.gdxunity.editor.desktop.ui;

import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainPathComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ColliderGeometrySource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshGeometry3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ConvexHullCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightField3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightFieldSource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsUnits3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ShapeDescriptor3D;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavMeshBaker;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavMeshBoundsComponent;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;

import java.util.HashMap;
import java.util.List;
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
    private static final Color SPAWN_COLOR = new Color(0.35f, 0.75f, 1f, 1f);
    /** The spawn marker's ring, arrow and post, in scene units: about a character's width and height. */
    private static final float SPAWN_RING_RADIUS = 45f;
    private static final float SPAWN_ARROW_LENGTH = 90f;
    private static final float SPAWN_ARROW_HEAD = 18f;
    private static final float SPAWN_POST_HEIGHT = 140f;
    /** The marker is drawn this far above the spawn point, so the ground does not hide it. */
    private static final float SPAWN_LIFT = 3f;
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
    private record CachedMesh(ColliderGeometrySource source, long revision, String signature, MeshGeometry3D geometry) {}

    /** Colours of a Terrain Path's curve by kind (roadmap M3.7). */
    private static final Color PATH_ROAD_COLOR = new Color(0.85f, 0.6f, 0.3f, 1f);
    private static final Color PATH_RIVER_COLOR = new Color(0.35f, 0.6f, 1f, 1f);
    private static final Color PATH_CLIFF_COLOR = new Color(0.75f, 0.75f, 0.75f, 1f);
    /** Half the size of the cross at a path's control point, in scene units. */
    private static final float PATH_POINT_CROSS = 14f;
    /** A path's curve is drawn this far above the ground, so the ground does not hide it. */
    private static final float PATH_LIFT = 4f;
    /** Each path's curve in world space, kept while its points and the ground stay the same. */
    private final Map<UUID, CachedPath> paths = new HashMap<>();

    /** A path's curve and control points as x, y, z triples, for the points and ground revision they were made from. */
    private record CachedPath(List<Vector2> points, long revision, float[] line, float[] marks) {}

    private record CachedLine(float[] line, float[] marks) {}

    /** Colours of a selected Nav Mesh Bounds: its box, and the outline of where agents can walk (roadmap M6.4). */
    private static final Color NAV_BOUNDS_COLOR = new Color(0.35f, 0.55f, 1f, 1f);
    private static final Color NAV_WALKABLE_COLOR = new Color(0.3f, 1f, 0.45f, 1f);
    /** The walkable outline is drawn this far above the floor, so the floor does not hide it. */
    private static final float NAV_LIFT = 3f;
    /** How often, at most, a selected Nav Mesh Bounds is baked again for drawing. */
    static final long NAV_REFRESH_MILLIS = 1000L;
    /** The last walkable outline drawn for each selected Nav Mesh Bounds, and when it was baked. */
    private final Map<UUID, CachedNavigation> navigation = new HashMap<>();

    private record CachedNavigation(long bakedAt, float[] lines) {}

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
        boolean selection = !editorState.getSelectedEntityIds().isEmpty();
        Matrix4 previousTransform = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(identity);
        shapeRenderer.setProjectionMatrix(camera.getCombinedMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Entity entity : scene.getAllEntities()) {
            // A spawn point has no shape of its own, so it is always marked, or it could not be found in the view (roadmap M3c.2)
            SpawnPointComponent spawn = entity.getComponent(SpawnPointComponent.class);
            if (spawn != null && spawn.isEnabled() && entity.isActiveInHierarchy()) {
                drawSpawnPoint(shapeRenderer, entity.getTransform());
            }
            // A Terrain Path is a curve on the ground with nothing drawn of its own until applied, so it is always marked (roadmap M3.7)
            TerrainPathComponent path = entity.getComponent(TerrainPathComponent.class);
            if (path != null && path.isEnabled()) {
                drawPath(shapeRenderer, scene, entity, path);
            }
            if (!all && (!selection || !editorState.isSelected(entity.getId()))) {
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
            NavMeshBoundsComponent navBounds = entity.getComponent(NavMeshBoundsComponent.class);
            if (navBounds != null && navBounds.isEnabled()) {
                drawNavigation(shapeRenderer, scene, entity, navBounds);
            }
        }
        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(previousTransform);
    }

    /**
     * A selected Nav Mesh Bounds (roadmap M6.4): its box in blue and the outline of the walkable area it bakes in green, a little above
     * the floor, so a person sees where agents can go and where obstacles cut it. Baked again at most every
     * {@link #NAV_REFRESH_MILLIS}, so moving an obstacle shows within a second without baking every frame.
     */
    private void drawNavigation(ShapeRenderer sr, Scene scene, Entity entity, NavMeshBoundsComponent bounds) {
        Matrix4 world = entity.getTransform().getWorldMatrix();
        Vector3 center = world.getTranslation(new Vector3());
        Vector3 scale = world.getScale(new Vector3());
        Vector3 half = bounds.getSize().scl(Math.abs(scale.x) / 2f, Math.abs(scale.y) / 2f, Math.abs(scale.z) / 2f);
        sr.setColor(NAV_BOUNDS_COLOR);
        sr.box(center.x - half.x, center.y - half.y, center.z + half.z, half.x * 2f, half.y * 2f, half.z * 2f);
        long now = System.currentTimeMillis();
        CachedNavigation cached = navigation.get(entity.getId());
        if (cached == null || now - cached.bakedAt() > NAV_REFRESH_MILLIS) {
            float[] lines;
            try {
                lines = NavMeshBaker.bake(scene, entity, bounds).grid().outline(NAV_LIFT);
            } catch (IllegalArgumentException e) {
                lines = new float[0]; // too many cells: the box alone is drawn, and Play reports why
            }
            cached = new CachedNavigation(now, lines);
            navigation.put(entity.getId(), cached);
        }
        sr.setColor(NAV_WALKABLE_COLOR);
        float[] lines = cached.lines();
        for (int i = 0; i + 5 < lines.length; i += 6) {
            sr.line(lines[i], lines[i + 1], lines[i + 2], lines[i + 3], lines[i + 4], lines[i + 5]);
        }
    }

    /**
     * The path's curve on the level ground (the first entity with a Terrain), and a cross at each control point: brown for a road,
     * blue for a river, grey for a cliff; brighter while selected. The curve is sampled again only when its points or the ground change.
     */
    private void drawPath(ShapeRenderer sr, Scene scene, Entity entity, TerrainPathComponent path) {
        Entity ground = null;
        for (Entity candidate : scene.getAllEntities()) {
            if (candidate.getComponent(TerrainComponent.class) != null) {
                ground = candidate;
                break;
            }
        }
        HeightFieldSource heights = scene.getHeightFields();
        if (ground == null || heights == null) {
            return;
        }
        long revision = heights.revisionOf(ground);
        List<Vector2> points = path.getPoints();
        CachedPath cached = paths.get(entity.getId());
        if (cached == null || cached.revision() != revision || !cached.points().equals(points)) {
            CachedLine vertices = pathVertices(heights, ground, points);
            cached = new CachedPath(points, revision, vertices.line(), vertices.marks());
            paths.put(entity.getId(), cached);
        }
        Color color = switch (path.getKind()) {
            case ROAD -> PATH_ROAD_COLOR;
            case RIVER -> PATH_RIVER_COLOR;
            case CLIFF -> PATH_CLIFF_COLOR;
        };
        sr.setColor(editorState.isSelected(entity.getId()) ? Color.WHITE : color);
        float[] line = cached.line();
        for (int i = 3; i < line.length; i += 3) {
            sr.line(line[i - 3], line[i - 2], line[i - 1], line[i], line[i + 1], line[i + 2]);
        }
        sr.setColor(color);
        float[] marks = cached.marks();
        for (int i = 0; i < marks.length; i += 3) {
            sr.line(marks[i] - PATH_POINT_CROSS, marks[i + 1], marks[i + 2], marks[i] + PATH_POINT_CROSS, marks[i + 1], marks[i + 2]);
            sr.line(marks[i], marks[i + 1], marks[i + 2] - PATH_POINT_CROSS, marks[i], marks[i + 1], marks[i + 2] + PATH_POINT_CROSS);
            sr.line(marks[i], marks[i + 1], marks[i + 2], marks[i], marks[i + 1] + PATH_POINT_CROSS * 2f, marks[i + 2]);
        }
    }

    /** The curve and the control points in world space, lifted a little above the ground, as x, y, z triples. */
    private static CachedLine pathVertices(HeightFieldSource heights, Entity ground, List<Vector2> points) {
        HeightField3D field;
        try {
            field = heights.heightFieldOf(ground);
        } catch (IllegalStateException e) {
            return new CachedLine(new float[0], new float[0]); // the ground's terrain cannot be read: nothing to draw on
        }
        Matrix4 world = ground.getTransform().getWorldMatrix();
        float[] marks = new float[points.size() * 3];
        Vector3 v = new Vector3();
        for (int i = 0; i < points.size(); i++) {
            v.set(points.get(i).x, groundHeight(field, points.get(i).x, points.get(i).y) + PATH_LIFT, points.get(i).y).mul(world);
            marks[i * 3] = v.x;
            marks[i * 3 + 1] = v.y;
            marks[i * 3 + 2] = v.z;
        }
        if (points.size() < 2) {
            return new CachedLine(new float[0], marks);
        }
        float[] xs = new float[points.size()];
        float[] zs = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i).x;
            zs[i] = points.get(i).y;
        }
        TerrainPath.Samples samples = TerrainPath.sample(xs, zs, field.cellSizeX());
        float[] line = new float[samples.size() * 3];
        for (int i = 0; i < samples.size(); i++) {
            v.set(samples.x()[i], groundHeight(field, samples.x()[i], samples.z()[i]) + PATH_LIFT, samples.z()[i]).mul(world);
            line[i * 3] = v.x;
            line[i * 3 + 1] = v.y;
            line[i * 3 + 2] = v.z;
        }
        return new CachedLine(line, marks);
    }

    /** The height field's height at the grid point nearest (x, z); 0 off the field or in a hole. */
    private static float groundHeight(HeightField3D field, float x, float z) {
        int column = Math.round((x - field.originX()) / field.cellSizeX());
        int row = Math.round((z - field.originZ()) / field.cellSizeZ());
        if (column < 0 || column >= field.columns() || row < 0 || row >= field.rows()) {
            return 0f;
        }
        float h = field.heightAt(column, row);
        return HeightField3D.isHole(h) ? 0f : h;
    }

    private void drawCollider(ShapeRenderer sr, Scene scene, Entity entity, Collider3DComponent collider) {
        if (collider instanceof TerrainCollider3DComponent) {
            // The terrain that is drawn is the ground that collides; an outline over every cell would only hide it
            return;
        }
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
        long revision = source instanceof ColliderGeometry models ? models.revision() : 0;
        CachedMesh cached = meshes.get(entity.getId());
        if (cached == null || cached.source() != source || cached.revision() != revision || !cached.signature().equals(signature)) {
            MeshGeometry3D geometry;
            try {
                geometry = source.geometryOf(entity);
            } catch (IllegalStateException e) {
                // Shown by Play as the refusal with the entity's name; the editor just has nothing to draw
                geometry = null;
            }
            cached = new CachedMesh(source, revision, signature, geometry);
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

    /**
     * A spawn point: a ring on the ground and an arrow the way the player will face (yaw only, as the player is turned), drawn in the
     * editor only; the game shows nothing there.
     */
    private void drawSpawnPoint(ShapeRenderer sr, Transform transform) {
        Vector3 facing = transform.getWorldRotation().transform(new Vector3(0f, 0f, -1f));
        float yaw = facing.x * facing.x + facing.z * facing.z > 1e-6f ? CameraRigComponent.yawOf(facing) : 0f;
        bodyMatrix.idt().translate(transform.getWorldPosition()).rotate(Vector3.Y, yaw);
        sr.setColor(SPAWN_COLOR);
        drawCircle(sr, SPAWN_RING_RADIUS, SPAWN_LIFT, Axis.XZ);
        drawCircle(sr, SPAWN_RING_RADIUS * 0.6f, SPAWN_LIFT, Axis.XZ);
        Vector3 tip = point(0f, SPAWN_LIFT, -SPAWN_ARROW_LENGTH);
        sr.line(point(0f, SPAWN_LIFT, 0f), tip);
        sr.line(tip, point(-SPAWN_ARROW_HEAD, SPAWN_LIFT, -SPAWN_ARROW_LENGTH + SPAWN_ARROW_HEAD));
        sr.line(tip, point(SPAWN_ARROW_HEAD, SPAWN_LIFT, -SPAWN_ARROW_LENGTH + SPAWN_ARROW_HEAD));
        // An upright post, so the spawn shows above grass and from far away
        sr.line(point(0f, 0f, 0f), point(0f, SPAWN_POST_HEIGHT, 0f));
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
