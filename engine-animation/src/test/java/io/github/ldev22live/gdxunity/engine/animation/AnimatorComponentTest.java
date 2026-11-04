package io.github.ldev22live.gdxunity.engine.animation;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatorComponentTest {

    private Entity entity;
    private AnimatorComponent animator;
    private AnimationClip clip;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        entity = new Scene("Test").createEntity("Box");
        animator = entity.addComponent(new AnimatorComponent("animations/Rise.anim"));
        clip = new AnimationClip("Rise", 2f, 30);
        AnimationCurve curve = new AnimationCurve();
        curve.setKey(new Keyframe(0f, 0f, Interpolation.LINEAR));
        curve.setKey(new Keyframe(2f, 200f, Interpolation.LINEAR));
        clip.addTrack(new AnimationTrack("", AnimatedProperties.POSITION_Y, curve));
    }

    private float y() {
        return entity.getTransform().getLocalPosition().y;
    }

    @Test
    void shouldDoNothingUntilBound() {
        animator.onStart();
        animator.onUpdate(1f);

        assertEquals(0f, y());
        assertFalse(animator.isPlaying());
    }

    @Test
    void shouldPlayAndLoop() {
        animator.bind(clip, AnimatedProperties.createDefault());
        animator.onStart();

        animator.onUpdate(0.5f);
        assertEquals(50f, y(), 1e-3f);

        animator.onUpdate(2f);
        assertEquals(0.5f, animator.getTime(), 1e-5f);
        assertTrue(animator.isPlaying());
    }

    @Test
    void shouldStopAtEndWhenPlayingOnce() {
        animator.setWrapMode(WrapMode.ONCE);
        animator.bind(clip, AnimatedProperties.createDefault());
        animator.onStart();

        animator.onUpdate(5f);

        assertEquals(200f, y(), 1e-3f);
        assertFalse(animator.isPlaying());
    }

    @Test
    void shouldTurnAroundWithPingPong() {
        animator.setWrapMode(WrapMode.PING_PONG);
        animator.bind(clip, AnimatedProperties.createDefault());
        animator.onStart();

        animator.onUpdate(2.5f);
        assertEquals(1.5f, animator.getTime(), 1e-5f);

        animator.onUpdate(1f);
        assertEquals(0.5f, animator.getTime(), 1e-5f);

        animator.onUpdate(1f);
        assertEquals(0.5f, animator.getTime(), 1e-5f);
    }

    @Test
    void shouldPlayBackwardsFromEndWithNegativeSpeed() {
        animator.setSpeed(-1f);
        animator.setWrapMode(WrapMode.ONCE);
        animator.bind(clip, AnimatedProperties.createDefault());
        animator.onStart();

        animator.onUpdate(0.5f);

        assertEquals(150f, y(), 1e-3f);
    }

    @Test
    void shouldNotStartWhenPlayOnStartIsOffUntilPlayIsCalled() {
        animator.setPlayOnStart(false);
        animator.bind(clip, AnimatedProperties.createDefault());
        animator.onStart();
        animator.onUpdate(1f);
        assertEquals(0f, animator.getTime());

        animator.play();
        animator.onUpdate(1f);
        assertEquals(100f, y(), 1e-3f);

        animator.stop();
        assertEquals(0f, y(), 1e-3f);
        assertFalse(animator.isPlaying());
    }

    @Test
    void shouldStartPlayingWhenBoundAfterStart() {
        animator.onStart();
        animator.bind(clip, AnimatedProperties.createDefault());

        animator.onUpdate(1f);

        assertEquals(100f, y(), 1e-3f);
    }

    @Test
    void shouldRoundTripSettingsThroughSerializer() {
        animator.setPlayOnStart(false);
        animator.setWrapMode(WrapMode.PING_PONG);
        animator.setSpeed(0.5f);
        AnimatorComponentSerializer serializer = new AnimatorComponentSerializer();
        StringWriter out = new StringWriter();
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setWriter(out);
        json.writeObjectStart();
        serializer.serialize(animator, json);
        json.writeObjectEnd();

        AnimatorComponent copy = serializer.deserialize(new JsonReader().parse(out.toString()), new Json());

        assertEquals("animations/Rise.anim", copy.getClipPath());
        assertFalse(copy.isPlayOnStart());
        assertEquals(WrapMode.PING_PONG, copy.getWrapMode());
        assertEquals(0.5f, copy.getSpeed());
    }
}
