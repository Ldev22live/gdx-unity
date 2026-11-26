package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.InputSource;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Platformer + 2D (TD-105, roadmap M3c.7): the scene checks name what a 2D player lacks with a fix, and in Play the player runs,
 * jumps once per press from the ground, lands on the level and stays upright, while the camera follows without tilting.
 */
class Platformer2DProfileTest {
    private static final float STEP = 1f / 60f;
    /** The floor's top: a 2000-wide, 20-high floor centred at y 0. */
    private static final float FLOOR_TOP = 10f;
    private static final float PLAYER_HALF_HEIGHT = 24f;
    /** Box2D keeps two shapes apart by their skins: 2 × 0.01 m, 2 pixels at 100 pixels per metre. */
    private static final float BOX2D_SKINS = 2f;

    private final Set<Integer> held = new HashSet<>();
    private final InputSource input = held::contains;
    private Scene scene;
    private Entity player;
    private Entity camera;
    private PhysicsSystem2D physics;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Level");
        scene.setGameMode(GameMode.MODE_2D);
        scene.setGameGenre(GameGenres.PLATFORMER);
        Entity floor = scene.createEntity("Floor");
        floor.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.STATIC));
        floor.addComponent(new BoxCollider2DComponent(2000f, 20f));
        player = scene.createEntity("Player");
        player.getTransform().setLocalPosition(0f, FLOOR_TOP + PLAYER_HALF_HEIGHT + 2f, 0f);
        player.addComponent(new PlayerComponent());
        camera = scene.createEntity("Main Camera");
        camera.getTransform().setLocalPosition(0f, 0f, 10f);
        camera.addComponent(new CameraComponent(CameraComponent.Projection.ORTHOGRAPHIC));
    }

    @AfterEach
    void tearDown() {
        if (physics != null) {
            physics.dispose();
        }
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            physics.update(STEP);
            scene.update(STEP);
        }
    }

    @Test
    void shouldOfferTheTwoDPartsOneAtATimeAsFixes() {
        PlayProfiles profiles = new PlayProfiles(GameGenres.standard());
        var profile = profiles.resolve(scene).profile();
        assertNotNull(profile, "Platformer + 2D is playable");

        List<ProfileProblem> problems = profiles.validate(scene, profile);
        assertEquals(1, problems.size());
        assertEquals(ProfileFix.Kind.ADD_RIGID_BODY_2D, problems.get(0).fix().kind());

        player.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.KINEMATIC));
        problems = profiles.validate(scene, profile);
        assertTrue(problems.get(0).message().contains("needs Dynamic"), problems.get(0).message());

        player.getComponent(RigidBody2DComponent.class).setBodyType(RigidBody2DComponent.BodyType.DYNAMIC);
        problems = profiles.validate(scene, profile);
        assertEquals(ProfileFix.Kind.ADD_BOX_COLLIDER_2D, problems.get(0).fix().kind());

        player.addComponent(new BoxCollider2DComponent(32f, 48f));
        assertEquals(List.of(), profiles.validate(scene, profile));
    }

    @Test
    void shouldRunJumpOncePerPressLandAndStayUprightWithTheCameraFollowing() {
        player.addComponent(new RigidBody2DComponent(RigidBody2DComponent.BodyType.DYNAMIC)); // rotation not fixed: the mover fixes it
        player.addComponent(new BoxCollider2DComponent(32f, 48f));
        physics = new PhysicsSystem2D(scene);
        ProfileStart start = new PlayProfiles(GameGenres.standard()).start(scene, null, input);
        assertEquals(List.of(), start.problems());
        assertTrue(start.message().startsWith("Playing as Platformer / Side-scroller + 2D."), start.message());
        Run2DMover mover = player.getComponent(Run2DMover.class);
        assertNotNull(mover);
        assertInstanceOf(ProfileCameraRig.class, camera.getComponent(CameraRigComponent.class));

        run(30);
        float standing = player.getTransform().getWorldPosition().y;
        assertEquals(FLOOR_TOP + PLAYER_HALF_HEIGHT + BOX2D_SKINS, standing, 1f, "settled on the floor");

        held.add(Keys.D);
        run(60);
        float x = player.getTransform().getWorldPosition().x;
        assertEquals(GameGenres.PLATFORMER_2D_RUN_SPEED, x, 30f, "about a second of running right");

        held.add(Keys.SPACE);
        run(15);
        assertTrue(player.getTransform().getWorldPosition().y > standing + 60f, "jumped");
        run(90); // still holding Space: no second jump on landing
        assertEquals(1, mover.getJumps());
        assertEquals(standing, player.getTransform().getWorldPosition().y, 1f, "landed on the floor");
        assertEquals(0f, physics.getBody(player).getAngle(), 1e-4f, "upright");

        Vector3 view = camera.getTransform().getWorldPosition();
        // A following camera trails a running player by about speed / follow rate (300 / 6 = 50 pixels)
        assertEquals(player.getTransform().getWorldPosition().x, view.x,
            GameGenres.PLATFORMER_2D_RUN_SPEED / GameGenres.SIDE_CAMERA_FOLLOW_RATE + 10f, "the camera followed across the screen");
        assertEquals(0f, camera.getTransform().getLocalEulerAngles().x, 0.01f, "and never tilts");
        assertEquals(0f, camera.getTransform().getLocalEulerAngles().y, 0.01f);

        float turnedAt = player.getTransform().getWorldPosition().x;
        held.clear();
        held.add(Keys.A);
        run(30);
        assertTrue(player.getTransform().getWorldPosition().x < turnedAt - 100f, "runs back left");
    }
}
