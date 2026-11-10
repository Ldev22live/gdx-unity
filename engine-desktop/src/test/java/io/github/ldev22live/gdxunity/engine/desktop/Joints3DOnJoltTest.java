package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.physics.JoltPhysicsBackend3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent.MotorMode;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent.Type;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Joints (TD-051) against the real Jolt physics: a door that swings and stops, a pendulum with the period the formula gives,
 * a fixed joint under load, a slider driven by its motor, a rope, a chain of ball joints, and the lifetime and refusal rules.
 */
class Joints3DOnJoltTest {
    private static final float FRAME = 1f / 60f;

    private Scene scene;
    private PhysicsSystem3D physics;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Joints on Jolt");
        physics = new PhysicsSystem3D(scene, new JoltPhysicsBackend3D());
        Entity floor = scene.createEntity("Floor");
        floor.getTransform().setLocalPosition(0f, -50f, 0f);
        floor.addComponent(new BoxCollider3DComponent(new Vector3(8000f, 100f, 8000f)));
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

    private Entity box(String name, Vector3 position, Vector3 size, RigidBody3DComponent.MotionType motion) {
        Entity entity = scene.createEntity(name);
        entity.getTransform().setLocalPosition(position);
        entity.addComponent(new BoxCollider3DComponent(size));
        RigidBody3DComponent body = new RigidBody3DComponent(motion);
        entity.addComponent(body);
        return entity;
    }

    private static Joint3DComponent joint(Entity entity, Type type) {
        Joint3DComponent joint = new Joint3DComponent(type);
        entity.addComponent(joint);
        return joint;
    }

    private static Vector3 at(Entity entity) {
        return entity.getTransform().getWorldPosition();
    }

    private static Vector3 worldPoint(Entity entity, Vector3 local) {
        return new Vector3(local).mul(entity.getTransform().getWorldMatrix());
    }

    @Test
    void shouldSwingAHingeDoorAndStopItAtItsLimit() {
        Entity door = box("Door", new Vector3(50f, 110f, 0f), new Vector3(100f, 200f, 10f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent hinge = joint(door, Type.HINGE);
        hinge.setAnchor(new Vector3(-50f, 0f, 0f));
        hinge.setAxis(new Vector3(0f, 1f, 0f));
        hinge.setLimitsEnabled(true);
        hinge.setLimits(-10f, 80f);
        hinge.setMotorMode(MotorMode.VELOCITY);
        hinge.setMotorTarget(90f);
        run(5);

        assertTrue(physics.hasJoint(door), "the joint exists once physics has started");
        run(180);

        assertEquals(80f, physics.getJointPosition(door), 2f, "stopped at the 80 degree limit after three seconds");
        // The hinge side stays where the anchor is (0, 110, 0) however far the door has swung
        Vector3 hingeSide = worldPoint(door, new Vector3(-50f, 0f, 0f));
        assertEquals(0f, hingeSide.dst(new Vector3(0f, 110f, 0f)), 3f, "the anchor does not move");
    }

    @Test
    void shouldSwingAPendulumWithThePeriodOfTheFormula() {
        float length = 400f;
        float startDegrees = 10f;
        double start = Math.toRadians(startDegrees);
        Vector3 pivot = new Vector3(0f, 800f, 0f);
        Vector3 bobAt = new Vector3(pivot.x + length * (float) Math.sin(start), pivot.y - length * (float) Math.cos(start), 0f);
        Entity bob = scene.createEntity("Bob");
        bob.getTransform().setLocalPosition(bobAt);
        bob.addComponent(new SphereCollider3DComponent(20f));
        RigidBody3DComponent body = new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC);
        body.setLinearDamping(0f);
        body.setAngularDamping(0f);
        bob.addComponent(body);
        Joint3DComponent hinge = joint(bob, Type.HINGE);
        hinge.setAnchor(new Vector3(pivot).sub(bobAt));
        hinge.setAxis(new Vector3(0f, 0f, 1f));

        // x crosses zero going left at a quarter period and going right at three quarters: half a period apart
        double crossedLeft = Double.NaN;
        double crossedRight = Double.NaN;
        float previousX = at(bob).x;
        for (int frame = 1; frame <= 60 * 8 && Double.isNaN(crossedRight); frame++) {
            run(1);
            float x = at(bob).x;
            if (previousX > 0f && x <= 0f && Double.isNaN(crossedLeft)) {
                crossedLeft = (frame - 1 + previousX / (previousX - x)) * FRAME;
            } else if (previousX < 0f && x >= 0f && !Double.isNaN(crossedLeft)) {
                crossedRight = (frame - 1 + previousX / (previousX - x)) * FRAME;
            }
            previousX = x;
        }

        // A sphere on a rod: the physical pendulum's effective length is L + (2/5) r^2 / L; the 10 degree swing adds theta^2 / 16
        double meters = length / 100.0;
        double radius = 0.2;
        double effective = meters + 0.4 * radius * radius / meters;
        double expected = 2 * Math.PI * Math.sqrt(effective / 9.81) * (1 + start * start / 16);
        assertFalse(Double.isNaN(crossedRight), "the pendulum swung through both ways");
        double period = 2 * (crossedRight - crossedLeft);
        assertEquals(expected, period, expected * 0.05, "period within 5% of the formula");
    }

    @Test
    void shouldHoldABoxAndItsLoadWithFixedJointsInTheAir() {
        Entity anchor = box("Anchor", new Vector3(0f, 400f, 0f), new Vector3(60f, 60f, 60f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent toWorld = joint(anchor, Type.FIXED);
        toWorld.setAnchor(new Vector3());
        Entity load = box("Load", new Vector3(130f, 400f, 0f), new Vector3(60f, 60f, 60f), RigidBody3DComponent.MotionType.DYNAMIC);
        ((RigidBody3DComponent) load.getComponent(RigidBody3DComponent.class)).setMass(20f);
        Joint3DComponent toAnchor = joint(load, Type.FIXED);
        toAnchor.setConnectedName("Anchor");
        toAnchor.setAnchor(new Vector3(-65f, 0f, 0f));

        run(180);

        assertEquals(0f, at(anchor).dst(new Vector3(0f, 400f, 0f)), 2f, "held by the world joint, not falling");
        assertEquals(0f, at(load).dst(new Vector3(130f, 400f, 0f)), 15f, "the load hangs off the side; Jolt's solver lets a 20 kg load sag about 10 units");
    }

    @Test
    void shouldDriveASliderToItsTargetPositionAndStopItAtItsLimit() {
        Entity platform = box("Platform", new Vector3(0f, 200f, 0f), new Vector3(200f, 20f, 200f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent slider = joint(platform, Type.SLIDER);
        slider.setAxis(new Vector3(1f, 0f, 0f));
        slider.setLimitsEnabled(true);
        slider.setLimits(0f, 300f);
        slider.setMotorMode(MotorMode.POSITION);
        slider.setMotorTarget(200f);
        slider.setMotorMaxForce(100000f);

        run(240);

        assertEquals(200f, physics.getJointPosition(platform), 6f, "moved to the motor's target");
        assertEquals(200f, at(platform).x, 6f);
        assertEquals(200f, at(platform).y, 3f, "does not sag while it slides");

        physics.setJointMotor(platform, MotorMode.VELOCITY, 500f);
        run(240);
        assertEquals(300f, physics.getJointPosition(platform), 6f, "stopped by the upper limit");
        physics.setJointMotor(platform, MotorMode.OFF, 0f);
        run(30);
        assertEquals(300f, physics.getJointPosition(platform), 6f, "motor off leaves it where it is (no gravity along the axis)");
    }

    @Test
    void shouldLetARopeFallUntilItIsTautAndNoFurther() {
        Entity bob = scene.createEntity("Bob");
        bob.getTransform().setLocalPosition(0f, 800f, 0f);
        bob.addComponent(new SphereCollider3DComponent(20f));
        bob.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
        Joint3DComponent rope = joint(bob, Type.DISTANCE);
        rope.setAnchor(new Vector3());
        rope.setConnectedAnchor(new Vector3(0f, 1000f, 0f));
        rope.setLimitsEnabled(true);
        rope.setLimits(0f, 400f);

        run(10);
        assertTrue(at(bob).y < 800f, "falls freely while the rope is slack");
        run(240);

        float distance = at(bob).dst(new Vector3(0f, 1000f, 0f));
        assertEquals(400f, distance, 6f, "hangs at the rope's length");
        assertEquals(600f, at(bob).y, 8f);
    }

    @Test
    void shouldKeepTheEndsOfABallJointChainTogetherWhileItSwings() {
        Entity first = box("Link1", new Vector3(0f, 600f, 0f), new Vector3(20f, 200f, 20f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent top = joint(first, Type.BALL);
        top.setAnchor(new Vector3(0f, 100f, 0f));
        Entity second = box("Link2", new Vector3(0f, 400f, 0f), new Vector3(20f, 200f, 20f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent middle = joint(second, Type.BALL);
        middle.setConnectedName("Link1");
        middle.setAnchor(new Vector3(0f, 100f, 0f));
        run(3);
        physics.setLinearVelocity(second, new Vector3(300f, 0f, 0f));

        float worstTop = 0f;
        float worstMiddle = 0f;
        for (int frame = 0; frame < 240; frame++) {
            run(1);
            worstTop = Math.max(worstTop, worldPoint(first, new Vector3(0f, 100f, 0f)).dst(new Vector3(0f, 700f, 0f)));
            worstMiddle = Math.max(worstMiddle,
                worldPoint(first, new Vector3(0f, -100f, 0f)).dst(worldPoint(second, new Vector3(0f, 100f, 0f))));
        }

        assertTrue(Math.abs(at(second).x) > 20f || Math.abs(at(first).x) > 5f, "the chain moved: " + at(first) + " " + at(second));
        assertTrue(worstTop < 4f, "the top link stayed on its pivot, worst gap " + worstTop);
        assertTrue(worstMiddle < 4f, "the links stayed joined, worst gap " + worstMiddle);
    }

    @Test
    void shouldLetAScriptDriveAndReadItsOwnJoint() {
        Entity platform = box("Platform", new Vector3(0f, 200f, 0f), new Vector3(200f, 20f, 200f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent slider = joint(platform, Type.SLIDER);
        slider.setAxis(new Vector3(1f, 0f, 0f));
        slider.setMotorMaxForce(100000f);
        ScriptComponent script = new ScriptComponent() { };
        platform.addComponent(script);
        run(3);

        script.setJointMotor3D(MotorMode.VELOCITY, 100f);
        run(60);

        assertEquals(100f, script.getJointPosition3D(), 8f, "a script moved the platform 100 units a second for a second");
    }

    @Test
    void shouldNotCollideJoinedBodiesUnlessAskedTo() {
        float[] settled = new float[2];
        for (int round = 0; round < 2; round++) {
            Entity held = box("Held" + round, new Vector3(0f, 300f + round * 2000f, 0f), new Vector3(100f, 100f, 100f),
                RigidBody3DComponent.MotionType.DYNAMIC);
            joint(held, Type.FIXED);
            Entity swinger = box("Swinger" + round, new Vector3(10f, 300f + round * 2000f, 0f), new Vector3(100f, 100f, 100f),
                RigidBody3DComponent.MotionType.DYNAMIC);
            RigidBody3DComponent swingerBody = swinger.getComponent(RigidBody3DComponent.class);
            swingerBody.setLinearDamping(3f); // so it settles instead of swinging for ever
            swingerBody.setAngularDamping(3f);
            Joint3DComponent ball = joint(swinger, Type.BALL);
            ball.setConnectedName("Held" + round);
            ball.setAnchor(new Vector3(-10f, 0f, 0f));
            ball.setCollideConnected(round == 1);
            run(400);
            settled[round] = at(swinger).y - (300f + round * 2000f);
        }

        assertEquals(-10f, settled[0], 3f, "two overlapping joined boxes hang from the ball joint without pushing each other away");
        assertTrue(Math.abs(settled[1] - settled[0]) > 5f, "with collideConnected they push each other: " + settled[1]);
    }

    @Test
    void shouldRemoveAJointWhileAnEntityIsInactiveAndMakeItAgain() {
        Entity hanging = box("Hanging", new Vector3(100f, 400f, 0f), new Vector3(60f, 60f, 60f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent toWorld = joint(hanging, Type.BALL);
        toWorld.setAnchor(new Vector3(0f, 30f, 0f));
        Entity hook = box("Hook", new Vector3(100f, 700f, 0f), new Vector3(20f, 20f, 20f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent hold = joint(hook, Type.FIXED);
        hold.setConnectedName("Hanging");
        run(5);
        assertTrue(physics.hasJoint(hanging));
        assertTrue(physics.hasJoint(hook));

        hanging.setActive(false);
        run(2);
        assertFalse(physics.hasJoint(hanging), "no body, no joint");
        assertFalse(physics.hasJoint(hook), "the joint to an inactive entity goes too");
        run(60);
        assertTrue(at(hook).y < 600f, "the hook falls once it is let go: " + at(hook));

        hanging.setActive(true);
        run(2);
        assertTrue(physics.hasJoint(hanging), "made again when the entity is back");
        assertTrue(physics.hasJoint(hook));

        toWorld.setEnabled(false);
        run(2);
        assertFalse(physics.hasJoint(hanging), "a disabled component has no joint");
    }

    @Test
    void shouldRefuseJointsThatCannotWorkAndNameTheEntity() {
        Entity lonely = box("Lonely", new Vector3(0f, 300f, 0f), new Vector3(50f, 50f, 50f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent joint = joint(lonely, Type.HINGE);
        joint.setConnectedName("Nobody");
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));
        assertTrue(missing.getMessage().contains("Lonely") && missing.getMessage().contains("Nobody"), missing.getMessage());

        lonely.removeComponent(joint);
        Entity wall = box("Wall", new Vector3(500f, 100f, 0f), new Vector3(50f, 200f, 50f), RigidBody3DComponent.MotionType.STATIC);
        Entity post = box("Post", new Vector3(700f, 100f, 0f), new Vector3(50f, 200f, 50f), RigidBody3DComponent.MotionType.KINEMATIC);
        joint(wall, Type.FIXED).setConnectedName("Post");
        IllegalStateException notDynamic = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));
        assertTrue(notDynamic.getMessage().contains("Wall") && notDynamic.getMessage().contains("Post")
            && notDynamic.getMessage().contains("dynamic"), notDynamic.getMessage());
    }

    @Test
    void shouldRefuseABadHingeMotorOrCharacterAndTriggerOnlyBodies() {
        Entity hinge = box("Hinge", new Vector3(0f, 300f, 0f), new Vector3(50f, 50f, 50f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent badLimits = joint(hinge, Type.HINGE);
        badLimits.setLimitsEnabled(true);
        badLimits.setLimits(10f, 50f);
        IllegalStateException limits = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));
        assertTrue(limits.getMessage().contains("Hinge") && limits.getMessage().contains("include 0"), limits.getMessage());
        hinge.removeComponent(badLimits);

        Joint3DComponent ball = joint(hinge, Type.BALL);
        ball.setMotorMode(MotorMode.VELOCITY);
        IllegalStateException motor = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));
        assertTrue(motor.getMessage().contains("Hinge") && motor.getMessage().contains("motor"), motor.getMessage());
        ball.setMotorMode(MotorMode.OFF);

        run(2);
        assertTrue(physics.hasJoint(hinge));
        IllegalStateException noMotor = assertThrows(IllegalStateException.class,
            () -> physics.setJointMotor(hinge, MotorMode.VELOCITY, 10f));
        assertTrue(noMotor.getMessage().contains("BALL"), noMotor.getMessage());
        IllegalStateException noJoint = assertThrows(IllegalStateException.class,
            () -> physics.setJointMotor(scene.findEntityByName("Floor"), MotorMode.OFF, 0f));
        assertTrue(noJoint.getMessage().contains("Floor") && noJoint.getMessage().contains("no joint"), noJoint.getMessage());
        assertThrows(IllegalArgumentException.class, () -> physics.setJointMotor(hinge, MotorMode.OFF, Float.NaN));
    }

    @Test
    void shouldRefuseToJoinACharacter() {
        Entity hero = scene.createEntity("Hero");
        hero.getTransform().setLocalPosition(0f, 70f, 0f);
        hero.addComponent(new CharacterController3DComponent());
        Entity crate = box("Crate", new Vector3(200f, 300f, 0f), new Vector3(50f, 50f, 50f), RigidBody3DComponent.MotionType.DYNAMIC);
        Joint3DComponent joint = joint(crate, Type.BALL);
        joint.setConnectedName("Hero");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));

        assertTrue(error.getMessage().contains("Hero") && error.getMessage().contains("character"), error.getMessage());
    }
}
