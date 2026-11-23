package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What scripts can do with 3D physics: contact callbacks, forces, impulses, velocities and queries (TD-048). */
class ScriptPhysics3DTest {

    /** Writes down every callback it gets, as "kind:other". */
    static final class Recorder extends ScriptComponent {
        final List<String> calls = new ArrayList<>();

        @Override
        public void onCollisionEnter3D(Entity other) {
            calls.add("collisionEnter:" + other.getName());
        }

        @Override
        public void onCollisionExit3D(Entity other) {
            calls.add("collisionExit:" + other.getName());
        }

        @Override
        public void onTriggerEnter3D(Entity other) {
            calls.add("triggerEnter:" + other.getName());
        }

        @Override
        public void onTriggerExit3D(Entity other) {
            calls.add("triggerExit:" + other.getName());
        }
    }

    private Scene scene;
    private PhysicsSystem3DTest.FakeBackend backend;
    private PhysicsSystem3D physics;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Scripts");
        backend = new PhysicsSystem3DTest.FakeBackend();
        physics = new PhysicsSystem3D(scene, backend);
    }

    private Entity body(String name, RigidBody3DComponent.MotionType motion) {
        Entity entity = scene.createEntity(name);
        entity.addComponent(new RigidBody3DComponent(motion));
        entity.addComponent(new BoxCollider3DComponent());
        return entity;
    }

    private List<String> forceCalls() {
        return backend.calls.stream().filter(call -> call.startsWith("force")).toList();
    }

    // --- Scene hook ---

    @Test
    void shouldGiveScriptsTheScenesPhysicsWhileItRunsAndNullAfterwards() {
        Entity entity = scene.createEntity("Scripted");
        Recorder script = new Recorder();
        entity.addComponent(script);

        assertSame(physics, scene.getPhysics3D());
        assertSame(physics, script.getPhysics3D());
        assertSame(physics, script.requirePhysics3D());

        physics.dispose();

        assertNull(scene.getPhysics3D());
        assertNull(script.getPhysics3D());
        IllegalStateException error = assertThrows(IllegalStateException.class, script::requirePhysics3D);
        assertTrue(error.getMessage().contains("3D collider"), "the message says what to add: " + error.getMessage());
    }

    @Test
    void shouldRefuseASecondPhysicsSystemOnTheSameScene() {
        PhysicsSystem3DTest.FakeBackend other = new PhysicsSystem3DTest.FakeBackend();

        assertThrows(IllegalStateException.class, () -> new PhysicsSystem3D(scene, other));

        assertSame(physics, scene.getPhysics3D(), "the running system stays attached");
    }

    @Test
    void shouldAllowANewSystemAfterTheOldOneIsDisposed() {
        physics.dispose();
        PhysicsSystem3D next = new PhysicsSystem3D(scene, new PhysicsSystem3DTest.FakeBackend());

        assertSame(next, scene.getPhysics3D());
        physics.dispose();
        assertSame(next, scene.getPhysics3D(), "disposing the old system twice must not detach the new one");
    }

    // --- Contact callbacks ---

    @Test
    void shouldCallTheCollisionCallbacksOnBothEntitiesWithTheOtherOne() {
        Entity a = body("A", RigidBody3DComponent.MotionType.DYNAMIC);
        Entity b = body("B", RigidBody3DComponent.MotionType.STATIC);
        Recorder first = new Recorder();
        Recorder second = new Recorder();
        a.addComponent(first);
        b.addComponent(second);
        physics.update(0f);
        List<Integer> handles = List.copyOf(backend.bodies.keySet());

        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), true));
        physics.update(0f);
        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), false));
        physics.update(0f);

        assertEquals(List.of("collisionEnter:B", "collisionExit:B"), first.calls);
        assertEquals(List.of("collisionEnter:A", "collisionExit:A"), second.calls);
    }

    @Test
    void shouldCallTheTriggerCallbacksForSensorContacts() {
        Entity mover = body("Mover", RigidBody3DComponent.MotionType.DYNAMIC);
        Entity zone = scene.createEntity("Zone");
        SphereCollider3DComponent trigger = new SphereCollider3DComponent();
        trigger.setTrigger(true);
        zone.addComponent(trigger);
        Recorder zoneScript = new Recorder();
        zone.addComponent(zoneScript);
        Recorder moverScript = new Recorder();
        mover.addComponent(moverScript);
        physics.update(0f);
        List<Integer> handles = List.copyOf(backend.bodies.keySet());

        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), true));
        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), false));
        physics.update(0f);

        assertEquals(List.of("triggerEnter:Zone", "triggerExit:Zone"), moverScript.calls);
        assertEquals(List.of("triggerEnter:Mover", "triggerExit:Mover"), zoneScript.calls);
    }

    @Test
    void shouldSkipDisabledScriptsAndInactiveEntities() {
        Entity a = body("A", RigidBody3DComponent.MotionType.DYNAMIC);
        Entity b = body("B", RigidBody3DComponent.MotionType.STATIC);
        Recorder disabled = new Recorder();
        a.addComponent(disabled);
        disabled.setEnabled(false);
        Recorder enabled = new Recorder();
        b.addComponent(enabled);
        physics.update(0f);
        List<Integer> handles = List.copyOf(backend.bodies.keySet());

        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), true));
        physics.update(0f);

        assertTrue(disabled.calls.isEmpty());
        assertEquals(List.of("collisionEnter:A"), enabled.calls);
    }

    @Test
    void shouldCallScriptsBoundThroughAnEntityScriptBindingAsSceneFilesDo() {
        Entity a = body("A", RigidBody3DComponent.MotionType.DYNAMIC);
        Entity b = body("B", RigidBody3DComponent.MotionType.STATIC);
        EntityScriptBindingComponent enabled = new EntityScriptBindingComponent("a.java", "A", ScriptLanguage.JAVA);
        Recorder bound = new Recorder();
        enabled.bindInstance(bound);
        a.addComponent(enabled);
        EntityScriptBindingComponent off = new EntityScriptBindingComponent("b.java", "B", ScriptLanguage.JAVA);
        Recorder switchedOff = new Recorder();
        off.bindInstance(switchedOff);
        b.addComponent(off);
        off.setEnabled(false);
        EntityScriptBindingComponent unbound = new EntityScriptBindingComponent("c.java", "C", ScriptLanguage.JAVA);
        b.addComponent(unbound);
        physics.update(0f);
        List<Integer> handles = List.copyOf(backend.bodies.keySet());

        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), true));
        physics.update(0f);

        assertEquals(List.of("collisionEnter:B"), bound.calls);
        assertTrue(switchedOff.calls.isEmpty(), "a disabled binding does not run its script");
    }

    @Test
    void shouldLetACallbackDestroyTheOtherEntityWithoutItsScriptBeingCalledOnADeadEntity() {
        Entity a = body("A", RigidBody3DComponent.MotionType.DYNAMIC);
        Entity b = body("B", RigidBody3DComponent.MotionType.STATIC);
        a.addComponent(new ScriptComponent() {
            @Override
            public void onCollisionEnter3D(Entity other) {
                getScene().removeEntity(other);
            }
        });
        Recorder second = new Recorder();
        b.addComponent(second);
        physics.update(0f);
        List<Integer> handles = List.copyOf(backend.bodies.keySet());

        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), true));
        physics.update(0f);

        assertNull(scene.findEntityById(b.getId()));
        assertTrue(second.calls.isEmpty(), "the removed entity's script is not called after its removal");
    }

    // --- Forces ---

    @Test
    void shouldApplyAForceOnEveryFixedStepOfTheNextUpdateAndThenDropIt() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);
        int handle = backend.solidHandle();

        physics.addForce(crate, new Vector3(0f, 1000f, 200f));
        physics.update(3 * PhysicsSystem3D.FIXED_TIMESTEP);
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);

        assertEquals(List.of("force " + handle + " 0.0 10.0 2.0", "force " + handle + " 0.0 10.0 2.0",
            "force " + handle + " 0.0 10.0 2.0"), forceCalls(), "1000 units/s2 is 10 m/s2 for each of the 3 steps, then nothing");
    }

    @Test
    void shouldKeepAForceUntilAStepHappens() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);

        physics.addForce(crate, new Vector3(100f, 0f, 0f));
        physics.update(0.001f);
        assertTrue(forceCalls().isEmpty(), "no step yet, so the force waits");
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);

        assertEquals(1, forceCalls().size());
    }

    @Test
    void shouldAddUpForcesAppliedInTheSameFrame() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);
        int handle = backend.solidHandle();

        physics.addForce(crate, new Vector3(100f, 0f, 0f));
        physics.addForce(crate, new Vector3(0f, 300f, 0f));
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);

        assertEquals(List.of("force " + handle + " 1.0 3.0 0.0"), forceCalls());
    }

    @Test
    void shouldPushForTheSameSimulatedTimeAtThirtyAndSixtyFramesPerSecond() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);
        int slowSteps = forcedSteps(crate, 30, 2 * PhysicsSystem3D.FIXED_TIMESTEP);
        backend.calls.clear();
        int fastSteps = forcedSteps(crate, 60, PhysicsSystem3D.FIXED_TIMESTEP);

        assertEquals(60, slowSteps, "one simulated second is 60 steps with the force");
        assertEquals(slowSteps, fastSteps, "a push that lasts a second moves the body the same at any frame rate");
    }

    /** Applies the force before each of {@code frames} frames of {@code frameSeconds} and counts the steps it acted in. */
    private int forcedSteps(Entity crate, int frames, float frameSeconds) {
        for (int i = 0; i < frames; i++) {
            physics.addForce(crate, new Vector3(0f, 100f, 0f));
            physics.update(frameSeconds);
        }
        return forceCalls().size();
    }

    @Test
    void shouldApplyTorquesInTheSameWayWithSquaredUnits() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);
        int handle = backend.solidHandle();

        physics.addTorque(crate, new Vector3(0f, 20000f, 0f));
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);

        assertEquals(List.of("torque " + handle + " 0.0 2.0 0.0"), backend.calls.stream().filter(c -> c.startsWith("torque")).toList(),
            "20000 kg unit2/s2 is 2 N m");
    }

    @Test
    void shouldDropAForceWhenTheBodyIsRemovedBeforeTheStep() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);

        physics.addForce(crate, new Vector3(100f, 0f, 0f));
        scene.removeEntity(crate);
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);

        assertTrue(forceCalls().isEmpty());
    }

    // --- Impulses and velocities ---

    @Test
    void shouldApplyAnImpulseAtOnceInMeters() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);
        int handle = backend.solidHandle();

        physics.addImpulse(crate, new Vector3(0f, 500f, 0f));

        assertEquals(List.of("impulse " + handle + " 0.0 5.0 0.0"), backend.calls);
    }

    @Test
    void shouldSetAndReadVelocitiesInSceneUnits() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);
        int handle = backend.solidHandle();

        physics.setLinearVelocity(crate, new Vector3(300f, 0f, -100f));
        physics.setAngularVelocity(crate, new Vector3(0f, 2f, 0f));

        assertEquals(new Vector3(3f, 0f, -1f), backend.linearVelocities.get(handle), "meters per second in the backend");
        assertEquals(new Vector3(300f, 0f, -100f), physics.getLinearVelocity(crate));
        assertEquals(new Vector3(0f, 2f, 0f), physics.getAngularVelocity(crate), "radians per second are not scaled");
    }

    @Test
    void shouldAllowKinematicVelocitiesButNotForces() {
        Entity platform = body("Platform", RigidBody3DComponent.MotionType.KINEMATIC);
        physics.update(0f);

        physics.setLinearVelocity(platform, new Vector3(100f, 0f, 0f));
        assertEquals(new Vector3(100f, 0f, 0f), physics.getLinearVelocity(platform));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> physics.addForce(platform, new Vector3(1f, 0f, 0f)));
        assertTrue(error.getMessage().contains("Platform") && error.getMessage().contains("KINEMATIC"), error.getMessage());
        assertThrows(IllegalStateException.class, () -> physics.addImpulse(platform, new Vector3(1f, 0f, 0f)));
    }

    @Test
    void shouldRefuseEverythingThatMovesAStaticBodyAndNameTheEntity() {
        Entity wall = body("Wall", RigidBody3DComponent.MotionType.STATIC);
        physics.update(0f);

        for (Runnable action : List.<Runnable>of(
            () -> physics.addForce(wall, new Vector3(1f, 0f, 0f)),
            () -> physics.addTorque(wall, new Vector3(1f, 0f, 0f)),
            () -> physics.addImpulse(wall, new Vector3(1f, 0f, 0f)),
            () -> physics.setLinearVelocity(wall, new Vector3(1f, 0f, 0f)),
            () -> physics.setAngularVelocity(wall, new Vector3(1f, 0f, 0f)))) {
            IllegalStateException error = assertThrows(IllegalStateException.class, action::run);
            assertTrue(error.getMessage().contains("Wall"), error.getMessage());
        }
        assertEquals(new Vector3(), physics.getLinearVelocity(wall), "a static body is not moving");
        assertTrue(backend.calls.isEmpty(), "nothing reached the backend");
    }

    @Test
    void shouldExplainWhyAnEntityWithoutABodyCannotBePushed() {
        physics.update(0f);
        Entity late = body("Late", RigidBody3DComponent.MotionType.DYNAMIC);
        Entity triggerOnly = scene.createEntity("Zone");
        SphereCollider3DComponent trigger = new SphereCollider3DComponent();
        trigger.setTrigger(true);
        triggerOnly.addComponent(trigger);
        Entity bare = scene.createEntity("Bare");

        String created = assertThrows(IllegalStateException.class, () -> physics.addImpulse(late, new Vector3(1f, 0f, 0f))).getMessage();
        assertTrue(created.contains("Late") && created.contains("next physics update"), created);
        physics.update(0f);
        physics.addImpulse(late, new Vector3(1f, 0f, 0f));

        String zone = assertThrows(IllegalStateException.class, () -> physics.addImpulse(triggerOnly, new Vector3(1f, 0f, 0f))).getMessage();
        assertTrue(zone.contains("only trigger colliders"), zone);
        String none = assertThrows(IllegalStateException.class, () -> physics.getLinearVelocity(bare)).getMessage();
        assertTrue(none.contains("Bare") && none.contains("collider"), none);
    }

    @Test
    void shouldRefuseNonFiniteVectorsAndNulls() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);

        assertThrows(IllegalArgumentException.class, () -> physics.addForce(crate, new Vector3(Float.NaN, 0f, 0f)));
        assertThrows(IllegalArgumentException.class, () -> physics.addImpulse(crate, new Vector3(0f, Float.POSITIVE_INFINITY, 0f)));
        assertThrows(IllegalArgumentException.class, () -> physics.setLinearVelocity(crate, new Vector3(0f, 0f, Float.NaN)));
        assertThrows(NullPointerException.class, () -> physics.addForce(crate, null));
        assertThrows(NullPointerException.class, () -> physics.addForce(null, new Vector3()));
        assertTrue(backend.calls.isEmpty());
    }

    @Test
    void shouldRefuseCallsAfterTheSystemStopped() {
        Entity crate = body("Crate", RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);
        physics.dispose();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> physics.addImpulse(crate, new Vector3(1f, 0f, 0f)));
        assertTrue(error.getMessage().contains("stopped"), error.getMessage());
    }

    // --- Queries ---

    @Test
    void shouldReturnTheEntitiesInsideASphereInSceneUnits() {
        Entity near = body("Near", RigidBody3DComponent.MotionType.STATIC);
        body("Far", RigidBody3DComponent.MotionType.STATIC);
        physics.update(0f);
        backend.overlaps.add(backend.bodies.keySet().iterator().next());
        backend.overlaps.add(backend.bodies.keySet().iterator().next());

        List<Entity> found = physics.overlapSphere(new Vector3(200f, 0f, 0f), 150f, PhysicsLayers3D.ALL_LAYERS);

        assertEquals(List.of(near), found, "each entity once");
        assertEquals(new Vector3(2f, 0f, 0f), backend.lastOverlapCenter);
        assertEquals(1.5f, backend.lastOverlapRadius, 1e-6f);
        assertThrows(IllegalArgumentException.class, () -> physics.overlapSphere(new Vector3(), 0f, 1));
        assertThrows(IllegalArgumentException.class, () -> physics.overlapSphere(new Vector3(Float.NaN, 0f, 0f), 1f, 1));
    }

    @Test
    void shouldRaycastFromAScriptAndReturnNullOnAMiss() {
        Entity target = body("Target", RigidBody3DComponent.MotionType.STATIC);
        Entity shooter = scene.createEntity("Shooter");
        Recorder script = new Recorder();
        shooter.addComponent(script);
        physics.update(0f);

        assertNull(script.raycast3D(new Vector3(), new Vector3(0f, 1f, 0f), 1000f));

        backend.nextHit = new PhysicsBackend3D.RayHit(backend.solidHandle(), new Vector3(0f, 2f, 0f), Vector3.Y, 2f);
        PhysicsSystem3D.RaycastHit hit = script.raycast3D(new Vector3(), new Vector3(0f, 1f, 0f), 1000f);
        assertSame(target, hit.entity());
        assertEquals(200f, hit.distance(), 1e-3f);

        physics.dispose();
        assertThrows(IllegalStateException.class, () -> script.raycast3D(new Vector3(), Vector3.Y, 10f));
    }
}
