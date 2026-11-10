package io.github.ldev22live.gdxunity.editor.desktop;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The humanoid clip library (Idle, Walk, Run, Jump, Fall in {@code animations/humanoid}) on its showcase scene
 * {@code humanoid-animations-3d.json}: every track finds its bone, every loop is seamless, and each clip moves the body
 * the way its name says (TD-044).
 */
class HumanoidAnimationSamplesTest {

    private static final File PROJECT = new File("../assets");
    private static final AnimatedProperties PROPERTIES = AnimatedProperties.createDefault();
    private static final float TOLERANCE = 1e-3f;
    private static final List<String> CLIPS = List.of("Idle", "Walk", "Run", "Jump", "Fall");
    private static final String FOOT_R = "Hips/Thigh R/Shin R/Foot R";
    private static final String FOOT_L = "Hips/Thigh L/Shin L/Foot L";
    private static final String HAND_R = "Hips/Spine/Chest/Arm R/Forearm R";
    /** How far a standing foot may be from the floor surface (y = 0) at the start of a clip. */
    private static final float FOOT_SLACK = 12f;

    private static final Map<String, Entity> HEROES = new LinkedHashMap<>();
    private static final Map<String, AnimationClip> LOADED = new LinkedHashMap<>();

    @BeforeAll
    static void load() throws IOException {
        GdxNativesLoader.load();
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        EditorApplication.registerComponentSerializers(registry);
        Scene scene = new SceneSerializer(registry).fromJson(
            Files.readString(new File(PROJECT, "samples/humanoid-animations-3d.json").toPath()));
        for (Entity root : scene.getRootEntities()) {
            AnimatorComponent animator = root.getComponent(AnimatorComponent.class);
            if (animator != null) {
                String name = new File(animator.getClipPath()).getName().replace(".anim", "");
                HEROES.put(name, root);
                LOADED.put(name, AnimationClipSerializer.fromJson(Files.readString(new File(PROJECT, animator.getClipPath()).toPath())));
            }
        }
    }

    private static float worldY(Entity hero, String path) {
        return AnimationTrack.resolve(hero, path).getTransform().getWorldPosition().y;
    }

    private static float worldZ(Entity hero, String path) {
        return AnimationTrack.resolve(hero, path).getTransform().getWorldPosition().z;
    }

    private static void pose(String name, float time) {
        ClipSampler.apply(LOADED.get(name), time, HEROES.get(name), PROPERTIES);
    }

    @Test
    void shouldShowOneHeroPerClip() {
        assertEquals(CLIPS, List.copyOf(HEROES.keySet()));
        assertTrue(CLIPS.stream().allMatch(name -> HEROES.get(name).getComponent(AnimatorComponent.class).getClipPath()
            .equals("animations/humanoid/" + name + ".anim")));
    }

    @Test
    void shouldPlayEveryTrackOfEveryClipOnTheSkeleton() {
        for (String name : CLIPS) {
            assertTrue(LOADED.get(name).getTracks().size() >= 8, name + " should move many bones");
            assertEquals(List.of(), ClipSampler.problems(LOADED.get(name), HEROES.get(name), PROPERTIES), name);
        }
    }

    @Test
    void shouldLoopEveryClipSeamlessly() {
        for (String name : CLIPS) {
            AnimationClip clip = LOADED.get(name);
            for (AnimationTrack track : clip.getTracks()) {
                assertEquals(track.getCurve().evaluate(0f), track.getCurve().evaluate(clip.getDuration()), TOLERANCE,
                    name + ": " + ClipSampler.describe(track, PROPERTIES));
            }
        }
    }

    @Test
    void shouldStartEveryGroundClipWithFeetOnTheFloor() {
        for (String name : List.of("Idle", "Walk", "Run", "Jump")) {
            pose(name, 0f);
            Entity hero = HEROES.get(name);
            float lowest = Math.min(worldY(hero, FOOT_R), worldY(hero, FOOT_L));
            assertTrue(Math.abs(lowest) < FOOT_SLACK, name + " starts with its lowest foot at y=" + lowest);
        }
    }

    @Test
    void shouldRunFasterAndWithWiderStridesThanWalk() {
        assertTrue(LOADED.get("Run").getDuration() < LOADED.get("Walk").getDuration(), "a run cycle is shorter than a walk cycle");
        float walkStride = stride("Walk");
        float runStride = stride("Run");
        assertTrue(runStride > walkStride, "feet spread " + runStride + " in Run vs " + walkStride + " in Walk");
    }

    /** Greatest distance along z between the two feet over the clip. */
    private static float stride(String name) {
        float widest = 0f;
        AnimationClip clip = LOADED.get(name);
        for (int i = 0; i <= 20; i++) {
            pose(name, clip.getDuration() * i / 20f);
            widest = Math.max(widest, Math.abs(worldZ(HEROES.get(name), FOOT_R) - worldZ(HEROES.get(name), FOOT_L)));
        }
        return widest;
    }

    @Test
    void shouldLeaveTheGroundWhenJumpingAndLandAgain() {
        Entity hero = HEROES.get("Jump");
        pose("Jump", 0f);
        float standing = worldY(hero, "Hips");
        pose("Jump", 0.2f);
        float crouched = worldY(hero, "Hips");
        pose("Jump", 0.55f);
        float apex = worldY(hero, "Hips");
        pose("Jump", 1f);
        float landed = worldY(hero, "Hips");

        assertTrue(crouched < standing - 20f, "crouch before the jump: " + crouched + " vs " + standing);
        assertTrue(apex > standing + 60f, "the hips rise well above standing height: " + apex + " vs " + standing);
        assertEquals(standing, landed, 1f, "the jump ends standing where it began");
        pose("Jump", 0.55f);
        assertTrue(Math.min(worldY(hero, FOOT_R), worldY(hero, FOOT_L)) > 40f, "both feet are off the floor at the top of the jump");
    }

    @Test
    void shouldFlailWithArmsRaisedWhenFalling() {
        Entity hero = HEROES.get("Fall");
        pose("Fall", 0f);
        float hips = worldY(hero, "Hips");
        float shoulders = worldY(hero, "Hips/Spine/Chest");
        float hand = worldY(hero, HAND_R);

        assertTrue(hips > 120f, "falling happens above the ground: hips at " + hips);
        assertTrue(hand > shoulders, "the arms are thrown up: hand " + hand + " above shoulders " + shoulders);
    }

    @Test
    void shouldKeepWalkEqualToTheSkeletonSampleWalkCycle() throws IOException {
        AnimationClip walk = LOADED.get("Walk");
        AnimationClip original = AnimationClipSerializer.fromJson(
            Files.readString(new File(PROJECT, "animations/skeleton/WalkCycle.anim").toPath()));

        assertNotNull(walk);
        assertEquals(original.getTracks().size(), walk.getTracks().size());
        assertEquals(original.getDuration(), walk.getDuration(), TOLERANCE);
    }
}
