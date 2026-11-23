package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperties;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClipSerializer;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.animation.ClipSampler;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skeleton animation samples ({@code skeleton-*-3d.json}, clips in {@code animations/skeleton}): each scene loads,
 * its Animator's clip plays on the bone hierarchy with no unresolved track, the loops are seamless, and a parent
 * bone's rotation carries its children (forward kinematics).
 */
class SkeletonAnimationSamplesTest {

    private static final File PROJECT = new File("../assets");
    private static final float TOLERANCE = 1e-3f;
    private static final AnimatedProperties PROPERTIES = AnimatedProperties.createDefault();

    @BeforeAll
    static void initNatives() {
        GdxNativesLoader.load();
    }

    private static Scene load(String sample) throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        EditorApplication.registerComponentSerializers(registry);
        return new SceneSerializer(registry).fromJson(
            Files.readString(new File(PROJECT, "samples/" + sample + ".json").toPath()));
    }

    private static AnimationClip clipOf(AnimatorComponent animator) throws IOException {
        return AnimationClipSerializer.fromJson(Files.readString(new File(PROJECT, animator.getClipPath()).toPath()));
    }

    private static Entity animated(Scene scene) {
        for (Entity entity : scene.getRootEntities()) {
            if (entity.getComponent(AnimatorComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("no entity has an Animator");
    }

    @Test
    void shouldPlayEveryTrackOfEachSampleClipOnItsBones() throws IOException {
        for (String sample : List.of("skeleton-wave-3d", "skeleton-walk-3d", "skeleton-tentacle-3d")) {
            Entity root = animated(load(sample));
            AnimationClip clip = clipOf(root.getComponent(AnimatorComponent.class));
            assertFalse(clip.getTracks().isEmpty(), sample);
            assertEquals(List.of(), ClipSampler.problems(clip, root, PROPERTIES), sample);
        }
    }

    @Test
    void shouldLoopSeamlesslyWhenFirstAndLastKeyAgree() throws IOException {
        for (String sample : List.of("skeleton-wave-3d", "skeleton-walk-3d", "skeleton-tentacle-3d")) {
            Entity root = animated(load(sample));
            AnimationClip clip = clipOf(root.getComponent(AnimatorComponent.class));
            for (AnimationTrack track : clip.getTracks()) {
                assertEquals(track.getCurve().evaluate(0f), track.getCurve().evaluate(clip.getDuration()), TOLERANCE,
                    sample + ": " + ClipSampler.describe(track, PROPERTIES));
            }
        }
    }

    @Test
    void shouldSwingWalkingLegsInOppositionAndMoveTheFootWithTheLeg() throws IOException {
        Entity hero = animated(load("skeleton-walk-3d"));
        AnimationClip clip = clipOf(hero.getComponent(AnimatorComponent.class));
        Entity footR = AnimationTrack.resolve(hero, "Hips/Thigh R/Shin R/Foot R");
        Entity thighR = AnimationTrack.resolve(hero, "Hips/Thigh R");
        Entity thighL = AnimationTrack.resolve(hero, "Hips/Thigh L");
        assertNotNull(footR);

        ClipSampler.apply(clip, 0f, hero, PROPERTIES);
        float pitchR = thighR.getTransform().getLocalEulerAngles().y;
        float pitchL = thighL.getTransform().getLocalEulerAngles().y;
        Vector3 footAtStart = footR.getTransform().getWorldPosition();
        assertTrue(pitchR * pitchL < 0f, "legs swing in opposite directions: " + pitchR + " vs " + pitchL);

        ClipSampler.apply(clip, 0.5f, hero, PROPERTIES);
        assertNotEquals(footAtStart.z, footR.getTransform().getWorldPosition().z, 1f,
            "the foot follows the thigh and shin without being animated itself");
    }

    @Test
    void shouldCarryTheForearmAlongWhenTheUpperArmRaises() throws IOException {
        Entity hero = animated(load("skeleton-wave-3d"));
        AnimationClip clip = clipOf(hero.getComponent(AnimatorComponent.class));
        Entity hand = AnimationTrack.resolve(hero, "Hips/Spine/Chest/Arm R/Forearm R");
        assertNotNull(hand);

        ClipSampler.apply(clip, 0f, hero, PROPERTIES);
        float lowered = hand.getTransform().getWorldPosition().y;
        ClipSampler.apply(clip, 1f, hero, PROPERTIES);
        float raised = hand.getTransform().getWorldPosition().y;
        assertTrue(raised > lowered + 30f, "raising the upper arm lifts the forearm joint: " + lowered + " -> " + raised);
    }

    @Test
    void shouldLagEachTentacleSegmentBehindItsParent() throws IOException {
        Entity tentacle = animated(load("skeleton-tentacle-3d"));
        AnimationClip clip = clipOf(tentacle.getComponent(AnimatorComponent.class));
        Entity first = AnimationTrack.resolve(tentacle, "Segment 1");
        Entity second = AnimationTrack.resolve(tentacle, "Segment 1/Segment 2");
        assertNotNull(second);

        // The same wave with a later start: at the same instant the two segments have different angles
        ClipSampler.apply(clip, 0.5f, tentacle, PROPERTIES);
        assertNotEquals(first.getTransform().getLocalEulerAngles().z, second.getTransform().getLocalEulerAngles().z, 1f);
    }
}
