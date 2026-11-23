package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.render.GdxTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent.Mode;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.CameraRigComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Camera rigs (TD-050) without physics: settings, serialization, and where each mode puts and turns the camera. */
class CameraRigComponentTest {
    private Scene scene;
    private Entity hero;
    private Entity cameraEntity;
    private CameraRigComponent rig;

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Rig");
        hero = scene.createEntity("Hero");
        hero.getTransform().setLocalPosition(100f, 0f, 200f);
        cameraEntity = scene.createEntity("Camera");
        rig = new CameraRigComponent();
        rig.setTargetName("Hero");
        cameraEntity.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        cameraEntity.addComponent(rig);
    }

    private Vector3 position() {
        return cameraEntity.getTransform().getWorldPosition();
    }

    private Vector3 looking() {
        return cameraEntity.getTransform().getWorldRotation().transform(new Vector3(0f, 0f, -1f));
    }

    @Test
    void shouldHoldSettingsInsideTheirLimitsAndRefuseBadValues() {
        rig.setPitchLimits(-30f, 40f);
        rig.setPitch(75f);
        assertEquals(40f, rig.getPitch());
        rig.rotate(0f, -200f);
        assertEquals(-30f, rig.getPitch());
        rig.setYaw(190f);
        assertEquals(-170f, rig.getYaw(), 1e-4f, "yaw wraps around");
        rig.setDistanceLimits(200f, 600f);
        rig.setDistance(10f);
        assertEquals(200f, rig.getDistance());
        rig.setDistance(5000f);
        assertEquals(600f, rig.getDistance());

        assertThrows(IllegalArgumentException.class, () -> rig.setPitchLimits(50f, 10f));
        assertThrows(IllegalArgumentException.class, () -> rig.setPitchLimits(-90f, 10f));
        assertThrows(IllegalArgumentException.class, () -> rig.setDistanceLimits(0f, 10f));
        assertThrows(IllegalArgumentException.class, () -> rig.setFollowRate(-1f));
        assertThrows(IllegalArgumentException.class, () -> rig.setYaw(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> rig.setOcclusionMargin(-1f));
    }

    @Test
    void shouldRoundTripEverySettingThroughTheSceneFile() {
        rig.setMode(Mode.ORBIT);
        rig.setFollowOffset(new Vector3(1f, 2f, 3f));
        rig.setFollowRate(2.5f);
        rig.setPivotHeight(90f);
        rig.setEyeHeight(60f);
        rig.setPitchLimits(-45f, 30f);
        rig.setDistanceLimits(150f, 900f);
        rig.setYaw(33f);
        rig.setPitch(-12f);
        rig.setDistance(500f);
        rig.setOcclusionEnabled(true);
        rig.setOcclusionMargin(35f);
        rig.setOcclusionMask(11);
        CameraRigComponentSerializer serializer = new CameraRigComponentSerializer();
        StringWriter out = new StringWriter();
        Json json = new Json(JsonWriter.OutputType.json);
        json.setWriter(out);
        json.writeObjectStart();
        serializer.serialize(rig, json);
        json.writeObjectEnd();

        CameraRigComponent copy = serializer.deserialize(new JsonReader().parse(out.toString()), new Json());

        assertEquals(Mode.ORBIT, copy.getMode());
        assertEquals("Hero", copy.getTargetName());
        assertEquals(new Vector3(1f, 2f, 3f), copy.getFollowOffset());
        assertEquals(2.5f, copy.getFollowRate());
        assertEquals(90f, copy.getPivotHeight());
        assertEquals(60f, copy.getEyeHeight());
        assertEquals(-45f, copy.getMinPitch());
        assertEquals(30f, copy.getMaxPitch());
        assertEquals(150f, copy.getMinDistance());
        assertEquals(900f, copy.getMaxDistance());
        assertEquals(33f, copy.getYaw(), 1e-4f);
        assertEquals(-12f, copy.getPitch());
        assertEquals(500f, copy.getDistance());
        assertTrue(copy.isOcclusionEnabled());
        assertEquals(35f, copy.getOcclusionMargin());
        assertEquals(11, copy.getOcclusionMask());
        assertThrows(SceneSerializationException.class,
            () -> serializer.deserialize(new JsonReader().parse("{\"mode\":\"FLY\"}"), new Json()));
    }

    @Test
    void shouldNameTheRigsEntityWhenTheTargetDoesNotExist() {
        rig.setTargetName("Nobody");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> scene.update(0.016f));

        assertTrue(error.getMessage().contains("Camera") && error.getMessage().contains("Nobody"), error.getMessage());
    }

    @Test
    void shouldStayIdleWithoutATargetName() {
        rig.setTargetName("");
        cameraEntity.getTransform().setLocalPosition(5f, 6f, 7f);

        for (int i = 0; i < 30; i++) {
            scene.update(1f / 60f);
        }

        assertEquals(new Vector3(5f, 6f, 7f), position());
    }

    @Test
    void shouldFollowAtTheOffsetAndLookAtTheTarget() {
        rig.setMode(Mode.FOLLOW);
        rig.setFollowOffset(new Vector3(0f, 400f, 500f));
        rig.setPivotHeight(50f);

        scene.update(1f / 60f);

        assertEquals(100f, position().x, 0.01f);
        assertEquals(400f, position().y, 0.01f, "the first frame snaps to the offset instead of swooping in");
        assertEquals(700f, position().z, 0.01f);
        Vector3 toLookAt = new Vector3(100f, 50f, 200f).sub(position()).nor();
        assertTrue(looking().dot(toLookAt) > 0.9999f, "looks at the target's look-at point: " + looking() + " vs " + toLookAt);
    }

    @Test
    void shouldCatchUpTheSameAtThirtyAndSixtyFramesPerSecond() {
        float[] after = new float[2];
        float[] frames = {1f / 60f, 1f / 30f};
        for (int i = 0; i < 2; i++) {
            Scene other = new Scene("Other" + i);
            Entity h = other.createEntity("Hero");
            Entity c = other.createEntity("Camera");
            CameraRigComponent r = new CameraRigComponent();
            r.setTargetName("Hero");
            r.setFollowOffset(new Vector3());
            r.setFollowRate(6f);
            c.addComponent(r);
            other.update(frames[i]);
            h.getTransform().setLocalPosition(1000f, 0f, 0f);
            for (float t = 0f; t < 0.5f - 1e-4f; t += frames[i]) {
                other.update(frames[i]);
            }
            after[i] = c.getTransform().getWorldPosition().x;
        }
        assertEquals(after[0], after[1], 1f, "same position after half a second at both frame rates");
        float expectedGap = 1000f * (float) Math.exp(-6f * 0.5f);
        assertEquals(expectedGap, 1000f - after[0], 5f, "closes the gap exponentially at the follow rate");
    }

    @Test
    void shouldFollowRigidlyAtRateZero() {
        rig.setFollowRate(0f);
        rig.setFollowOffset(new Vector3());
        scene.update(1f / 60f);
        hero.getTransform().setLocalPosition(900f, 0f, 0f);

        scene.update(1f / 60f);

        assertEquals(900f, position().x, 0.01f);
    }

    @Test
    void shouldOrbitAtTheDistanceInTheDirectionOppositeToTheView() {
        rig.setMode(Mode.ORBIT);
        rig.setPivotHeight(100f);
        rig.setDistance(400f);
        rig.setYaw(90f);
        rig.setPitch(0f);

        scene.update(1f / 60f);

        // Yaw 90 looks along -X, so the camera sits on the +X side of the pivot (100, 100, 200)
        assertEquals(500f, position().x, 0.05f);
        assertEquals(100f, position().y, 0.05f);
        assertEquals(200f, position().z, 0.05f);
        assertEquals(-1f, looking().x, 1e-3f);

        rig.setYaw(0f);
        rig.setPitch(-30f);
        scene.update(1f / 60f);
        Vector3 pivot = new Vector3(100f, 100f, 200f);
        assertEquals(400f, position().dst(pivot), 0.1f, "distance is kept");
        assertTrue(position().y > pivot.y, "looking down means sitting above the pivot");
        assertTrue(looking().dot(new Vector3(pivot).sub(position()).nor()) > 0.9999f, "always looks at the pivot");
    }

    @Test
    void shouldSitAtTheEyesOfTheTargetAndFaceWhereItFaces() {
        rig.setMode(Mode.FIRST_PERSON);
        rig.setEyeHeight(70f);
        hero.getTransform().setLocalEulerAngles(90f, 0f, 0f);
        rig.setPitch(20f);

        scene.update(1f / 60f);

        assertEquals(0f, new Vector3(100f, 70f, 200f).dst(position()), 0.01f);
        Vector3 heroFacing = hero.getTransform().getWorldRotation().transform(new Vector3(0f, 0f, -1f));
        Vector3 flat = new Vector3(looking().x, 0f, looking().z).nor();
        assertTrue(flat.dot(heroFacing) > 0.9999f, "same horizontal direction as the target: " + flat + " vs " + heroFacing);
        assertEquals(20f, CameraRigComponent.pitchOf(looking()), 0.01f);

        rig.setYaw(180f);
        scene.update(1f / 60f);
        Vector3 turnedAround = new Vector3(looking().x, 0f, looking().z).nor();
        assertTrue(turnedAround.dot(heroFacing) < -0.999f, "yaw turns away from the target's facing");
    }
}
