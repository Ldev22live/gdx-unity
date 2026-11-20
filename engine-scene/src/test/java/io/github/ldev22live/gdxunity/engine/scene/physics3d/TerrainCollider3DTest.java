package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.TerrainCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data side of terrain collision (roadmap M3.3, TD-087): the height field, how a Terrain Collider 3D becomes a body in meters,
 * the combinations that are refused and why, rebuilding when the terrain changes, and saving. Jolt itself is tested in
 * {@code engine-physics}.
 */
class TerrainCollider3DTest {
    private Scene scene;
    private PhysicsSystem3DTest.FakeBackend backend;
    private PhysicsSystem3D physics;
    private HeightField3D field;
    private long revision;
    private int reads;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Terrain");
        field = flat(3, 2, 0f);
        scene.setHeightFields(new HeightFieldSource() {
            @Override
            public HeightField3D heightFieldOf(Entity entity) {
                reads++;
                return field;
            }

            @Override
            public long revisionOf(Entity entity) {
                return revision;
            }
        });
        backend = new PhysicsSystem3DTest.FakeBackend();
        physics = new PhysicsSystem3D(scene, backend);
    }

    @AfterEach
    void tearDown() {
        physics.dispose();
        scene.dispose();
    }

    private static HeightField3D flat(int columns, int rows, float height) {
        float[] heights = new float[columns * rows];
        Arrays.fill(heights, height);
        return new HeightField3D(columns, rows, 100f, 100f, -(columns - 1) * 50f, -(rows - 1) * 50f, heights);
    }

    private Entity ground() {
        Entity ground = scene.createEntity("Ground");
        ground.addComponent(new TerrainCollider3DComponent());
        return ground;
    }

    private ShapeDescriptor3D onlyShape() {
        assertEquals(1, backend.bodies.size(), "one body");
        BodyDescriptor3D body = backend.bodies.values().iterator().next();
        assertEquals(1, body.shapes().size());
        return body.shapes().get(0);
    }

    @Test
    void shouldRefuseAHeightFieldWithTheWrongSizeOrValues() {
        assertThrows(IllegalArgumentException.class, () -> new HeightField3D(1, 5, 1f, 1f, 0f, 0f, new float[5]), "too few columns");
        assertThrows(IllegalArgumentException.class, () -> new HeightField3D(2, 2, 1f, 1f, 0f, 0f, new float[3]), "wrong count");
        assertThrows(IllegalArgumentException.class, () -> new HeightField3D(2, 2, 0f, 1f, 0f, 0f, new float[4]), "no cell size");
        assertThrows(IllegalArgumentException.class,
            () -> new HeightField3D(2, 2, 1f, 1f, 0f, 0f, new float[] {0f, Float.POSITIVE_INFINITY, 0f, 0f}), "infinite height");
        float[] heights = {0f, 1f, HeightField3D.NO_COLLISION, 3f};
        HeightField3D withHole = new HeightField3D(2, 2, 1f, 1f, 0f, 0f, heights);
        heights[1] = 99f;
        assertEquals(1f, withHole.heightAt(1, 0), "keeps its own copy");
        assertTrue(HeightField3D.isHole(withHole.heightAt(0, 1)));
    }

    @Test
    void shouldBuildAStaticHeightFieldInMetersWithTheEntityScale() {
        field = new HeightField3D(2, 2, 100f, 50f, -50f, -25f, new float[] {0f, 10f, HeightField3D.NO_COLLISION, 30f});
        Entity ground = ground();
        ground.getTransform().setLocalPosition(200f, 0f, 0f);
        ground.getTransform().setLocalScale(2f, 3f, 1f);

        physics.update(1f / 60f);

        ShapeDescriptor3D shape = onlyShape();
        assertEquals(ShapeDescriptor3D.Kind.HEIGHT_FIELD, shape.kind());
        HeightField3D meters = shape.heightField();
        assertEquals(2f, meters.cellSizeX(), 1e-5f, "100 units * scale 2 = 2 m");
        assertEquals(0.5f, meters.cellSizeZ(), 1e-5f);
        assertEquals(-1f, meters.originX(), 1e-5f);
        assertEquals(0.3f, meters.heightAt(1, 0), 1e-5f, "10 units * scale 3 = 0.3 m");
        assertTrue(HeightField3D.isHole(meters.heightAt(0, 1)), "a hole stays a hole");
        BodyDescriptor3D body = backend.bodies.values().iterator().next();
        assertEquals(RigidBody3DComponent.MotionType.STATIC, body.motionType());
        assertEquals(2f, body.position().x, 1e-5f, "the body is at the entity, in meters");
        assertTrue(PhysicsSystem3D.isUsedBy(scene), "a terrain collider is a 3D collider");
    }

    @Test
    void shouldRefuseAMovingBodyAMissingSourceAndATrigger() {
        Entity ground = ground();
        ground.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
        IllegalStateException moving = assertThrows(IllegalStateException.class, () -> physics.update(1f / 60f));
        assertTrue(moving.getMessage().contains("Ground has a Terrain Collider 3D on a DYNAMIC body"), moving.getMessage());
        ground.removeComponent(ground.getComponent(RigidBody3DComponent.class));

        scene.setHeightFields(null);
        IllegalStateException noSource = assertThrows(IllegalStateException.class, () -> physics.update(1f / 60f));
        assertTrue(noSource.getMessage().contains("cannot read terrain here"), noSource.getMessage());

        IllegalArgumentException trigger = assertThrows(IllegalArgumentException.class,
            () -> ground.getComponent(TerrainCollider3DComponent.class).setTrigger(true));
        assertTrue(trigger.getMessage().contains("cannot be a trigger"), trigger.getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> PhysicsSystem3D.describe(new TerrainCollider3DComponent(), new Vector3(1f, 1f, 1f)), "built from its terrain only");
    }

    @Test
    void shouldRebuildTheBodyOnlyWhenTheTerrainRevisionChanges() {
        ground();
        for (int i = 0; i < 30; i++) {
            physics.update(1f / 60f);
        }
        assertEquals(1, reads, "read once");
        int firstHandle = backend.bodies.keySet().iterator().next();

        field = flat(3, 2, 250f);
        revision++;
        physics.update(1f / 60f);

        assertEquals(2, reads, "read again after a change");
        assertEquals(1, backend.bodies.size(), "the old body was removed");
        assertFalse(backend.bodies.containsKey(firstHandle));
        assertEquals(2.5f, onlyShape().heightField().heightAt(0, 0), 1e-5f, "the new body has the new heights");
    }

    @Test
    void shouldSaveAndReadBackATerrainCollider() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new TerrainCollider3DComponentSerializer());
        SceneSerializer serializer = new SceneSerializer(registry);
        Entity ground = ground();
        ground.getComponent(TerrainCollider3DComponent.class).setOffset(new Vector3(0f, -5f, 0f));
        Entity other = scene.createEntity("Other");

        Scene read = serializer.fromJson(serializer.toJson(scene));

        TerrainCollider3DComponent readCollider = read.findEntityById(ground.getId()).getComponent(TerrainCollider3DComponent.class);
        assertNotNull(readCollider);
        assertEquals(new Vector3(0f, -5f, 0f), readCollider.getOffset());
        assertFalse(readCollider.isTrigger());
        assertNull(read.findEntityById(other.getId()).getComponent(TerrainCollider3DComponent.class));
        read.dispose();
    }
}
