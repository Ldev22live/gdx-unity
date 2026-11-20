package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavAgent3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavMeshBoundsComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M6.4's done-when, "an agent paths around an obstacle", in the game runtime on real Jolt physics (TD-092): a guard with a
 * Character Controller 3D and a Nav Agent, told by a behaviour graph file to walk to the far side of a wall, walks round its end and
 * arrives, never touching it. The scene is written to build/m6-4-nav, where the game runtime plays it for the screenshots.
 */
class NavigationGuardOnJoltTest {
    private static final Path OUT = Path.of("build", "m6-4-nav");
    private static final File SAMPLE = new File("../assets/samples/materials-3d.json");
    private static final float FRAME = 1f / 60f;
    /** The wall: 1 m thick, 2 m high, 12 m long along z, between the guard and its goal. */
    private static final float WALL_HALF_THICKNESS = 50f;
    private static final float WALL_HALF_LENGTH = 600f;
    private static final Vector3 GOAL = new Vector3(600f, 0f, 0f);
    /** Where the guard's centre starts: a default character's centre is 70 above the ground it stands on. */
    private static final Vector3 START = new Vector3(-600f, 70f, 0f);

    private GameSession session;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.dispose();
        }
    }

    /** The courtyard: the showroom's sun, sky light and camera, raised to look down; ground, wall, bounds, guard and a goal marker. */
    static void writeCourtyard() throws IOException {
        Files.createDirectories(OUT.resolve("scenes"));
        Files.createDirectories(OUT.resolve("graphs"));
        Scene scene = GameSession.loadScene(SAMPLE);
        scene.setName("Courtyard");
        for (Entity entity : new ArrayList<>(scene.getAllEntities())) {
            if (!List.of("Sun", "Sky Light", "Main Camera").contains(entity.getName())) {
                scene.removeEntity(entity);
            }
        }
        Entity camera = scene.findEntityByName("Main Camera");
        camera.getTransform().setLocalPosition(0f, 1700f, 1500f);
        camera.getTransform().setLocalEulerAngles(0f, -48f, 0f);

        Entity ground = scene.createEntity("Ground");
        ground.getTransform().setLocalPosition(0f, -10f, 0f);
        ground.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 2000f, 20f, 2000f, new Color(0.55f, 0.55f, 0.5f, 1f)));
        ground.addComponent(new BoxCollider3DComponent(new Vector3(2000f, 20f, 2000f)));
        Entity wall = scene.createEntity("Wall");
        wall.getTransform().setLocalPosition(0f, 100f, 0f);
        Vector3 wallSize = new Vector3(2 * WALL_HALF_THICKNESS, 200f, 2 * WALL_HALF_LENGTH);
        wall.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, wallSize.x, wallSize.y, wallSize.z,
            new Color(0.7f, 0.3f, 0.2f, 1f)));
        wall.addComponent(new BoxCollider3DComponent(wallSize));
        scene.createEntity("Nav Mesh Bounds").addComponent(new NavMeshBoundsComponent());
        Entity goal = scene.createEntity("Goal");
        goal.getTransform().setLocalPosition(GOAL.x, 1f, GOAL.z);
        goal.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 80f, 2f, 80f, new Color(0.2f, 0.85f, 0.3f, 1f)));

        Entity guard = scene.createEntity("Guard");
        guard.getTransform().setLocalPosition(START);
        guard.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 60f, 140f, 60f, new Color(0.2f, 0.4f, 0.9f, 1f)));
        guard.addComponent(new CharacterController3DComponent());
        guard.addComponent(new NavAgent3DComponent());
        guard.addComponent(new GraphScriptComponent("graphs/Patrol.graph"));
        Entity visor = scene.createEntity("Visor");
        visor.getTransform().setParent(guard.getTransform());
        visor.getTransform().setLocalPosition(0f, 40f, 35f);
        visor.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 44f, 18f, 12f, new Color(0.95f, 0.85f, 0.2f, 1f)));

        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        Files.writeString(OUT.resolve("scenes/Courtyard.json"), new SceneSerializer(registry).toJson(scene), StandardCharsets.UTF_8);
        Files.writeString(OUT.resolve("graphs/Patrol.graph"), GraphSerializer.toJson(patrol()), StandardCharsets.UTF_8);
        Files.writeString(OUT.resolve("gdxunity.project"), "format=1\nname=Navigation\nstartScene=scenes/Courtyard.json\n",
            StandardCharsets.UTF_8);
    }

    /** Begin Play: Move To the goal, printing "walking" or "no way". */
    private static Graph patrol() {
        Graph graph = new Graph("Patrol");
        GraphNode begin = new GraphNode(graph.newNodeId(), BuiltInNodes.EVENT_BEGIN_PLAY, 0f, 0f);
        graph.addNode(begin);
        GraphNode move = new GraphNode(graph.newNodeId(), BuiltInNodes.ACTION_MOVE_TO, 250f, 0f);
        graph.addNode(move);
        move.setLiteral("destination", new Vector3(GOAL));
        GraphNode walking = new GraphNode(graph.newNodeId(), BuiltInNodes.ACTION_PRINT, 500f, -60f);
        graph.addNode(walking);
        walking.setLiteral("message", "walking");
        GraphNode noWay = new GraphNode(graph.newNodeId(), BuiltInNodes.ACTION_PRINT, 500f, 60f);
        graph.addNode(noWay);
        noWay.setLiteral("message", "no way");
        graph.addLink(new GraphLink(begin.getId(), PinDef.EXEC_OUT, move.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(move.getId(), BuiltInNodes.PIN_MOVING, walking.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(move.getId(), BuiltInNodes.PIN_UNREACHABLE, noWay.getId(), PinDef.EXEC_IN));
        return graph;
    }

    @Test
    void shouldWalkTheGuardRoundTheWallOnJoltAndArrive() throws IOException {
        writeCourtyard();
        AssetSource project = AssetSource.directory(OUT.toFile());
        Scene scene = GameSession.loadScene(project, "scenes/Courtyard.json");
        List<String> lines = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        session = new GameSession(project, scene, new ScriptRegistry(), new GameSession.Log() {
            @Override
            public void info(String message) {
                lines.add(message);
            }

            @Override
            public void error(String message) {
                errors.add(message);
            }
        });
        assertEquals(List.of(), session.start());
        Entity guard = scene.findEntityByName("Guard");
        NavAgent3DComponent agent = guard.getComponent(NavAgent3DComponent.class);

        float closest = Float.MAX_VALUE;
        int frames = 0;
        while (!agent.hasArrived() && frames < 12 * 60) {
            session.update(FRAME);
            frames++;
            Vector3 at = guard.getTransform().getWorldPosition();
            float dx = Math.max(0f, Math.abs(at.x) - WALL_HALF_THICKNESS);
            float dz = Math.max(0f, Math.abs(at.z) - WALL_HALF_LENGTH);
            closest = Math.min(closest, (float) Math.hypot(dx, dz));
        }

        assertEquals(List.of(), errors);
        assertEquals(List.of(), session.getRuntimeErrors());
        assertTrue(lines.stream().anyMatch(line -> line.contains("walking")), "the graph found a way: " + lines);
        assertTrue(agent.hasArrived(), "arrived within 12 s; status " + agent.getStatus() + " at " + guard.getTransform().getWorldPosition());
        Vector3 end = guard.getTransform().getWorldPosition();
        assertTrue(Math.hypot(end.x - GOAL.x, end.z - GOAL.z) <= NavAgent3DComponent.DEFAULT_STOPPING_DISTANCE + 10f, "at " + end);
        assertTrue(closest >= CharacterController3DComponent.DEFAULT_RADIUS, "the guard's capsule never touched the wall: closest "
            + closest);
        assertTrue(frames > 4 * 60, "it walked round, about 13 m at 3 m/s, in " + frames + " frames");
        assertTrue(scene.getPhysics3D().isCharacterGrounded(guard), "standing on the ground");
        for (int i = 0; i < 30; i++) {
            session.update(FRAME);
        }
        Vector3 rested = guard.getTransform().getWorldPosition();
        assertEquals(end.x, rested.x, 2f, "it stopped: no drift after arriving");
        assertEquals(end.z, rested.z, 2f);
    }
}
