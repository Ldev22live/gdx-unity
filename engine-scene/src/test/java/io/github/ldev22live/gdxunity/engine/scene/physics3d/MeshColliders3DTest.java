package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.ConvexHullCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.MeshCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The data side of mesh and convex hull colliders (TD-052): geometry validation, point reduction, scaling and saving. */
class MeshColliders3DTest {

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static MeshGeometry3D triangle() {
        return new MeshGeometry3D(new float[] {0f, 0f, 0f, 100f, 0f, 0f, 0f, 0f, 100f}, new int[] {0, 2, 1});
    }

    @Test
    void shouldRefuseGeometryThatIsNotWholeVerticesAndTriangles() {
        assertThrows(IllegalArgumentException.class, () -> new MeshGeometry3D(new float[] {0f, 0f}, new int[0]));
        assertThrows(IllegalArgumentException.class, () -> new MeshGeometry3D(new float[] {0f, 0f, 0f}, new int[] {0, 0}));
        assertThrows(IllegalArgumentException.class, () -> new MeshGeometry3D(new float[] {0f, 0f, 0f}, new int[] {0, 0, 1}),
            "index 1 is past the only vertex");
        MeshGeometry3D geometry = triangle();
        assertEquals(3, geometry.vertexCount());
        assertEquals(1, geometry.triangleCount());
    }

    @Test
    void shouldKeepGeometryIndependentOfTheArraysItWasBuiltFrom() {
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
        MeshGeometry3D geometry = new MeshGeometry3D(positions, new int[] {0, 1, 2});
        positions[3] = 99f;
        geometry.positions()[3] = 77f;

        assertEquals(1f, geometry.positions()[3], "neither the source array nor a returned copy changes the geometry");
    }

    @Test
    void shouldScaleAMeshIntoMetersWithTheEntityScale() {
        ShapeDescriptor3D shape = PhysicsSystem3D.describe(new MeshCollider3DComponent(), new Vector3(2f, 1f, 3f), triangle());

        assertEquals(ShapeDescriptor3D.Kind.MESH, shape.kind());
        // 100 units * scale 2 = 200 units = 2 meters; the z vertex 100 * 3 = 3 meters
        assertEquals(2f, shape.points()[3], 1e-5f);
        assertEquals(3f, shape.points()[8], 1e-5f);
        assertEquals(3, shape.indices().length);
    }

    @Test
    void shouldNeedTheMeshToDescribeAMeshOrHullCollider() {
        assertThrows(IllegalArgumentException.class, () -> PhysicsSystem3D.describe(new MeshCollider3DComponent(), new Vector3(1f, 1f, 1f)));
        assertThrows(IllegalArgumentException.class,
            () -> PhysicsSystem3D.describe(new ConvexHullCollider3DComponent(), new Vector3(1f, 1f, 1f)));
    }

    @Test
    void shouldMergeDuplicatePointsAndKeepTheOutermostWhenThereAreTooMany() {
        float[] duplicated = {0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f};
        assertEquals(6, ColliderPoints3D.reduce(duplicated, 64).length, "two distinct points remain");

        int count = 1000;
        float[] ring = new float[count * 3];
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count;
            ring[i * 3] = (float) Math.cos(angle);
            ring[i * 3 + 2] = (float) Math.sin(angle);
        }
        float[] reduced = ColliderPoints3D.reduce(ring, 16);

        assertEquals(16 * 3, reduced.length);
        for (int i = 0; i < reduced.length; i += 3) {
            assertEquals(1f, new Vector3(reduced[i], reduced[i + 1], reduced[i + 2]).len(), 1e-3f, "every kept point is on the ring");
        }
    }

    @Test
    void shouldReportAMeshWithTooManyTrianglesAsAWarning() {
        int triangles = PhysicsSystem3D.LARGE_MESH_TRIANGLES + 1;
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
        int[] indices = new int[triangles * 3];
        for (int t = 0; t < triangles; t++) {
            indices[t * 3] = 0;
            indices[t * 3 + 1] = 2;
            indices[t * 3 + 2] = 1;
        }
        MeshGeometry3D big = new MeshGeometry3D(positions, indices);
        Scene scene = new Scene("Big");
        scene.setColliderGeometry(entity -> big);
        Entity terrain = scene.createEntity("Terrain");
        terrain.addComponent(new MeshCollider3DComponent());
        PhysicsSystem3D physics = new PhysicsSystem3D(scene, new PhysicsSystem3DTest.FakeBackend());
        try {
            physics.update(1f / 60f);
            assertEquals(1, physics.getWarnings().size());
            assertTrue(physics.getWarnings().get(0).contains("Terrain"), physics.getWarnings().get(0));
        } finally {
            physics.dispose();
            scene.dispose();
        }
    }

    @Test
    void shouldRoundTripBothColliders() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new ConvexHullCollider3DComponentSerializer());
        registry.register(new MeshCollider3DComponentSerializer());
        SceneSerializer serializer = new SceneSerializer(registry);
        Scene scene = new Scene("Meshes");
        Entity entity = scene.createEntity("Rock");
        ConvexHullCollider3DComponent hull = new ConvexHullCollider3DComponent();
        hull.setOffset(new Vector3(1f, 2f, 3f));
        hull.setTrigger(true);
        entity.addComponent(hull);
        MeshCollider3DComponent mesh = new MeshCollider3DComponent();
        mesh.setEnabled(false);
        entity.addComponent(mesh);

        Entity read = serializer.fromJson(serializer.toJson(scene)).findEntityById(entity.getId());

        ConvexHullCollider3DComponent readHull = read.getComponent(ConvexHullCollider3DComponent.class);
        assertNotNull(readHull);
        assertEquals(new Vector3(1f, 2f, 3f), readHull.getOffset());
        assertTrue(readHull.isTrigger());
        MeshCollider3DComponent readMesh = read.getComponent(MeshCollider3DComponent.class);
        assertNotNull(readMesh);
        assertFalse(readMesh.isEnabled());
        assertFalse(readMesh.isTrigger());
    }
}
