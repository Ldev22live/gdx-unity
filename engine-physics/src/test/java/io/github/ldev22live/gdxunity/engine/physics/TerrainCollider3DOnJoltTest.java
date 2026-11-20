package io.github.ldev22live.gdxunity.engine.physics;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightField3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightFieldSource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D.RaycastHit;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Terrain collision (roadmap M3.3, TD-087) on the real Jolt backend, in scene units (100 per meter): bodies rest on a height field,
 * rays hit it where the terrain is, characters walk up gentle slopes, are stopped by and slide down steep ones, jump and land, and
 * the ground is rebuilt when the terrain changes. The heights come from a stand-in source; the real {@code .terrain} files are
 * checked in {@code engine-desktop}.
 */
class TerrainCollider3DOnJoltTest {
    private static final float FRAME = 1f / 60f;
    /** Where a default character's center is above the ground it stands on: half of 80 plus the 30 radius. */
    private static final float STANDING_HEIGHT = 70f;
    /** The bound TD-087 sets between the physics ground and the terrain: 1 cm, one scene unit. */
    private static final float HEIGHT_TOLERANCE = 1f;
    private static final float WALK_SPEED = 150f;

    private Scene scene;
    private PhysicsSystem3D physics;
    private final FakeHeightFields source = new FakeHeightFields();

    /** Heights per entity, with a revision that the test bumps when it changes them. */
    private static final class FakeHeightFields implements HeightFieldSource {
        final Map<UUID, HeightField3D> fields = new HashMap<>();
        final Map<UUID, Long> revisions = new HashMap<>();
        int reads;

        @Override
        public HeightField3D heightFieldOf(Entity entity) {
            reads++;
            HeightField3D field = fields.get(entity.getId());
            if (field == null) {
                throw new IllegalStateException(entity.getName() + " has a Terrain Collider 3D but no Terrain");
            }
            return field;
        }

        @Override
        public long revisionOf(Entity entity) {
            return revisions.getOrDefault(entity.getId(), 0L);
        }

        void change(Entity entity, HeightField3D field) {
            fields.put(entity.getId(), field);
            revisions.merge(entity.getId(), 1L, Long::sum);
        }
    }

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Terrain");
        scene.setHeightFields(source);
        physics = new PhysicsSystem3D(scene, new JoltPhysicsBackend3D());
    }

    @AfterEach
    void tearDown() {
        physics.dispose();
        scene.dispose();
    }

    /** A grid centred on the entity, like a terrain: {@code columns} by {@code rows} points {@code cell} apart, heights from (x, z). */
    private static HeightField3D grid(int columns, int rows, float cell, BinaryOperator<Float> height) {
        float originX = -(columns - 1) * cell / 2f;
        float originZ = -(rows - 1) * cell / 2f;
        float[] heights = new float[columns * rows];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                heights[row * columns + column] = height.apply(originX + column * cell, originZ + row * cell);
            }
        }
        return new HeightField3D(columns, rows, cell, cell, originX, originZ, heights);
    }

    private Entity ground(String name, float x, float z, HeightField3D field) {
        Entity ground = scene.createEntity(name);
        ground.getTransform().setLocalPosition(x, 0f, z);
        ground.addComponent(new TerrainCollider3DComponent());
        source.change(ground, field);
        return ground;
    }

    private Entity character(String name, float x, float y, float z) {
        Entity hero = scene.createEntity(name);
        hero.getTransform().setLocalPosition(x, y, z);
        hero.addComponent(new CharacterController3DComponent());
        return hero;
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            physics.update(FRAME);
        }
    }

    private Optional<RaycastHit> down(float x, float z) {
        return physics.raycast(new Vector3(x, 5000f, z), new Vector3(0f, -1f, 0f), 20000f, PhysicsLayers3D.ALL_LAYERS);
    }

    private static Vector3 position(Entity entity) {
        return entity.getTransform().getWorldPosition();
    }

    @Test
    void shouldLetASphereComeToRestOnTheGround() {
        ground("Ground", 0f, 0f, grid(21, 21, 100f, (x, z) -> 0f));
        Entity ball = scene.createEntity("Ball");
        ball.getTransform().setLocalPosition(0f, 300f, 0f);
        ball.addComponent(new SphereCollider3DComponent(50f));
        ball.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));

        run(3 * 60);

        // A resting body sinks up to Jolt's penetration slop (2 cm) into whatever it rests on; the box floor test allows the same 5
        assertEquals(50f, position(ball).y, 5f, "the ball rests on the ground, its radius above it");
    }

    @Test
    void shouldHitTheGroundWhereTheTerrainIsWithinOneCentimetre() {
        // Rolling hills 6 m high on a 40 m square, with the cell size of the valley sample (1 m)
        BinaryOperator<Float> hills = (x, z) -> (float) (300.0 * Math.sin(x / 700.0) * Math.cos(z / 500.0));
        Entity ground = ground("Hills", 250f, -120f, grid(41, 41, 100f, hills));
        run(1);
        assertTrue(physics.hasBody(ground), "the terrain has a body");

        float worst = 0f;
        for (int row = 0; row <= 40; row += 3) {
            for (int column = 0; column <= 40; column += 3) {
                float localX = -2000f + column * 100f;
                float localZ = -2000f + row * 100f;
                RaycastHit hit = down(250f + localX, -120f + localZ).orElseThrow(() -> new AssertionError("no ground at a terrain point"));
                worst = Math.max(worst, Math.abs(hit.point().y - hills.apply(localX, localZ)));
            }
        }
        assertTrue(worst <= HEIGHT_TOLERANCE, "the physics ground is within 1 cm of the terrain; the largest difference was " + worst);
        assertTrue(down(250f + 2100f, -120f).isEmpty(), "and nothing is hit beyond its edge");
    }

    @Test
    void shouldWalkUpAGentleSlopeAndBeStoppedByASteepOne() {
        // Flat for x < 0, then rising along +x; each lane is its own terrain
        ground("Gentle", 0f, 0f, grid(61, 11, 50f, (x, z) -> x < 0f ? 0f : (float) (x * Math.tan(Math.toRadians(20)))));
        ground("Steep", 0f, 1000f, grid(61, 11, 50f, (x, z) -> x < 0f ? 0f : (float) (x * Math.tan(Math.toRadians(60)))));
        Entity easy = character("Easy", -400f, STANDING_HEIGHT + 5f, 0f);
        Entity hard = character("Hard", -400f, STANDING_HEIGHT + 5f, 1000f);
        run(30);
        assertTrue(physics.isCharacterGrounded(easy), "stands on the flat part");
        physics.moveCharacter(easy, new Vector3(WALK_SPEED, 0f, 0f));
        physics.moveCharacter(hard, new Vector3(WALK_SPEED, 0f, 0f));

        run(5 * 60);

        assertTrue(position(easy).y > STANDING_HEIGHT + 80f, "climbed the 20 degree slope: " + position(easy));
        assertTrue(position(hard).y < STANDING_HEIGHT + 40f, "did not climb the 60 degree slope: " + position(hard));
    }

    @Test
    void shouldSlideDownASlopeSteeperThanItsLimit() {
        float degrees = 60f;
        ground("Cliff", 0f, 0f, grid(41, 11, 50f, (x, z) -> (float) ((x + 1000f) * Math.tan(Math.toRadians(degrees)))));
        float startX = 500f;
        float surface = (float) ((startX + 1000f) * Math.tan(Math.toRadians(degrees)));
        Entity hero = character("Hero", startX, surface + 150f, 0f);

        run(3 * 60);

        assertTrue(position(hero).x < startX - 100f, "slid down the 60 degree slope, more than 1 m: " + position(hero));
        assertTrue(position(hero).y < surface, "and is lower than where it landed: " + position(hero));
    }

    @Test
    void shouldJumpFromTheGroundAndLandOnItAgain() {
        BinaryOperator<Float> waves = (x, z) -> (float) (40.0 * Math.sin(x / 300.0));
        ground("Ground", 0f, 0f, grid(31, 31, 100f, waves));
        Entity hero = character("Hero", 100f, waves.apply(100f, 0f) + STANDING_HEIGHT + 20f, 0f);
        run(60);
        assertTrue(physics.isCharacterGrounded(hero), "stands on the terrain");

        assertTrue(physics.jumpCharacter(hero, 500f), "jumps from it");
        run(10);
        assertFalse(physics.isCharacterGrounded(hero), "in the air");
        run(90);

        assertTrue(physics.isCharacterGrounded(hero), "landed on the terrain again");
        assertEquals(waves.apply(position(hero).x, 0f) + STANDING_HEIGHT, position(hero).y, 10f, "on the ground under it");
    }

    @Test
    void shouldCollideOnlyWhereANonSquareTerrainIsAndNotThroughAHole() {
        // 9 columns by 5 rows: padded to a square in Jolt, and the padding must not be ground
        float[] heights = new float[9 * 5];
        heights[2 * 9 + 4] = HeightField3D.NO_COLLISION;
        ground("Strip", 0f, 0f, new HeightField3D(9, 5, 100f, 100f, -400f, -200f, heights));
        run(1);

        assertTrue(down(-350f, -150f).isPresent(), "inside the terrain");
        assertTrue(down(350f, 150f).isPresent(), "near the far corner");
        assertTrue(down(0f, 350f).isEmpty(), "past the last row, where the square is only padding");
        assertTrue(down(0f, 0f).isEmpty(), "through the hole");
    }

    @Test
    void shouldRebuildTheGroundOnlyWhenTheTerrainChanges() {
        Entity ground = ground("Ground", 0f, 0f, grid(11, 11, 100f, (x, z) -> 0f));
        run(60);
        assertEquals(1, source.reads, "read once when physics started, not on every step");
        assertEquals(0f, down(0f, 0f).orElseThrow().point().y, HEIGHT_TOLERANCE);

        source.change(ground, grid(11, 11, 100f, (x, z) -> 200f));
        run(1);

        assertEquals(2, source.reads, "read again after the terrain changed");
        assertEquals(200f, down(0f, 0f).orElseThrow().point().y, HEIGHT_TOLERANCE, "the raised ground is hit");
    }
}
