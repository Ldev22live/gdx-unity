package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.animation.ClipLibrary;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The animation sample clips the Animations tab lists ({@code animations/humanoid} and {@code animations/samples}): every clip
 * loads, the humanoid ones play on the humanoid skeleton and loop seamlessly, and the general ones play on the entities of the
 * {@code animation-samples-3d} scene.
 */
class AnimationSampleClipsTest {
    private static final File PROJECT = new File("../assets");
    private static final AnimatedProperties PROPERTIES = AnimatedProperties.createDefault();
    private static final float TOLERANCE = 1e-3f;
    private static final List<String> HUMANOID = List.of("Crouch", "Dance", "Fall", "Idle", "Jump", "LookAround", "Punch", "Run", "Walk", "Wave");
    private static final List<String> GENERAL = List.of("CameraSway", "ColorPulse", "DoorSwing", "Pendulum", "PulsingLight", "SpinCube");

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static Scene load(String sample) throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        EditorApplication.registerComponentSerializers(registry);
        return new SceneSerializer(registry).fromJson(Files.readString(new File(PROJECT, "samples/" + sample + ".json").toPath()));
    }

    private static AnimationClip clip(String folder, String name) throws IOException {
        return AnimationClipSerializer.fromJson(Files.readString(new File(PROJECT, "animations/" + folder + "/" + name + ".anim").toPath()));
    }

    @Test
    void shouldListTheHumanoidTemplatesFirstAndTheGeneralSamplesAfterThem() {
        List<ClipLibrary.Entry> entries = ClipLibrary.list(PROJECT);

        assertEquals(HUMANOID, entries.stream().filter(ClipLibrary.Entry::humanoid).map(ClipLibrary.Entry::name).toList());
        List<String> others = entries.stream().filter(entry -> !entry.humanoid()).map(ClipLibrary.Entry::name).toList();
        assertTrue(others.containsAll(GENERAL), "general samples listed: " + others);
        int firstOther = entries.indexOf(entries.stream().filter(entry -> !entry.humanoid()).findFirst().orElseThrow());
        assertTrue(entries.subList(0, firstOther).stream().allMatch(ClipLibrary.Entry::humanoid), "humanoid clips come first");
    }

    @Test
    void shouldPlayEveryHumanoidClipOnTheHumanoidSkeletonAndLoopSeamlessly() throws IOException {
        Scene scene = load("humanoid-animations-3d");
        Entity hero = scene.getRootEntities().stream().filter(entity -> entity.getComponent(AnimatorComponent.class) != null).findFirst().orElseThrow();
        for (String name : HUMANOID) {
            AnimationClip clip = clip("humanoid", name);
            assertEquals(name, clip.getName());
            assertTrue(clip.getTracks().size() >= 4, name + " moves several parts: " + clip.getTracks().size());
            assertEquals(List.of(), ClipSampler.problems(clip, hero, PROPERTIES), name);
            for (AnimationTrack track : clip.getTracks()) {
                assertEquals(track.getCurve().evaluate(0f), track.getCurve().evaluate(clip.getDuration()), TOLERANCE,
                    name + ": " + ClipSampler.describe(track, PROPERTIES));
            }
        }
        scene.dispose();
    }

    @Test
    void shouldMakeEachNewHumanoidClipMoveTheBodyDifferentlyFromTheRestPose() throws IOException {
        Scene scene = load("humanoid-animations-3d");
        Entity hero = scene.getRootEntities().stream().filter(entity -> entity.getComponent(AnimatorComponent.class) != null).findFirst().orElseThrow();
        for (String name : List.of("Crouch", "Dance", "LookAround", "Punch", "Wave")) {
            AnimationClip clip = clip("humanoid", name);
            ClipSampler.apply(clip, 0f, hero, PROPERTIES);
            float moved = 0f;
            for (float t = 0.1f; t < clip.getDuration(); t += 0.1f) {
                ClipSampler.apply(clip, t, hero, PROPERTIES);
                for (AnimationTrack track : clip.getTracks()) {
                    moved = Math.max(moved, Math.abs(track.getCurve().evaluate(t) - track.getCurve().evaluate(0f)));
                }
            }
            assertTrue(moved > 5f, name + " changes some track by " + moved);
        }
        scene.dispose();
    }

    @Test
    void shouldCrouchWithTheHipsDroppingByWhatTheBentLegsGiveUp() throws IOException {
        AnimationClip crouch = clip("humanoid", "Crouch");
        AnimationTrack hips = crouch.findTrack("Hips", "transform.position.y");
        // thigh 48 and shin 46 long: bent by -70 and +80 degrees the legs reach 62 down instead of 94, so the hips drop 32
        assertEquals(68f, hips.getCurve().evaluate(1.0f), 0.5f);
        assertEquals(100f, hips.getCurve().evaluate(0f), 0.01f);
    }

    @Test
    void shouldPlayEveryGeneralClipOnTheEntityTheSampleSceneGivesIt() throws IOException {
        Scene scene = load("animation-samples-3d");
        int played = 0;
        for (Entity entity : scene.getAllEntities()) {
            AnimatorComponent animator = entity.getComponent(AnimatorComponent.class);
            if (animator == null) {
                continue;
            }
            String name = new File(animator.getClipPath()).getName().replace(".anim", "");
            assertTrue(GENERAL.contains(name), name);
            AnimationClip clip = clip("samples", name);
            assertEquals(List.of(), ClipSampler.problems(clip, entity, PROPERTIES), name + " on " + entity.getName());
            assertEquals(clip.getTracks().size(), ClipSampler.apply(clip, clip.getDuration() / 2f, entity, PROPERTIES), name);
            played++;
        }
        assertEquals(GENERAL.size(), played, "one entity per general clip");
        assertFalse(scene.getAllEntities().isEmpty());
        scene.dispose();
    }
}
