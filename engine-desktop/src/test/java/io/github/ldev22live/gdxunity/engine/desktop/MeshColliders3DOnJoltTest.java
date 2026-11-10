package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.physics.JoltPhysicsBackend3D;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ConvexHullCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshGeometry3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ShapeDescriptor3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mesh and convex hull colliders (TD-052) against the real Jolt physics: a ball that rolls into a bowl and stays, a box
 * resting on the hull of a cube, characters walking on a mesh floor and ramp, rays hitting a mesh, and the refusals.
 */
class MeshColliders3DOnJoltTest {
    private static final float FRAME = 1f / 60f;

    private Scene scene;
    private PhysicsSystem3D physics;
    private final Map<String, MeshGeometry3D> meshes = new HashMap<>();

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Mesh colliders on Jolt");
        // The scene reads each entity's mesh by its name here; the renderer module's reader is tested separately below
        scene.setColliderGeometry(entity -> {
            MeshGeometry3D mesh = meshes.get(entity.getName());
            if (mesh == null) {
                throw new IllegalStateException(entity.getName() + " shows no mesh");
            }
            return mesh;
        });
        physics = new PhysicsSystem3D(scene, new JoltPhysicsBackend3D());
    }

    @AfterEach
    void tearDown() {
        physics.dispose();
        scene.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            physics.update(FRAME);
            scene.update(FRAME);
        }
    }

    private Entity staticMesh(String name, Vector3 position, MeshGeometry3D geometry) {
        meshes.put(name, geometry);
        Entity entity = scene.createEntity(name);
        entity.getTransform().setLocalPosition(position);
        entity.addComponent(new MeshCollider3DComponent());
        return entity;
    }

    /** A bowl 800 wide: the surface is y = 0.0015 * (x squared + z squared), 240 high at the rim. */
    private static MeshGeometry3D bowl() {
        int cells = 16;
        float size = 800f;
        float[] positions = new float[(cells + 1) * (cells + 1) * 3];
        int at = 0;
        for (int j = 0; j <= cells; j++) {
            for (int i = 0; i <= cells; i++) {
                float x = -size / 2f + size * i / cells;
                float z = -size / 2f + size * j / cells;
                positions[at++] = x;
                positions[at++] = 0.0015f * (x * x + z * z);
                positions[at++] = z;
            }
        }
        int[] indices = new int[cells * cells * 6];
        at = 0;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int a = j * (cells + 1) + i;
                int b = a + 1;
                int c = a + cells + 1;
                int d = c + 1;
                // Counter-clockwise seen from above, so every triangle faces up
                indices[at++] = a;
                indices[at++] = c;
                indices[at++] = b;
                indices[at++] = b;
                indices[at++] = c;
                indices[at++] = d;
            }
        }
        return new MeshGeometry3D(positions, indices);
    }

    /** A slope rising from y 0 at x 300 to y 200 at x 700, 400 wide. */
    private static MeshGeometry3D ramp() {
        float[] positions = {300f, 0f, -200f, 700f, 200f, -200f, 700f, 200f, 200f, 300f, 0f, 200f};
        return new MeshGeometry3D(positions, new int[] {0, 2, 1, 0, 3, 2});
    }

    private static MeshGeometry3D flatFloor(float half) {
        return new MeshGeometry3D(new float[] {-half, 0f, -half, half, 0f, -half, half, 0f, half, -half, 0f, half}, new int[] {0, 2, 1, 0, 3, 2});
    }

    @Test
    void shouldLetABallRollIntoABowlMeshAndStayThere() {
        staticMesh("Bowl", new Vector3(0f, 0f, 0f), bowl());
        Entity ball = scene.createEntity("Ball");
        ball.getTransform().setLocalPosition(150f, 220f, 40f);
        ball.addComponent(new SphereCollider3DComponent(20f));
        RigidBody3DComponent body = new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC);
        body.setLinearDamping(0.6f);
        body.setAngularDamping(0.3f);
        body.setFriction(0.5f);
        ball.addComponent(body);

        run(60 * 12);

        Vector3 rest = ball.getTransform().getWorldPosition();
        assertTrue(Math.abs(rest.x) < 60f && Math.abs(rest.z) < 60f, "the ball is near the bottom of the bowl: " + rest);
        assertTrue(rest.y > 10f && rest.y < 50f, "the ball sits on the bowl surface (radius 20), not through or above it: " + rest);
        run(30);
        assertTrue(ball.getTransform().getWorldPosition().dst(rest) < 3f, "and stays there");
    }

    @Test
    void shouldRestABoxOnTheConvexHullOfACube() {
        Entity floor = scene.createEntity("Floor");
        floor.getTransform().setLocalPosition(0f, -50f, 0f);
        floor.addComponent(new BoxCollider3DComponent(new Vector3(4000f, 100f, 4000f)));

        Entity cube = scene.createEntity("Hull Cube");
        cube.getTransform().setLocalPosition(0f, 300f, 0f);
        cube.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 100f, 100f, com.badlogic.gdx.graphics.Color.WHITE));
        cube.addComponent(new ConvexHullCollider3DComponent());
        cube.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
        scene.setColliderGeometry(new ColliderGeometry(null));

        run(60 * 3);

        // Jolt lets resting bodies sink by its penetration slop (2 units here), so the center is 50 minus up to about 2
        assertEquals(50f, cube.getTransform().getWorldPosition().y, 3f, "the cube rests with its bottom on the floor (center 50 above it)");
        assertEquals(0f, cube.getTransform().getWorldPosition().x, 2f);
    }

    @Test
    void shouldRefuseAMeshColliderOnADynamicBodyAndNameTheEntity() {
        Entity crate = staticMesh("Crate", new Vector3(0f, 200f, 0f), flatFloor(50f));
        crate.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));

        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));

        assertTrue(refusal.getMessage().contains("Crate"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("static") && refusal.getMessage().contains("Convex Hull"), refusal.getMessage());
    }

    @Test
    void shouldRefuseAMeshColliderWhenTheSceneCannotReadMeshes() {
        scene.setColliderGeometry(null);
        Entity terrain = scene.createEntity("Terrain");
        terrain.addComponent(new MeshCollider3DComponent());

        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));

        assertTrue(refusal.getMessage().contains("Terrain"), refusal.getMessage());
    }

    @Test
    void shouldRefuseAnEntityWithNoMeshToBuildTheColliderFrom() {
        scene.setColliderGeometry(new ColliderGeometry(null));
        Entity bare = scene.createEntity("Bare");
        bare.addComponent(new ConvexHullCollider3DComponent());

        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));

        assertTrue(refusal.getMessage().contains("Bare") && refusal.getMessage().contains("Mesh"), refusal.getMessage());
    }

    @Test
    void shouldWalkACharacterAcrossAMeshFloorAndUpAMeshRamp() {
        staticMesh("Floor", new Vector3(0f, 0f, 0f), flatFloor(1500f));
        staticMesh("Ramp", new Vector3(0f, 0f, 0f), ramp());
        Entity hero = scene.createEntity("Hero");
        hero.getTransform().setLocalPosition(0f, 120f, 0f);
        hero.addComponent(new CharacterController3DComponent());

        run(60);
        assertTrue(physics.isCharacterGrounded(hero), "the character stands on the mesh floor");
        float groundY = hero.getTransform().getWorldPosition().y;
        assertTrue(groundY > 20f && groundY < 80f, "and does not fall through it: " + groundY);

        for (int i = 0; i < 60 * 5; i++) {
            physics.moveCharacter(hero, new Vector3(150f, 0f, 0f));
            run(1);
        }

        Vector3 top = hero.getTransform().getWorldPosition();
        assertTrue(top.x > 450f, "walked past the foot of the ramp: " + top);
        assertTrue(top.y > groundY + 40f, "and climbed it: from " + groundY + " to " + top.y);
        assertTrue(physics.isCharacterGrounded(hero), "and is standing on it");
    }

    @Test
    void shouldHitAMeshAtTheRightPointWithTheSlopesNormal() {
        Entity slope = staticMesh("Slope", new Vector3(0f, 0f, 0f), ramp());

        Optional<PhysicsSystem3D.RaycastHit> hit = Optional.empty();
        for (int i = 0; i < 2 && hit.isEmpty(); i++) {
            run(1);
            hit = physics.raycast(new Vector3(500f, 500f, 0f), new Vector3(0f, -1f, 0f), 1000f, PhysicsLayers3D.ALL_LAYERS);
        }

        assertTrue(hit.isPresent(), "the ray hits the slope");
        assertEquals(slope, hit.get().entity());
        assertEquals(100f, hit.get().point().y, 1f, "halfway up the slope at x 500");
        assertEquals(500f, hit.get().point().x, 1f);
        Vector3 normal = hit.get().normal();
        assertEquals(-0.447f, normal.x, 0.02f, "the normal leans back, away from the rise: " + normal);
        assertEquals(0.894f, normal.y, 0.02f, "and mostly up: " + normal);
        assertEquals(0f, normal.z, 0.02f);
    }

    @Test
    void shouldShiftAMeshColliderByItsOffsetAndScaleItWithTheEntity() {
        Entity floor = staticMesh("Scaled Floor", new Vector3(0f, 0f, 0f), flatFloor(100f));
        floor.getTransform().setLocalScale(4f, 1f, 4f);
        floor.getComponent(MeshCollider3DComponent.class).setOffset(new Vector3(0f, 50f, 0f));
        run(1);

        Optional<PhysicsSystem3D.RaycastHit> inside = physics.raycast(new Vector3(350f, 500f, 0f), new Vector3(0f, -1f, 0f), 1000f,
            PhysicsLayers3D.ALL_LAYERS);
        Optional<PhysicsSystem3D.RaycastHit> outside = physics.raycast(new Vector3(450f, 500f, 0f), new Vector3(0f, -1f, 0f), 1000f,
            PhysicsLayers3D.ALL_LAYERS);

        assertTrue(inside.isPresent(), "the mesh is 100 wide times scale 4, so 350 is on it");
        assertEquals(50f, inside.get().point().y, 1f, "and the offset lifts it by 50");
        assertFalse(outside.isPresent(), "while 450 is past its scaled edge at 400");
    }

    @Test
    void shouldReduceTheHullOfARoundMeshToAtMostSixtyFourPoints() {
        Entity ball = scene.createEntity("Round");
        ball.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.SPHERE, 200f, 200f, 200f, com.badlogic.gdx.graphics.Color.WHITE));
        MeshGeometry3D geometry = new ColliderGeometry(null).geometryOf(ball);
        assertTrue(geometry.vertexCount() > PhysicsSystem3D.MAX_HULL_POINTS, "the sphere mesh has more points than a hull keeps");

        ShapeDescriptor3D hull = PhysicsSystem3D.describe(new ConvexHullCollider3DComponent(), new Vector3(1f, 1f, 1f), geometry);

        assertEquals(ShapeDescriptor3D.Kind.HULL, hull.kind());
        assertTrue(hull.points().length / 3 <= PhysicsSystem3D.MAX_HULL_POINTS, "points: " + hull.points().length / 3);
        float far = 0f;
        float[] points = hull.points();
        for (int i = 0; i < points.length; i += 3) {
            far = Math.max(far, new Vector3(points[i], points[i + 1], points[i + 2]).len());
        }
        assertEquals(1f, far, 0.01f, "the points still reach the sphere's radius of 1 meter");
    }

    @Test
    void shouldFaceEveryTriangleOfAPrimitiveOutwardsOrUp() {
        MeshPrimitiveComponent box = new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 60f, 40f, com.badlogic.gdx.graphics.Color.WHITE);
        MeshPrimitiveComponent sphere = new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.SPHERE, 100f, 100f, 100f, com.badlogic.gdx.graphics.Color.WHITE);
        MeshPrimitiveComponent plane = new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.PLANE, 100f, 100f, 100f, com.badlogic.gdx.graphics.Color.WHITE);
        for (MeshPrimitiveComponent primitive : new MeshPrimitiveComponent[] {box, sphere, plane}) {
            Entity entity = scene.createEntity("P " + primitive.getShape());
            entity.addComponent(primitive);
            MeshGeometry3D geometry = new ColliderGeometry(null).geometryOf(entity);
            float[] p = geometry.positions();
            int[] ix = geometry.indices();
            for (int t = 0; t < ix.length; t += 3) {
                Vector3 a = new Vector3(p[ix[t] * 3], p[ix[t] * 3 + 1], p[ix[t] * 3 + 2]);
                Vector3 b = new Vector3(p[ix[t + 1] * 3], p[ix[t + 1] * 3 + 1], p[ix[t + 1] * 3 + 2]);
                Vector3 c = new Vector3(p[ix[t + 2] * 3], p[ix[t + 2] * 3 + 1], p[ix[t + 2] * 3 + 2]);
                Vector3 normal = new Vector3(b).sub(a).crs(new Vector3(c).sub(a));
                if (normal.len2() < 1e-6f) {
                    continue; // a sliver at a sphere pole has no direction
                }
                Vector3 centroid = new Vector3(a).add(b).add(c).scl(1f / 3f);
                float facing = primitive.getShape() == MeshPrimitiveComponent.Shape.PLANE ? normal.y : normal.dot(centroid);
                assertTrue(facing > 0f, primitive.getShape() + " triangle " + t / 3 + " faces inwards");
            }
        }
    }
}
