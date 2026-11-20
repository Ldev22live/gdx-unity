package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.GameTestProfile;
import io.github.ldev22live.gdxunity.engine.scene.play.InputMapping;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Action RPG + 3D's walking directions and turning (TD-088, roadmap M3c.1); walking on real Jolt is ActionRpgValleyProfileOnJoltTest. */
class GroundWalkMoverTest {
    private static final float EPSILON = 1e-4f;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static void assertDirection(float x, float z, Vector3 actual) {
        assertEquals(x, actual.x, EPSILON, "x of " + actual);
        assertEquals(0f, actual.y, EPSILON, "y of " + actual);
        assertEquals(z, actual.z, EPSILON, "z of " + actual);
    }

    @Test
    void shouldWalkRelativeToTheWayTheCameraLooks() {
        Vector3 out = new Vector3();
        // A camera with yaw 0 looks along -Z: forward walks away from it, right walks along +X
        assertDirection(0f, -1f, GroundWalkMover.walkDirection(0f, 0f, 1f, out));
        assertDirection(1f, 0f, GroundWalkMover.walkDirection(0f, 1f, 0f, out));
        assertDirection(0f, 1f, GroundWalkMover.walkDirection(0f, 0f, -1f, out));
        // Turned 90 degrees left, the camera looks along -X: forward walks along -X and right along -Z
        assertDirection(-1f, 0f, GroundWalkMover.walkDirection(90f, 0f, 1f, out));
        assertDirection(0f, -1f, GroundWalkMover.walkDirection(90f, 1f, 0f, out));
        float diagonal = (float) Math.sqrt(0.5);
        assertDirection(diagonal, -diagonal, GroundWalkMover.walkDirection(0f, 1f, 1f, out));
        assertEquals(0f, GroundWalkMover.walkDirection(30f, 0f, 0f, out).len(), EPSILON, "no key: no movement");
        assertEquals(0f, GroundWalkMover.walkDirection(30f, 1f, 0f, out).len() - 1f, EPSILON, "always unit length");
    }

    @Test
    void shouldFaceWhereItWalksTurningTheShortWayAtItsRate() {
        assertEquals(0f, CameraRigComponent.yawOf(GroundWalkMover.walkDirection(0f, 0f, 1f, new Vector3())), EPSILON);
        assertEquals(-90f, CameraRigComponent.yawOf(GroundWalkMover.walkDirection(0f, 1f, 0f, new Vector3())), EPSILON,
            "walking right faces yaw -90");
        assertEquals(-12f, GroundWalkMover.turnToward(0f, -90f, 12f), EPSILON, "at most the step");
        assertEquals(-90f, GroundWalkMover.turnToward(-85f, -90f, 12f), EPSILON, "arrives without overshooting");
        assertEquals(-178f, GroundWalkMover.turnToward(170f, -170f, 12f), EPSILON, "across 180, the short way");
    }

    @Test
    void shouldConfigureTheThirdPersonCameraFromTheProfile() {
        GameTestProfile profile = GameGenres.standard().support(GameGenres.ACTION_RPG,
            GameMode.MODE_3D).profile();
        ProfileCameraRig rig = new ProfileCameraRig(profile.camera(), "Hero", InputMapping.defaults(), key -> false);

        assertEquals(CameraRigComponent.Mode.ORBIT, rig.getMode());
        assertEquals(GameGenres.THIRD_PERSON_DISTANCE, rig.getDistance(), EPSILON);
        assertEquals(GameGenres.THIRD_PERSON_PIVOT_HEIGHT, rig.getPivotHeight(), EPSILON);
        assertEquals(-GameGenres.THIRD_PERSON_PITCH, rig.getPitch(), EPSILON, "tilted down");
        assertTrue(rig.isOcclusionEnabled(), "hills and walls pull the camera in");
        assertThrows(IllegalArgumentException.class, () -> new GroundWalkMover(GameGenres.standard().support(GameGenres.PLATFORMER,
            GameMode.MODE_2_5D).profile().movement(), InputMapping.defaults(), key -> false));
    }
}
