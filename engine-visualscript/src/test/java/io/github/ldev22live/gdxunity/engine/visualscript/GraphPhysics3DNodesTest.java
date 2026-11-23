package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsBackend3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 3D physics nodes (TD-048): what they do, what they refuse, and the 3D contacts that reach graph events. */
class GraphPhysics3DNodesTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    private static final class Host implements GraphHost {
        final List<String> lines = new ArrayList<>();
        final List<GraphRuntimeException> errors = new ArrayList<>();

        @Override
        public void log(GraphInstance instance, String message) {
            lines.add(message);
        }

        @Override
        public void error(GraphInstance instance, GraphRuntimeException error) {
            errors.add(error);
        }
    }

    private Scene scene;
    private Entity self;
    private Entity wall;
    private StubPhysicsBackend3D backend;
    private PhysicsSystem3D physics;
    private Graph graph;
    private Host host;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Physics graphs");
        self = scene.createEntity("Self");
        self.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
        self.addComponent(new SphereCollider3DComponent());
        wall = scene.createEntity("Wall");
        wall.addComponent(new BoxCollider3DComponent());
        graph = new Graph("Physics");
        host = new Host();
    }

    @AfterEach
    void tearDown() {
        if (physics != null) {
            physics.dispose();
        }
    }

    /** Starts physics on the scene so the entities have bodies; the wall is the second body. */
    private void startPhysics() {
        backend = new StubPhysicsBackend3D();
        physics = new PhysicsSystem3D(scene, backend);
        physics.update(0f);
        backend.hitHandle = backend.handle(1);
    }

    private String node(String type) {
        GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
        graph.addNode(node);
        return node.getId();
    }

    private String node(String type, String pin, Object literal) {
        String id = node(type);
        graph.getNode(id).setLiteral(pin, literal);
        return id;
    }

    private void wire(String from, String fromPin, String to, String toPin) {
        graph.addLink(new GraphLink(from, fromPin, to, toPin));
    }

    private void exec(String from, String to) {
        wire(from, PinDef.EXEC_OUT, to, PinDef.EXEC_IN);
    }

    private GraphInstance instanceOn(Entity entity) {
        return new GraphInstance(graph, REGISTRY, entity, host);
    }

    private String print(String message) {
        return node(ACTION_PRINT, "message", message);
    }

    /** A Raycast 3D node from (0, 100, 0) straight down. */
    private String raycast(float distance) {
        String ray = node(ACTION_RAYCAST_3D);
        graph.getNode(ray).setLiteral("origin", new Vector3(0f, 100f, 0f));
        graph.getNode(ray).setLiteral("direction", new Vector3(0f, -1f, 0f));
        graph.getNode(ray).setLiteral("distance", distance);
        return ray;
    }

    // --- Raycast 3D ---

    @Test
    void shouldFollowTheHitPinAndOutputWhatTheRayHit() {
        startPhysics();
        String begin = node(EVENT_BEGIN_PLAY);
        String ray = raycast(500f);
        exec(begin, ray);
        String name = node(DATA_GET_NAME);
        wire(ray, PIN_HIT_ENTITY, name, "target");
        String text = node(TEXT_APPEND, "a", "hit ");
        wire(name, "name", text, "b");
        String first = node(ACTION_PRINT);
        wire(text, "result", first, "message");
        wire(ray, PIN_HIT, first, PinDef.EXEC_IN);
        String distanceText = node(TEXT_FROM_FLOAT);
        wire(ray, PIN_HIT_DISTANCE, distanceText, "value");
        String second = node(ACTION_PRINT);
        wire(distanceText, "text", second, "message");
        exec(first, second);
        wire(ray, PIN_MISS, print("miss"), PinDef.EXEC_IN);

        instanceOn(self).beginPlay();

        assertTrue(host.errors.isEmpty(), host.errors.toString());
        assertEquals(List.of("hit Wall", "50"), host.lines, "the entity hit, and 0.5 m as 50 scene units");
    }

    @Test
    void shouldFollowTheMissPinWhenTheRayHitsNothing() {
        startPhysics();
        backend.hitHandle = null;
        String begin = node(EVENT_BEGIN_PLAY);
        String ray = raycast(500f);
        exec(begin, ray);
        wire(ray, PIN_HIT, print("hit"), PinDef.EXEC_IN);
        wire(ray, PIN_MISS, print("miss"), PinDef.EXEC_IN);

        instanceOn(self).beginPlay();

        assertEquals(List.of("miss"), host.lines);
    }

    @Test
    void shouldReportARayWithoutDistanceAsAnErrorOnTheNode() {
        startPhysics();
        String begin = node(EVENT_BEGIN_PLAY);
        String ray = raycast(0f);
        exec(begin, ray);

        instanceOn(self).beginPlay();

        assertEquals(1, host.errors.size());
        assertEquals(ray, host.errors.get(0).getNodeId());
        assertTrue(host.errors.get(0).getMessage().contains("positive distance"), host.errors.get(0).getMessage());
    }

    // --- Forces and velocities ---

    @Test
    void shouldSendImpulseForceAndVelocityToTheBackendInMeters() {
        startPhysics();
        String begin = node(EVENT_BEGIN_PLAY);
        String impulse = node(ACTION_ADD_IMPULSE, "impulse", new Vector3(0f, 500f, 0f));
        String force = node(ACTION_ADD_FORCE, "force", new Vector3(100f, 0f, 0f));
        String velocity = node(ACTION_SET_VELOCITY, "velocity", new Vector3(0f, 0f, 300f));
        exec(begin, impulse);
        exec(impulse, force);
        exec(force, velocity);

        instanceOn(self).beginPlay();
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);

        assertTrue(host.errors.isEmpty(), host.errors.toString());
        int body = backend.handle(0);
        assertEquals(List.of("impulse " + body + " 0.0 5.0 0.0", "velocity " + body + " 0.0 0.0 3.0", "force " + body + " 1.0 0.0 0.0"),
            backend.calls, "the impulse and velocity act at once; the force acts in the next step");
    }

    @Test
    void shouldReadTheVelocityBackInSceneUnits() {
        startPhysics();
        String begin = node(EVENT_BEGIN_PLAY);
        String set = node(ACTION_SET_VELOCITY, "velocity", new Vector3(0f, 0f, 300f));
        exec(begin, set);
        String get = node(DATA_GET_VELOCITY);
        String parts = node(VECTOR_BREAK);
        wire(get, "velocity", parts, "vector");
        String text = node(TEXT_FROM_FLOAT);
        wire(parts, "z", text, "value");
        String show = node(ACTION_PRINT);
        wire(text, "text", show, "message");
        exec(set, show);

        instanceOn(self).beginPlay();

        assertEquals(List.of("300"), host.lines);
    }

    @Test
    void shouldReportASceneWithoutPhysicsAsAnErrorThatSaysWhatToAdd() {
        String begin = node(EVENT_BEGIN_PLAY);
        String impulse = node(ACTION_ADD_IMPULSE, "impulse", new Vector3(0f, 500f, 0f));
        exec(begin, impulse);

        instanceOn(self).beginPlay();

        assertEquals(1, host.errors.size());
        assertTrue(host.errors.get(0).getMessage().contains("no 3D physics")
            && host.errors.get(0).getMessage().contains("3D collider"), host.errors.get(0).getMessage());
    }

    @Test
    void shouldReportAStaticTargetAsAnErrorNamingTheEntity() {
        startPhysics();
        String begin = node(EVENT_BEGIN_PLAY);
        String impulse = node(ACTION_ADD_IMPULSE, "impulse", new Vector3(0f, 500f, 0f));
        exec(begin, impulse);

        instanceOn(wall).beginPlay();

        assertEquals(1, host.errors.size());
        assertTrue(host.errors.get(0).getMessage().contains("Wall") && host.errors.get(0).getMessage().contains("STATIC"),
            host.errors.get(0).getMessage());
        assertTrue(backend.calls.isEmpty());
    }

    // --- 3D contacts reach graph events ---

    @Test
    void shouldFireCollisionAndTriggerEventsFromThreeDContactsOnBothEntities() {
        SphereCollider3DComponent zone = new SphereCollider3DComponent();
        zone.setTrigger(true);
        self.addComponent(zone);
        startPhysics();
        String onCollision = node(EVENT_COLLISION);
        String name = node(DATA_GET_NAME);
        wire(onCollision, PIN_OTHER, name, "target");
        String text = node(TEXT_APPEND, "a", "collision with ");
        wire(name, "name", text, "b");
        String collisionPrint = node(ACTION_PRINT);
        wire(text, "result", collisionPrint, "message");
        exec(onCollision, collisionPrint);
        exec(node(EVENT_TRIGGER), print("trigger"));
        GraphScriptComponent component = new GraphScriptComponent("physics.graph");
        self.addComponent(component);
        component.bind(instanceOn(self));
        physics.addContactListener(new GraphContactForwarder());

        int solid = backend.handle(0);
        int sensor = backend.handle(1);
        int other = backend.handle(2);
        backend.events.add(new PhysicsBackend3D.ContactEvent(solid, other, true));
        backend.events.add(new PhysicsBackend3D.ContactEvent(sensor, other, true));
        backend.events.add(new PhysicsBackend3D.ContactEvent(solid, other, false));
        physics.update(0f);

        assertTrue(host.errors.isEmpty(), host.errors.toString());
        assertEquals(List.of("collision with Wall", "trigger"), host.lines, "enter events only: exits have no node yet");
    }

    // --- Web ---

    @Test
    void shouldRefuseToGenerateTypeScriptForNodesTheWebPlayerCannotRun() {
        String begin = node(EVENT_BEGIN_PLAY);
        String impulse = node(ACTION_ADD_IMPULSE, "impulse", new Vector3(0f, 500f, 0f));
        exec(begin, impulse);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph));

        assertTrue(error.getMessage().contains(impulse) && error.getMessage().contains("web player")
            && error.getMessage().contains("Generate Java"), error.getMessage());
    }

    @Test
    void shouldRefuseToGenerateTypeScriptForARaycastAndGetVelocity() {
        String begin = node(EVENT_BEGIN_PLAY);
        exec(begin, raycast(100f));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph))
            .getMessage().contains("web player"));

        graph = new Graph("Velocity");
        begin = node(EVENT_BEGIN_PLAY);
        String get = node(DATA_GET_VELOCITY);
        String parts = node(VECTOR_BREAK);
        wire(get, "velocity", parts, "vector");
        String text = node(TEXT_FROM_FLOAT);
        wire(parts, "x", text, "value");
        String show = node(ACTION_PRINT);
        wire(text, "text", show, "message");
        exec(begin, show);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph))
            .getMessage().contains("web player"));
    }

    // --- Walking characters (TD-049) ---

    /** A scene with a character, physics started and the character standing on (stub) ground. */
    private Entity startWithCharacter() {
        Entity hero = scene.createEntity("Hero");
        hero.addComponent(new CharacterController3DComponent());
        startPhysics();
        backend.grounded = true;
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);
        return hero;
    }

    @Test
    void shouldMoveAndJumpACharacterAndTellWhetherItIsGrounded() {
        Entity hero = startWithCharacter();
        String begin = node(EVENT_BEGIN_PLAY);
        String branch = node(FLOW_BRANCH);
        String grounded = node(DATA_IS_GROUNDED);
        wire(grounded, "grounded", branch, "condition");
        exec(begin, branch);
        wire(branch, "true", print("grounded"), PinDef.EXEC_IN);
        wire(branch, "false", print("air"), PinDef.EXEC_IN);
        String move = node(EVENT_TICK);
        String walk = node(ACTION_MOVE_CHARACTER, "velocity", new Vector3(300f, 0f, 0f));
        String jump = node(ACTION_JUMP_CHARACTER, "speed", 400f);
        exec(move, walk);
        exec(walk, jump);
        GraphInstance instance = instanceOn(hero);

        instance.beginPlay();
        instance.tick(PhysicsSystem3D.FIXED_TIMESTEP);
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);

        assertTrue(host.errors.isEmpty(), host.errors.toString());
        assertEquals(List.of("grounded"), host.lines);
        Vector3 moved = backend.characterMoves.get(backend.characterMoves.size() - 1);
        assertEquals(3f, moved.x, 1e-4f, "300 scene units per second is 3 m/s");
        assertTrue(moved.y > 3.9f, "the jump started at 4 m/s: " + moved);
    }

    @Test
    void shouldReportACharacterNodeOnAnEntityThatIsNotACharacterNamingIt() {
        startPhysics();
        String begin = node(EVENT_BEGIN_PLAY);
        String walk = node(ACTION_MOVE_CHARACTER, "velocity", new Vector3(300f, 0f, 0f));
        exec(begin, walk);

        instanceOn(wall).beginPlay();

        assertEquals(1, host.errors.size());
        assertEquals(walk, host.errors.get(0).getNodeId());
        assertTrue(host.errors.get(0).getMessage().contains("Wall") && host.errors.get(0).getMessage().contains("Character Controller 3D"),
            host.errors.get(0).getMessage());
    }

    @Test
    void shouldRefuseToGenerateTypeScriptForTheCharacterNodes() {
        String begin = node(EVENT_BEGIN_PLAY);
        String walk = node(ACTION_MOVE_CHARACTER, "velocity", new Vector3(300f, 0f, 0f));
        exec(begin, walk);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph))
            .getMessage().contains("web player"));

        graph = new Graph("Jump");
        begin = node(EVENT_BEGIN_PLAY);
        exec(begin, node(ACTION_JUMP_CHARACTER, "speed", 400f));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph))
            .getMessage().contains("web player"));

        graph = new Graph("Grounded");
        begin = node(EVENT_BEGIN_PLAY);
        String branch = node(FLOW_BRANCH);
        wire(node(DATA_IS_GROUNDED), "grounded", branch, "condition");
        exec(begin, branch);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph))
            .getMessage().contains("web player"));
    }
}
