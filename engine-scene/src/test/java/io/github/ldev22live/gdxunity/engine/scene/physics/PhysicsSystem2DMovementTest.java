package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Moving a 2D body in scene units and finding the ground under it (TD-105), for the Platformer + 2D profile. */
class PhysicsSystem2DMovementTest {
    private static final float STEP = 1f / 60f;

    private Scene scene;
    private PhysicsSystem2D physics;
    private Entity player;

    /** A 600-wide floor whose top is at y 10, and a 32 × 48 player standing just above it at x 0. */
    @BeforeEach
    void setUp() {
        scene = new Scene("Level");
        Entity floor = scene.createEntity("Floor");
        floor.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.STATIC));
        floor.addComponent(new BoxCollider2DComponent(600f, 20f));
        player = scene.createEntity("Player");
        player.getTransform().setLocalPosition(0f, 36f, 0f);
        RigidBody2DComponent body = new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC);
        body.setFixedRotation(true);
        player.addComponent(body);
        player.addComponent(new BoxCollider2DComponent(32f, 48f));
        physics = new PhysicsSystem2D(scene);
    }

    @AfterEach
    void tearDown() {
        physics.dispose();
    }

    private void run(int steps) {
        for (int i = 0; i < steps; i++) {
            physics.update(STEP);
        }
    }

    @Test
    void shouldBeTheScenesTwoDPhysicsUntilDisposed() {
        assertSame(physics, scene.getPhysics2D());
        physics.dispose();
        assertNull(scene.getPhysics2D());
        physics = new PhysicsSystem2D(scene); // for tearDown
    }

    @Test
    void shouldFindTheGroundUnderAStandingBodyAndNotUnderAFallingOne() {
        run(30);
        assertTrue(physics.isGrounded(player), "resting on the floor");
        // Box2D keeps shapes apart by their skins (2 × 0.01 m, 2 pixels at 100 pixels per metre)
        assertEquals(10f + 24f + 2f, player.getTransform().getLocalPosition().y, 1f, "half its height and the skins above the floor");

        player.getTransform().setLocalPosition(0f, 300f, 0f);
        physics.getBody(player).setTransform(0f, 3f, 0f);
        assertFalse(physics.isGrounded(player), "high above the floor");
    }

    @Test
    void shouldSetAndReadVelocityInSceneUnits() {
        run(30);
        physics.setLinearVelocity(player, 300f, 0f);
        assertEquals(300f, physics.getLinearVelocity(player, new Vector2()).x, 0.01f);
        run(60);
        // Set once, then the floor's friction slows the sliding body: less than 300 pixels in that second, but well on its way
        float x = player.getTransform().getLocalPosition().x;
        assertTrue(x > 150f && x < 300f, "slid right, slowed by friction: " + x);

        physics.setLinearVelocity(player, 0f, 650f);
        run(10);
        assertTrue(player.getTransform().getLocalPosition().y > 60f, "a jump lifts it");
        assertFalse(physics.isGrounded(player));
    }

    @Test
    void shouldRefuseAnEntityWithoutABodyAndANonFiniteVelocity() {
        Entity ghost = scene.createEntity("Ghost");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> physics.isGrounded(ghost));
        assertTrue(e.getMessage().contains("Ghost"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> physics.setLinearVelocity(player, Float.NaN, 0f));
    }
}
