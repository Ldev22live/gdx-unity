package io.github.ldev22live.gdxunity.engine.scene.scripting;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scripts get 2D collision and sensor callbacks from the physics system, on both entities of a contact. */
class ScriptCollision2DTest {
    private static final float FRAME = 1f / 60f;

    /** Writes down every 2D contact callback it receives. */
    static final class Listening extends ScriptComponent {
        final List<String> heard = new ArrayList<>();

        @Override
        public void onCollisionEnter2D(Entity other) {
            heard.add("collision enter " + other.getName());
        }

        @Override
        public void onCollisionExit2D(Entity other) {
            heard.add("collision exit " + other.getName());
        }

        @Override
        public void onTriggerEnter2D(Entity other) {
            heard.add("trigger enter " + other.getName());
        }

        @Override
        public void onTriggerExit2D(Entity other) {
            heard.add("trigger exit " + other.getName());
        }
    }

    private PhysicsSystem2D physics;

    @AfterEach
    void tearDown() {
        if (physics != null) {
            physics.dispose();
        }
    }

    private static Entity ground(Scene scene) {
        Entity ground = scene.createEntity("Ground");
        ground.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.STATIC));
        ground.addComponent(new BoxCollider2DComponent(500f, 20f));
        return ground;
    }

    private static Entity faller(Scene scene, float y) {
        Entity ball = scene.createEntity("Faller");
        ball.getTransform().setLocalPosition(0f, y, 0f);
        ball.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC));
        ball.addComponent(new BoxCollider2DComponent(20f, 20f));
        return ball;
    }

    private void run(float seconds) {
        for (int i = 0; i < Math.round(seconds / FRAME); i++) {
            physics.update(FRAME);
        }
    }

    @Test
    void shouldTellBothEntitiesWhenADynamicBoxLandsOnTheGround() {
        Scene scene = new Scene("Level");
        Entity ground = ground(scene);
        Entity faller = faller(scene, 80f);
        Listening onGround = ground.addComponent(new Listening());
        Listening onFaller = faller.addComponent(new Listening());
        physics = new PhysicsSystem2D(scene);

        run(1.5f);

        assertTrue(onGround.heard.contains("collision enter Faller"), onGround.heard.toString());
        assertTrue(onFaller.heard.contains("collision enter Ground"), onFaller.heard.toString());
        assertEquals(1, onFaller.heard.stream().filter(line -> line.startsWith("collision enter")).count(), "reported once");
    }

    @Test
    void shouldCallTheScriptOfAnEntityBoundThroughAScriptBindingToo() {
        Scene scene = new Scene("Level");
        ground(scene);
        Entity faller = faller(scene, 80f);
        EntityScriptBindingComponent binding = new EntityScriptBindingComponent("scripts/Listening.java", "Listening", ScriptLanguage.JAVA);
        Listening script = new Listening();
        faller.addComponent(binding);
        binding.bindInstance(script);
        physics = new PhysicsSystem2D(scene);

        run(1.5f);

        assertTrue(script.heard.contains("collision enter Ground"), script.heard.toString());
    }

    @Test
    void shouldReportASensorOverlapAsATriggerNotACollision() {
        Scene scene = new Scene("Level");
        Entity zone = scene.createEntity("Zone");
        zone.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.STATIC));
        BoxCollider2DComponent sensor = new BoxCollider2DComponent(200f, 200f);
        sensor.setSensor(true);
        zone.addComponent(sensor);
        Entity faller = faller(scene, 300f);
        Listening inZone = zone.addComponent(new Listening());
        Listening onFaller = faller.addComponent(new Listening());
        physics = new PhysicsSystem2D(scene);

        run(1.2f);

        assertTrue(inZone.heard.contains("trigger enter Faller"), inZone.heard.toString());
        assertTrue(onFaller.heard.contains("trigger enter Zone"), onFaller.heard.toString());
        assertTrue(inZone.heard.stream().noneMatch(line -> line.startsWith("collision")), "a sensor is not solid: " + inZone.heard);
    }

    @Test
    void shouldNotCallADisabledScriptOrOneOnAnInactiveEntity() {
        Scene scene = new Scene("Level");
        Entity ground = ground(scene);
        Entity faller = faller(scene, 80f);
        Listening off = ground.addComponent(new Listening());
        off.setEnabled(false);
        Listening on = faller.addComponent(new Listening());
        physics = new PhysicsSystem2D(scene);

        run(1.5f);

        assertTrue(off.heard.isEmpty(), "disabled: " + off.heard);
        assertTrue(on.heard.contains("collision enter Ground"), on.heard.toString());
    }
}
