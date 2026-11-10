package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.physics.JoltPhysicsBackend3D;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camera rigs (TD-050) against the real Jolt physics: a rig follows a character that walks, and an orbit camera is pulled in
 * front of a wall and let out again, ignoring the character it orbits.
 */
class CameraRigOnJoltTest {
    private static final float FRAME = 1f / 60f;
    /** Height of the standing character's center: half of 80 + 2 * 30. */
    private static final float STANDING_Y = 70f;

    private Scene scene;
    private PhysicsSystem3D physics;
    private Entity hero;
    private Entity cameraEntity;
    private CameraRigComponent rig;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Rig on Jolt");
        physics = new PhysicsSystem3D(scene, new JoltPhysicsBackend3D());
        Entity floor = scene.createEntity("Floor");
        floor.getTransform().setLocalPosition(0f, -50f, 0f);
        floor.addComponent(new BoxCollider3DComponent(new Vector3(4000f, 100f, 4000f)));
        hero = scene.createEntity("Hero");
        hero.getTransform().setLocalPosition(0f, STANDING_Y, 0f);
        hero.addComponent(new CharacterController3DComponent());
        cameraEntity = scene.createEntity("Camera");
        cameraEntity.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        rig = new CameraRigComponent();
        rig.setTargetName("Hero");
        cameraEntity.addComponent(rig);
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

    private Entity wall(float z) {
        Entity wall = scene.createEntity("Wall");
        wall.getTransform().setLocalPosition(0f, 200f, z);
        wall.addComponent(new BoxCollider3DComponent(new Vector3(2000f, 400f, 20f)));
        return wall;
    }

    @Test
    void shouldFollowACharacterThatWalksAwayAndKeepItsOffset() {
        rig.setFollowOffset(new Vector3(0f, 300f, 400f));
        rig.setFollowRate(8f);
        run(5);
        physics.moveCharacter(hero, new Vector3(200f, 0f, 0f));

        run(120);

        Vector3 camera = cameraEntity.getTransform().getWorldPosition();
        Vector3 heroPosition = hero.getTransform().getWorldPosition();
        assertTrue(heroPosition.x > 300f, "the hero walked: " + heroPosition);
        assertEquals(heroPosition.x, camera.x, 40f, "within a couple of frames of lag behind a walker");
        assertEquals(heroPosition.y + 300f, camera.y, 5f);
        assertEquals(heroPosition.z + 400f, camera.z, 5f);
    }

    @Test
    void shouldPullAnOrbitCameraInFrontOfAWallAndLetItOutAgain() {
        rig.setMode(CameraRigComponent.Mode.ORBIT);
        rig.setPivotHeight(0f);
        rig.setPitch(0f);
        rig.setYaw(0f);
        rig.setDistance(400f);
        rig.setOcclusionEnabled(true);
        rig.setOcclusionMargin(20f);
        Entity wall = wall(200f);
        run(10);

        // The wall's near face is at z = 190; the camera stops 20 units short of it. The ray starts inside the hero's capsule.
        assertEquals(170f, cameraEntity.getTransform().getWorldPosition().z, 4f, "pulled in front of the wall");

        wall.setActive(false);
        run(5);
        assertEquals(400f, cameraEntity.getTransform().getWorldPosition().z, 1f, "released when the wall is gone");

        rig.setOcclusionEnabled(false);
        wall.setActive(true);
        run(5);
        assertEquals(400f, cameraEntity.getTransform().getWorldPosition().z, 1f, "occlusion off ignores walls");
    }

    @Test
    void shouldNotBePulledInByTheCharacterItOrbits() {
        rig.setMode(CameraRigComponent.Mode.ORBIT);
        rig.setPivotHeight(0f);
        rig.setPitch(0f);
        rig.setDistance(400f);
        rig.setOcclusionEnabled(true);

        run(10);

        assertEquals(400f, cameraEntity.getTransform().getWorldPosition().z, 1f, "nothing but the hero is behind it");
    }
}
