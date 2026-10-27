package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsSystem2DTest {

    private Scene scene;
    private PhysicsSystem2D physicsSystem;

    @BeforeEach
    void setUp() {
        scene = new Scene("PhysicsTestScene");
    }

    @AfterEach
    void tearDown() {
        if (physicsSystem != null) {
            physicsSystem.dispose();
            physicsSystem = null;
        }
    }

    @Test
    void shouldInitializeNativeBox2DAndCreateBodies() {
        Entity entity = scene.createEntity("DynamicBox");
        entity.getTransform().setLocalPosition(100.0f, 200.0f, 0.0f);
        RigidBody2DComponent rb = new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC);
        entity.addComponent(rb);
        entity.addComponent(new BoxCollider2DComponent(50.0f, 50.0f));

        physicsSystem = new PhysicsSystem2D(scene);

        assertNotNull(physicsSystem.getWorld());
        assertEquals(1, physicsSystem.getBodyCount());

        var body = physicsSystem.getBody(entity);
        assertNotNull(body);
        assertEquals(1.0f, body.getPosition().x, 0.001f);
        assertEquals(2.0f, body.getPosition().y, 0.001f);
    }

    @Test
    void shouldSimulateDynamicBodyFallUnderGravity() {
        Entity ground = scene.createEntity("Ground");
        ground.getTransform().setLocalPosition(0.0f, 0.0f, 0.0f);
        ground.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.STATIC));
        ground.addComponent(new BoxCollider2DComponent(500.0f, 20.0f));

        Entity falling = scene.createEntity("FallingBall");
        falling.getTransform().setLocalPosition(0.0f, 200.0f, 0.0f);
        falling.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        falling.addComponent(new CircleCollider2DComponent(15.0f));

        physicsSystem = new PhysicsSystem2D(scene, new Vector2(0.0f, -9.81f));

        // Step simulation for 0.5 seconds
        for (int i = 0; i < 30; i++) {
            physicsSystem.update(1.0f / 60.0f);
        }

        // Falling entity should have moved downwards in Y
        assertTrue(falling.getTransform().getLocalPosition().y < 200.0f,
            "Falling entity should have lower Y coordinate after simulation");
    }

    @Test
    void shouldBufferAndDispatchCollisionEvents() {
        Entity ground = scene.createEntity("Ground");
        ground.getTransform().setLocalPosition(0.0f, 0.0f, 0.0f);
        ground.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.STATIC));
        ground.addComponent(new BoxCollider2DComponent(200.0f, 50.0f));

        Entity falling = scene.createEntity("FallingBox");
        // Place just slightly above ground to trigger collision within a few steps
        falling.getTransform().setLocalPosition(0.0f, 50.0f, 0.0f);
        falling.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        falling.addComponent(new BoxCollider2DComponent(50.0f, 50.0f));

        physicsSystem = new PhysicsSystem2D(scene, new Vector2(0.0f, -9.81f));

        List<Collision2D> events = new ArrayList<>();
        physicsSystem.addCollisionListener(events::add);

        // Step simulation until contact occurs
        for (int i = 0; i < 60; i++) {
            physicsSystem.update(1.0f / 60.0f);
            if (!events.isEmpty()) {
                break;
            }
        }

        assertFalse(events.isEmpty(), "Collision event should have been buffered and dispatched");
        Collision2D event = events.get(0);
        assertNotNull(event);
        assertEquals(Collision2D.Type.COLLISION_ENTER, event.type());
        assertTrue(event.entity() == ground || event.entity() == falling);
        assertTrue(event.otherEntity() == ground || event.otherEntity() == falling);
        assertNotNull(event.collider());
        assertNotNull(event.otherCollider());
    }

    @Test
    void shouldHandleParentedEntityCoordinateSynchronization() {
        Entity parent = scene.createEntity("ParentPlatform");
        parent.getTransform().setLocalPosition(200.0f, 100.0f, 0.0f);

        Entity child = scene.createEntity("ChildDynamic");
        child.getTransform().setParent(parent.getTransform());
        child.getTransform().setLocalPosition(0.0f, 150.0f, 0.0f);
        child.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        child.addComponent(new BoxCollider2DComponent(30.0f, 30.0f));

        physicsSystem = new PhysicsSystem2D(scene, new Vector2(0.0f, -9.81f));

        var body = physicsSystem.getBody(child);
        assertNotNull(body);
        // Initial world position of child: parent (200, 100) + child (0, 150) = (200, 250) -> in physics: (2.0, 2.5)
        assertEquals(2.0f, body.getPosition().x, 0.001f);
        assertEquals(2.5f, body.getPosition().y, 0.001f);

        // Step simulation
        for (int i = 0; i < 10; i++) {
            physicsSystem.update(1.0f / 60.0f);
        }

        assertTrue(child.getTransform().getLocalPosition().y < 150.0f);
    }

    @Test
    void shouldSupportMultipleCollidersOnSingleEntity() {
        Entity compound = scene.createEntity("CompoundEntity");
        compound.getTransform().setLocalPosition(50.0f, 50.0f, 0.0f);
        compound.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));

        BoxCollider2DComponent box = new BoxCollider2DComponent(40.0f, 20.0f);
        box.setOffset(-10.0f, 0.0f);
        compound.addComponent(box);

        CircleCollider2DComponent circle = new CircleCollider2DComponent(15.0f);
        circle.setOffset(20.0f, 0.0f);
        compound.addComponent(circle);

        physicsSystem = new PhysicsSystem2D(scene);
        var body = physicsSystem.getBody(compound);
        assertNotNull(body);
        assertEquals(2, body.getFixtureList().size, "Body should have 2 fixtures attached");
    }

    @Test
    void shouldHandleKinematicAndStaticBodyMovementCorrectness() {
        Entity staticGround = scene.createEntity("StaticWall");
        staticGround.getTransform().setLocalPosition(0.0f, 0.0f, 0.0f);
        staticGround.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.STATIC));
        staticGround.addComponent(new BoxCollider2DComponent(100.0f, 20.0f));

        Entity kinematicPlatform = scene.createEntity("KinematicPlatform");
        kinematicPlatform.getTransform().setLocalPosition(0.0f, 100.0f, 0.0f);
        RigidBody2DComponent kinRb = new RigidBody2DComponent(RigidBody2DComponent.BodyType.KINEMATIC);
        kinRb.setInitialLinearVelocity(5.0f, 0.0f); // 5 m/s = 500 px/s
        kinematicPlatform.addComponent(kinRb);
        kinematicPlatform.addComponent(new BoxCollider2DComponent(60.0f, 10.0f));

        physicsSystem = new PhysicsSystem2D(scene);

        // Step 10 times (1/60th sec each)
        for (int i = 0; i < 10; i++) {
            physicsSystem.update(1.0f / 60.0f);
        }

        // Static ground must not move at all
        assertEquals(0.0f, staticGround.getTransform().getLocalPosition().x, 0.001f);
        assertEquals(0.0f, staticGround.getTransform().getLocalPosition().y, 0.001f);

        // Kinematic platform should have moved in X direction according to initial velocity
        assertTrue(kinematicPlatform.getTransform().getLocalPosition().x > 0.0f,
            "Kinematic platform should advance based on initial velocity");
    }

    @Test
    void shouldHandleRotatedAndScaledParentHierarchyCorrectness() {
        Entity parent = scene.createEntity("RotatedParent");
        parent.getTransform().setLocalPosition(100.0f, 100.0f, 0.0f);
        parent.getTransform().setLocalScale(2.0f, 2.0f, 1.0f);
        parent.getTransform().setLocalRotation(new com.badlogic.gdx.math.Quaternion(new com.badlogic.gdx.math.Vector3(0f, 0f, 1f), 90.0f));

        Entity child = scene.createEntity("DynamicChild");
        child.getTransform().setParent(parent.getTransform());
        child.getTransform().setLocalPosition(10.0f, 0.0f, 0.0f);
        child.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        child.addComponent(new CircleCollider2DComponent(10.0f));

        physicsSystem = new PhysicsSystem2D(scene, new Vector2(0.0f, -9.81f));

        var childBody = physicsSystem.getBody(child);
        assertNotNull(childBody);

        // Step simulation safely
        for (int i = 0; i < 10; i++) {
            physicsSystem.update(1.0f / 60.0f);
        }

        // Check child local coordinates are finite and updated without crashing
        assertTrue(Float.isFinite(child.getTransform().getLocalPosition().x));
        assertTrue(Float.isFinite(child.getTransform().getLocalPosition().y));
    }

    @Test
    void shouldSafelyHandleZeroScaleAndNonFiniteHierarchyTransform() {
        Entity zeroScaleParent = scene.createEntity("ZeroScaleParent");
        zeroScaleParent.getTransform().setLocalScale(0.0f, 0.0f, 1.0f);

        Entity child = scene.createEntity("ChildOfZeroScale");
        child.getTransform().setParent(zeroScaleParent.getTransform());
        child.getTransform().setLocalPosition(10.0f, 20.0f, 0.0f);
        child.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        child.addComponent(new BoxCollider2DComponent(20.0f, 20.0f));

        physicsSystem = new PhysicsSystem2D(scene, new Vector2(0.0f, -9.81f));

        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) {
                physicsSystem.update(1.0f / 60.0f);
            }
        });

        assertTrue(Float.isFinite(child.getTransform().getLocalPosition().x));
        assertTrue(Float.isFinite(child.getTransform().getLocalPosition().y));
    }

    @Test
    void shouldTriggerSensorEnterAndExitEvents() {
        Entity triggerArea = scene.createEntity("SensorZone");
        triggerArea.getTransform().setLocalPosition(0.0f, 0.0f, 0.0f);
        BoxCollider2DComponent triggerBox = new BoxCollider2DComponent(100.0f, 100.0f);
        triggerBox.setSensor(true);
        triggerArea.addComponent(triggerBox);

        Entity dynamicActor = scene.createEntity("Actor");
        dynamicActor.getTransform().setLocalPosition(0.0f, 20.0f, 0.0f);
        dynamicActor.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        dynamicActor.addComponent(new CircleCollider2DComponent(10.0f));

        physicsSystem = new PhysicsSystem2D(scene, new Vector2(0.0f, 0.0f)); // Zero gravity

        List<Collision2D> events = new ArrayList<>();
        physicsSystem.addCollisionListener(events::add);

        // Step simulation
        for (int i = 0; i < 5; i++) {
            physicsSystem.update(1.0f / 60.0f);
        }

        assertFalse(events.isEmpty(), "Sensor event should have triggered");
        assertTrue(events.stream().anyMatch(e -> e.type() == Collision2D.Type.SENSOR_ENTER));
    }
}

