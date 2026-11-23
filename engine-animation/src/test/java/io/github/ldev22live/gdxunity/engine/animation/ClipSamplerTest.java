package io.github.ldev22live.gdxunity.engine.animation;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipSamplerTest {

    private final AnimatedProperties properties = AnimatedProperties.createDefault();
    private Scene scene;
    private Entity root;
    private Entity hand;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Test");
        root = scene.createEntity("Robot");
        Entity arm = scene.createEntity("Arm");
        arm.getTransform().setParent(root.getTransform());
        hand = scene.createEntity("Hand");
        hand.getTransform().setParent(arm.getTransform());
    }

    private static AnimationTrack track(String path, String property, float... timeValuePairs) {
        AnimationCurve curve = new AnimationCurve();
        for (int i = 0; i < timeValuePairs.length; i += 2) {
            curve.setKey(new Keyframe(timeValuePairs[i], timeValuePairs[i + 1], Interpolation.LINEAR));
        }
        return new AnimationTrack(path, property, curve);
    }

    @Test
    void shouldResolveChildPathsBothWays() {
        assertSame(hand, AnimationTrack.resolve(root, "Arm/Hand"));
        assertSame(root, AnimationTrack.resolve(root, ""));
        assertNull(AnimationTrack.resolve(root, "Arm/Foot"));
        assertEquals("Arm/Hand", AnimationTrack.pathOf(root, hand));
        assertEquals("", AnimationTrack.pathOf(root, root));
        assertNull(AnimationTrack.pathOf(hand, root));
    }

    @Test
    void shouldApplyTracksToRootAndChildren() {
        AnimationClip clip = new AnimationClip("Wave");
        clip.addTrack(track("", AnimatedProperties.POSITION_Y, 0f, 0f, 2f, 100f));
        clip.addTrack(track("Arm/Hand", AnimatedProperties.SCALE_X, 0f, 1f, 2f, 3f));

        int applied = ClipSampler.apply(clip, 1f, root, properties);

        assertEquals(2, applied);
        assertEquals(50f, root.getTransform().getLocalPosition().y, 1e-4f);
        assertEquals(2f, hand.getTransform().getLocalScale().x, 1e-4f);
    }

    @Test
    void shouldCombineRotationChannelsOfOneEntity() {
        AnimationClip clip = new AnimationClip("Turn");
        clip.addTrack(track("", AnimatedProperties.ROTATION_YAW, 0f, 30f));
        clip.addTrack(track("", AnimatedProperties.ROTATION_PITCH, 0f, 20f));
        clip.addTrack(track("", AnimatedProperties.ROTATION_ROLL, 0f, 10f));

        ClipSampler.apply(clip, 0f, root, properties);

        Vector3 yawPitchRoll = root.getTransform().getLocalEulerAngles();
        assertEquals(30f, yawPitchRoll.x, 1e-3f);
        assertEquals(20f, yawPitchRoll.y, 1e-3f);
        assertEquals(10f, yawPitchRoll.z, 1e-3f);
    }

    @Test
    void shouldAnimateComponentPropertiesAndModelClipTime() {
        root.addComponent(new MeshPrimitiveComponent());
        ModelComponent model = hand.addComponent(new ModelComponent("models/hero.glb"));
        model.setAnimation("Walk");
        model.setModelInfo(new ModelComponent.ModelInfo(Map.of("Walk", 4f), null, 3, null));
        AnimationClip clip = new AnimationClip("Fade");
        clip.addTrack(track("", "mesh.color.a", 0f, 1f, 2f, 0f));
        clip.addTrack(track("Arm/Hand", AnimatedProperties.MODEL_CLIP_TIME, 0f, 0f, 2f, 4f));

        ClipSampler.apply(clip, 0.5f, root, properties);

        assertEquals(0.75f, root.getComponent(MeshPrimitiveComponent.class).getColor().a, 1e-4f);
        assertEquals(1f, model.getTime(), 1e-4f);
    }

    @Test
    void shouldSkipAndReportTracksThatCannotPlay() {
        AnimationClip clip = new AnimationClip("Broken");
        clip.addTrack(track("Leg", AnimatedProperties.POSITION_X, 0f, 1f));
        clip.addTrack(track("", "mesh.color.r", 0f, 1f));
        clip.addTrack(track("", "no.such.property", 0f, 1f));
        clip.addTrack(track("", AnimatedProperties.POSITION_X));

        assertEquals(0, ClipSampler.apply(clip, 0f, root, properties));
        List<String> problems = ClipSampler.problems(clip, root, properties);

        assertEquals(3, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("no child 'Leg'"), problems.get(0));
        assertTrue(problems.get(1).contains("has no Mesh component"), problems.get(1));
        assertTrue(problems.get(2).contains("unknown property"), problems.get(2));
    }

    @Test
    void shouldRestoreEverythingTheClipChanged() {
        root.getTransform().setLocalPosition(5f, 6f, 7f);
        Quaternion rotation = new Quaternion().setEulerAngles(10f, 20f, 30f);
        root.getTransform().setLocalRotation(rotation);
        AnimationClip clip = new AnimationClip("Move");
        clip.addTrack(track("", AnimatedProperties.POSITION_X, 0f, 100f));
        clip.addTrack(track("", AnimatedProperties.ROTATION_YAW, 0f, 90f));

        ClipSampler.Snapshot snapshot = ClipSampler.capture(clip, root, properties);
        ClipSampler.apply(clip, 0f, root, properties);
        snapshot.restore();

        assertEquals(new Vector3(5f, 6f, 7f), root.getTransform().getLocalPosition());
        assertEquals(rotation, root.getTransform().getLocalRotation());
        assertTrue(snapshot.covers(root));
    }

    @Test
    void shouldRoundTripClipThroughJson() {
        AnimationClip clip = new AnimationClip("Bounce", 1.5f, 24);
        AnimationCurve curve = new AnimationCurve();
        curve.setKey(new Keyframe(0f, 0f, Interpolation.BEZIER));
        curve.setKey(new Keyframe(0.75f, 120f, Interpolation.CONSTANT));
        clip.addTrack(new AnimationTrack("Arm/Hand", AnimatedProperties.POSITION_Y, curve));

        AnimationClip copy = AnimationClipSerializer.fromJson(AnimationClipSerializer.toJson(clip));

        assertEquals("Bounce", copy.getName());
        assertEquals(1.5f, copy.getDuration());
        assertEquals(24, copy.getFrameRate());
        AnimationTrack track = copy.findTrack("Arm/Hand", AnimatedProperties.POSITION_Y);
        assertEquals(curve.getKeys(), track.getCurve().getKeys());
    }

    @Test
    void shouldRejectBrokenClipFilesWithReason() {
        assertThrows(SceneSerializationException.class, () -> AnimationClipSerializer.fromJson("{ nope"));
        SceneSerializationException newer = assertThrows(SceneSerializationException.class,
            () -> AnimationClipSerializer.fromJson("{\"format\": 99}"));
        assertTrue(newer.getMessage().contains("newer"));
        assertThrows(SceneSerializationException.class, () -> AnimationClipSerializer.fromJson(
            "{\"tracks\":[{\"property\":\"transform.position.x\",\"keys\":[{\"t\":0,\"v\":1,\"i\":\"WOBBLY\"}]}]}"));
        assertThrows(SceneSerializationException.class, () -> AnimationClipSerializer.fromJson(
            "{\"tracks\":[{\"property\":\"a\"},{\"property\":\"a\"}]}"));
    }

    @Test
    void shouldSnapTimesToFrames() {
        AnimationClip clip = new AnimationClip("Snap", 2f, 10);

        assertEquals(0.3f, clip.snapToFrame(0.27f), 1e-6f);
        assertThrows(IllegalArgumentException.class, () -> clip.setDuration(0f));
        assertThrows(IllegalArgumentException.class, () -> clip.setFrameRate(0));
    }
}
