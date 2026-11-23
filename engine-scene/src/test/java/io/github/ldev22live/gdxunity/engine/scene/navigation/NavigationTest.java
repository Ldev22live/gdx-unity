package io.github.ldev22live.gdxunity.engine.scene.navigation;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.navigation.serialization.NavigationSerializers;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightField3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightFieldSource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M6.4's done-when, "an agent paths around an obstacle", without physics or graphics: the bake (floor, obstacles, clearance,
 * steps, headroom, terrain), A* with straightening, an agent walking the path frame by frame, and the files (TD-092).
 */
class NavigationTest {
    /** A wall 1 m thick and 2 m high, from z = -600 to 600, between the start and the goal. */
    private static final float WALL_HALF_LENGTH = 600f;
    private static final float WALL_HALF_THICKNESS = 50f;
    private static final Vector3 START = new Vector3(-600f, 0f, 0f);
    private static final Vector3 GOAL = new Vector3(600f, 0f, 0f);

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** 20 m of flat ground (its top at y 0), Nav Mesh Bounds over it, and optionally a wall across the middle. */
    private static Scene room(float wallHalfLength) {
        Scene scene = new Scene("Room");
        Entity ground = scene.createEntity("Ground");
        ground.getTransform().setLocalPosition(0f, -10f, 0f);
        ground.addComponent(new BoxCollider3DComponent(new Vector3(2000f, 20f, 2000f)));
        scene.createEntity("Bounds").addComponent(new NavMeshBoundsComponent());
        if (wallHalfLength > 0f) {
            Entity wall = scene.createEntity("Wall");
            wall.getTransform().setLocalPosition(0f, 100f, 0f);
            wall.addComponent(new BoxCollider3DComponent(new Vector3(2 * WALL_HALF_THICKNESS, 200f, 2 * wallHalfLength)));
        }
        return scene;
    }

    private static NavGrid3D grid(Scene scene) {
        NavigationSystem3D navigation = NavigationSystem3D.of(scene);
        assertEquals(List.of(), navigation.getProblems());
        assertEquals(1, navigation.getRegions().size());
        return navigation.getRegions().get(0).grid();
    }

    /** Whether a point is closer to the wall's footprint than the agent radius allows (less a quarter cell of grid rounding). */
    private static boolean tooCloseToWall(float x, float z) {
        float margin = NavMeshBoundsComponent.DEFAULT_AGENT_RADIUS - NavMeshBoundsComponent.DEFAULT_CELL_SIZE * 0.25f;
        float dx = Math.max(0f, Math.abs(x) - WALL_HALF_THICKNESS);
        float dz = Math.max(0f, Math.abs(z) - WALL_HALF_LENGTH);
        return dx * dx + dz * dz < margin * margin;
    }

    @Test
    void shouldBakeTheGroundAsFloorAndTheWallAsAnObstacleWithClearance() {
        NavGrid3D grid = grid(room(WALL_HALF_LENGTH));
        assertEquals(80, grid.getColumns(), "20 m in 25 cm cells");
        assertEquals(80, grid.getRows());
        int column = grid.columnOf(-300f);
        int row = grid.rowOf(0f);
        assertEquals(0f, grid.floorAt(column, row), 1e-3f, "the ground's top");
        assertTrue(grid.isWalkable(column, row));
        assertEquals(200f, grid.floorAt(grid.columnOf(0f), row), 1e-3f, "no room above the ground there: the wall's top is the floor");
        assertTrue(grid.findPath(new Vector3(-300f, 0f, 0f), new Vector3(10f, 200f, 0f)).stream().noneMatch(p -> p.y > 1f),
            "and no path climbs onto it");
        assertFalse(grid.isWalkableAt(-WALL_HALF_THICKNESS - 20f, 0f), "within the agent radius of the wall");
        assertTrue(grid.isWalkableAt(-WALL_HALF_THICKNESS - 60f, 0f), "past it");
        assertFalse(grid.isWalkableAt(-990f, 0f), "within the radius of the bounds' side");
    }

    @Test
    void shouldFindAPathAroundTheWallThatNeverComesTooClose() {
        NavGrid3D grid = grid(room(WALL_HALF_LENGTH));
        List<Vector3> path = grid.findPath(START, GOAL);

        assertFalse(path.isEmpty());
        assertEquals(GOAL.x, path.get(path.size() - 1).x, 1e-3f, "ends at the goal itself");
        assertEquals(GOAL.z, path.get(path.size() - 1).z, 1e-3f);
        assertTrue(path.size() <= 4, "straightened to a few corners, not one point per cell: " + path);
        float length = 0f;
        Vector3 previous = START;
        for (Vector3 waypoint : path) {
            assertEquals(0f, waypoint.y, 1e-3f, "on the floor");
            for (int i = 0; i <= 100; i++) {
                float t = i / 100f;
                float x = previous.x + (waypoint.x - previous.x) * t;
                float z = previous.z + (waypoint.z - previous.z) * t;
                assertFalse(tooCloseToWall(x, z), "the path passes " + x + ", " + z + ": " + path);
            }
            length += previous.dst(waypoint);
            previous = waypoint;
        }
        float around = 2f * (float) Math.hypot(600f, WALL_HALF_LENGTH + NavMeshBoundsComponent.DEFAULT_AGENT_RADIUS);
        assertTrue(length >= around - 1f && length < around * 1.15f, "close to the shortest way round: " + length + " vs " + around);

        assertEquals(1, grid(room(0f)).findPath(START, GOAL).size(), "without the wall: straight there");
    }

    @Test
    void shouldFindNoPathWhenTheWallClosesTheRoom() {
        Scene scene = room(1200f);
        assertTrue(grid(scene).findPath(START, GOAL).isEmpty());
        assertTrue(NavigationSystem3D.of(scene).findPath(new Vector3(5000f, 0f, 0f), GOAL).isEmpty(), "a start off every grid");
    }

    @Test
    void shouldMakeAHighCrateAnIslandButWalkOverALowStep() {
        Scene scene = room(0f);
        Entity crate = scene.createEntity("Crate");
        crate.getTransform().setLocalPosition(0f, 50f, 0f);
        crate.addComponent(new BoxCollider3DComponent(new Vector3(400f, 100f, 400f)));
        Entity step = scene.createEntity("Step");
        step.getTransform().setLocalPosition(0f, 10f, 700f);
        step.addComponent(new BoxCollider3DComponent(new Vector3(2000f, 20f, 200f)));
        NavGrid3D grid = grid(scene);

        assertEquals(100f, grid.floorAt(grid.columnOf(0f), grid.rowOf(0f)), 1e-3f, "the crate's top is a floor");
        assertTrue(grid.findPath(new Vector3(-700f, 0f, 0f), new Vector3(0f, 100f, 0f)).isEmpty(), "but a separate one");
        List<Vector3> overStep = grid.findPath(new Vector3(-700f, 0f, 500f), new Vector3(-700f, 0f, 900f));
        assertEquals(1, overStep.size(), "20 units is within the step height: straight over it");
        assertEquals(0f, overStep.get(0).y, 1e-3f);
    }

    @Test
    void shouldKeepTheGroundUnderAHighBeamAndBlockItUnderALowOne() {
        Scene scene = room(0f);
        Entity high = scene.createEntity("High Beam");
        high.getTransform().setLocalPosition(-500f, 275f, 0f);
        high.addComponent(new BoxCollider3DComponent(new Vector3(100f, 50f, 2000f)));
        Entity low = scene.createEntity("Low Beam");
        low.getTransform().setLocalPosition(500f, 125f, 0f);
        low.addComponent(new BoxCollider3DComponent(new Vector3(100f, 50f, 2000f)));
        NavGrid3D grid = grid(scene);
        assertEquals(0f, grid.floorAt(grid.columnOf(-500f), grid.rowOf(0f)), 1e-3f, "2.5 m up leaves 1.8 m of room");
        assertEquals(150f, grid.floorAt(grid.columnOf(500f), grid.rowOf(0f)), 1e-3f, "1 m up does not: its top is the floor there");
        assertTrue(grid.findPath(new Vector3(0f, 0f, 0f), new Vector3(800f, 0f, 0f)).isEmpty(), "and it cuts the room in two");
    }

    @Test
    void shouldSeeATurnedBoxAsItIsNotAsItsBox() {
        Scene scene = room(0f);
        Entity pillar = scene.createEntity("Pillar");
        pillar.getTransform().setLocalPosition(0f, 100f, 0f);
        pillar.getTransform().setLocalEulerAngles(45f, 0f, 0f);
        pillar.addComponent(new BoxCollider3DComponent(new Vector3(400f, 200f, 400f)));
        NavGrid3D grid = grid(scene);
        assertEquals(200f, grid.floorAt(grid.columnOf(0f), grid.rowOf(0f)), 1e-3f, "the middle is the pillar's top");
        assertEquals(200f, grid.floorAt(grid.columnOf(250f), grid.rowOf(0f)), 1e-3f, "a corner of the diamond reaches x 283");
        assertTrue(grid.isWalkableAt(200f, 200f), "the turned box's bounding corner is open ground");
    }

    @Test
    void shouldIgnoreMovingBodiesAndTriggers() {
        Scene scene = room(0f);
        Entity ball = scene.createEntity("Ball");
        ball.getTransform().setLocalPosition(0f, 50f, 0f);
        ball.addComponent(new BoxCollider3DComponent(new Vector3(300f, 100f, 300f)));
        ball.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
        Entity zone = scene.createEntity("Zone");
        BoxCollider3DComponent trigger = new BoxCollider3DComponent(new Vector3(300f, 100f, 300f));
        trigger.setTrigger(true);
        zone.addComponent(trigger);
        zone.getTransform().setLocalPosition(500f, 50f, 0f);
        NavGrid3D grid = grid(scene);
        assertTrue(grid.isWalkableAt(0f, 0f));
        assertTrue(grid.isWalkableAt(500f, 0f));
    }

    @Test
    void shouldFollowATerrainAndStopAtItsCliff() {
        Scene scene = new Scene("Hill");
        // 21 by 21 points, 100 apart: a gentle slope rising 10 per point along x, then a 300 cliff past x 500
        float[] heights = new float[21 * 21];
        for (int r = 0; r < 21; r++) {
            for (int c = 0; c < 21; c++) {
                heights[r * 21 + c] = c * 10f + (c > 15 ? 300f : 0f);
            }
        }
        HeightField3D field = new HeightField3D(21, 21, 100f, 100f, -1000f, -1000f, heights);
        Entity terrain = scene.createEntity("Terrain");
        terrain.addComponent(new TerrainCollider3DComponent());
        scene.setHeightFields(new HeightFieldSource() {
            @Override
            public HeightField3D heightFieldOf(Entity entity) {
                return field;
            }

            @Override
            public long revisionOf(Entity entity) {
                return 0L;
            }
        });
        NavMeshBoundsComponent bounds = new NavMeshBoundsComponent();
        bounds.setSize(new Vector3(2000f, 2000f, 2000f));
        scene.createEntity("Bounds").addComponent(bounds);
        NavGrid3D grid = grid(scene);

        assertEquals(98.75f, grid.floorAt(grid.columnOf(-10f), grid.rowOf(0f)), 0.01f, "the slope's height at that cell's centre, x -12.5");
        List<Vector3> up = grid.findPath(new Vector3(-800f, 0f, 0f), new Vector3(400f, 0f, 0f));
        assertFalse(up.isEmpty(), "the gentle slope is walkable");
        assertEquals(140f, up.get(up.size() - 1).y, 2f, "and the path ends on it");
        assertTrue(grid.findPath(new Vector3(0f, 0f, 0f), new Vector3(800f, 0f, 0f)).isEmpty(), "the cliff is not");

        scene.setHeightFields(null);
        NavigationSystem3D unread = NavigationSystem3D.of(scene);
        unread.rebake();
        assertTrue(unread.getProblems().get(0).contains("Terrain"), unread.getProblems().toString());
    }

    @Test
    void shouldWalkAnAgentAroundTheWallFrameByFrameAndArrive() {
        Scene scene = room(WALL_HALF_LENGTH);
        Entity walker = scene.createEntity("Walker");
        walker.getTransform().setLocalPosition(START);
        NavAgent3DComponent agent = new NavAgent3DComponent();
        walker.addComponent(agent);

        assertTrue(agent.moveTo(GOAL));
        assertTrue(agent.isMoving());
        List<Vector3> path = agent.getPath();
        assertTrue(path.size() >= 2, "round a corner: " + path);
        Vector3 corner = path.get(path.size() - 2);
        float seconds = 0f;
        while (agent.isMoving() && seconds < 20f) {
            scene.update(1f / 60f);
            seconds += 1f / 60f;
            Vector3 at = walker.getTransform().getWorldPosition();
            assertFalse(tooCloseToWall(at.x, at.z), "walked into the wall's clearance at " + at);
            assertEquals(0f, at.y, 1e-3f, "stays on the floor");
        }
        assertTrue(agent.hasArrived(), "arrived in " + seconds + " s");
        Vector3 end = walker.getTransform().getWorldPosition();
        assertTrue(Math.hypot(end.x - GOAL.x, end.z - GOAL.z) <= NavAgent3DComponent.DEFAULT_STOPPING_DISTANCE + 1e-3f, "at " + end);
        float shortest = 2f * (float) Math.hypot(600f, WALL_HALF_LENGTH + NavMeshBoundsComponent.DEFAULT_AGENT_RADIUS);
        assertTrue(seconds >= (shortest - 2 * NavAgent3DComponent.DEFAULT_STOPPING_DISTANCE) / NavAgent3DComponent.DEFAULT_SPEED,
            "it walked, it did not jump: " + seconds + " s");
        float lastStretch = (float) Math.toDegrees(Math.atan2(GOAL.x - corner.x, GOAL.z - corner.z));
        assertEquals(lastStretch, walker.getTransform().getLocalEulerAngles().x, 1f, "facing along the last stretch, +Z turned to it");
    }

    @Test
    void shouldReportAnUnreachableGoalAndStopOnRequest() {
        Scene scene = room(1200f);
        Entity walker = scene.createEntity("Walker");
        walker.getTransform().setLocalPosition(START);
        NavAgent3DComponent agent = new NavAgent3DComponent();
        walker.addComponent(agent);
        assertFalse(NavAgent3DComponent.moveTo(walker, GOAL));
        assertEquals(NavAgent3DComponent.Status.UNREACHABLE, agent.getStatus());

        assertTrue(agent.moveTo(new Vector3(-600f, 0f, 500f)));
        scene.update(0.1f);
        NavAgent3DComponent.stop(walker);
        Vector3 stopped = walker.getTransform().getWorldPosition();
        scene.update(0.5f);
        assertEquals(stopped, walker.getTransform().getWorldPosition(), "stays where it stopped");
        assertEquals(NavAgent3DComponent.Status.IDLE, agent.getStatus());
        assertFalse(NavAgent3DComponent.hasArrived(walker));
        assertSame(NavigationSystem3D.of(scene), scene.getNavigation(), "baked once for the scene");

        Entity rock = scene.createEntity("Rock");
        IllegalStateException none = assertThrows(IllegalStateException.class, () -> NavAgent3DComponent.moveTo(rock, GOAL));
        assertTrue(none.getMessage().contains("Rock has no Nav Agent"), none.getMessage());
    }

    @Test
    void shouldRefuseAGridTooLargeToBake() {
        Scene scene = new Scene("Huge");
        NavMeshBoundsComponent bounds = new NavMeshBoundsComponent();
        bounds.setSize(new Vector3(100000f, 500f, 100000f));
        bounds.setCellSize(10f);
        scene.createEntity("Bounds").addComponent(bounds);
        NavigationSystem3D navigation = NavigationSystem3D.of(scene);
        assertTrue(navigation.getRegions().isEmpty());
        assertTrue(navigation.getProblems().get(0).contains("make the box smaller or the cells larger"));
    }

    @Test
    void shouldSaveAndReadEverySettingAndRefuseABadOne() {
        Scene scene = new Scene("Saved");
        NavMeshBoundsComponent bounds = new NavMeshBoundsComponent();
        bounds.setSize(new Vector3(3000f, 600f, 1500f));
        bounds.setCellSize(40f);
        bounds.setAgentRadius(50f);
        bounds.setAgentHeight(200f);
        bounds.setMaxSlope(30f);
        bounds.setStepHeight(20f);
        Entity entity = scene.createEntity("Area");
        entity.addComponent(bounds);
        NavAgent3DComponent agent = new NavAgent3DComponent();
        agent.setSpeed(450f);
        agent.setStoppingDistance(60f);
        agent.setFaceMovement(false);
        scene.createEntity("Guard").addComponent(agent);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        NavigationSerializers.registerAll(registry);
        SceneSerializer serializer = new SceneSerializer(registry);
        String json = serializer.toJson(scene);
        Scene read = serializer.fromJson(json);

        NavMeshBoundsComponent readBounds = read.findEntityByName("Area").getComponent(NavMeshBoundsComponent.class);
        assertEquals(new Vector3(3000f, 600f, 1500f), readBounds.getSize());
        assertEquals(40f, readBounds.getCellSize());
        assertEquals(50f, readBounds.getAgentRadius());
        assertEquals(200f, readBounds.getAgentHeight());
        assertEquals(30f, readBounds.getMaxSlope());
        assertEquals(20f, readBounds.getStepHeight());
        NavAgent3DComponent readAgent = read.findEntityByName("Guard").getComponent(NavAgent3DComponent.class);
        assertNotNull(readAgent);
        assertEquals(450f, readAgent.getSpeed());
        assertEquals(60f, readAgent.getStoppingDistance());
        assertFalse(readAgent.isFaceMovement());

        String bad = json.replaceFirst("\"maxSlope\"\\s*:\\s*30(\\.0)?", "\"maxSlope\":95");
        SceneSerializationException error = assertThrows(SceneSerializationException.class, () -> serializer.fromJson(bad));
        assertTrue(error.getMessage().contains("Max slope"), error.getMessage());
    }
}
