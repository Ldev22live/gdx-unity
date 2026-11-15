package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioListenerComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.EngineSoundComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Engine Sounds (TD-080) played through {@link GameAudio}: binding, mixing, stereo placement and unbinding. */
class GameAudioEngineTest {
    private static final int BLOCK = 2048;
    /** Blocks rendered so the engine's inertia and the gain ramp have settled. */
    private static final int SETTLE = 8;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static EngineSoundComponent addEngine(Scene scene, String name, float x, String profile, boolean spatial) {
        Entity entity = scene.createEntity(name);
        entity.getTransform().setLocalPosition(x, 0f, 0f);
        EngineSoundComponent engine = new EngineSoundComponent(profile);
        engine.setSpatial(spatial);
        engine.setDistances(100f, 1000f);
        entity.addComponent(engine);
        return engine;
    }

    /** Renders {@code SETTLE} blocks and returns the root mean square of the last one, left then right. */
    private static float[] level(GameAudio audio) {
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];
        for (int i = 0; i < SETTLE; i++) {
            audio.render(left, right, BLOCK);
        }
        return new float[] {rms(left), rms(right)};
    }

    private static float rms(float[] samples) {
        double sum = 0;
        for (float s : samples) {
            sum += s * s;
        }
        return (float) Math.sqrt(sum / samples.length);
    }

    @Test
    void shouldBindAnEngineAndSoundItFromTheStartEvenBeforeTheScriptSetsAState() {
        Scene scene = new Scene("Race");
        EngineSoundComponent engine = addEngine(scene, "Car", 0f, "Muscle V8", false);
        GameAudio audio = new GameAudio(scene, false);

        List<String> problems = audio.bind();

        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(1, audio.getBoundEngineCount());
        assertTrue(engine.isBound());
        engine.setState(0f, 0f);
        float[] idle = level(audio);
        assertTrue(idle[0] > 0.001f && idle[1] > 0.001f, "an idling engine is audible: " + idle[0] + ", " + idle[1]);
        audio.dispose();
    }

    @Test
    void shouldBeLouderWhenTheScriptGivesItRevsAndThrottle() {
        Scene scene = new Scene("Race");
        EngineSoundComponent engine = addEngine(scene, "Car", 0f, "Hatchback", false);
        GameAudio audio = new GameAudio(scene, false);
        audio.bind();
        engine.setState(0f, 0f);
        float idle = level(audio)[0];

        engine.setState(0.8f, 1f);
        float flatOut = level(audio)[0];

        assertTrue(flatOut > 1.5f * idle, "flat out " + flatOut + " vs idle " + idle);
        audio.dispose();
    }

    @Test
    void shouldPlaceASpatialEngineInTheSpeakerItsSideOfTheListenerAndFadeItWithDistance() {
        Scene scene = new Scene("Race");
        scene.createEntity("Ears").addComponent(new AudioListenerComponent());
        EngineSoundComponent near = addEngine(scene, "Near", 200f, "Hatchback", true);
        GameAudio audio = new GameAudio(scene, false);
        audio.bind();
        near.setState(0.6f, 1f);
        float[] closeBy = level(audio);

        near.getEntity().getTransform().setLocalPosition(900f, 0f, 0f);
        near.setState(0.6f, 1f);
        float[] far = level(audio);

        assertTrue(closeBy[1] > 3f * closeBy[0], "a car on the right is in the right speaker: " + closeBy[0] + ", " + closeBy[1]);
        assertTrue(far[1] < 0.5f * closeBy[1], "and quieter when further away: " + far[1] + " vs " + closeBy[1]);
        audio.dispose();
    }

    @Test
    void shouldReportAnUnknownEngineAndBindNothing() {
        Scene scene = new Scene("Race");
        EngineSoundComponent engine = addEngine(scene, "Car", 0f, "Steam Engine", false);
        GameAudio audio = new GameAudio(scene, false);

        List<String> problems = audio.bind();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Steam Engine"), problems.get(0));
        assertEquals(0, audio.getBoundEngineCount());
        assertFalse(engine.isBound());
        audio.dispose();
    }

    @Test
    void shouldSilenceAndUnbindEnginesWhenDisposed() {
        Scene scene = new Scene("Race");
        EngineSoundComponent engine = addEngine(scene, "Car", 0f, "Sports Coupe", false);
        GameAudio audio = new GameAudio(scene, false);
        audio.bind();

        audio.dispose();

        assertFalse(engine.isBound());
        assertEquals(0, audio.getBoundEngineCount());
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];
        audio.render(left, right, BLOCK);
        assertEquals(0f, rms(left), 0f);
    }

    @Test
    void shouldNotBindADisabledEngine() {
        Scene scene = new Scene("Race");
        EngineSoundComponent engine = addEngine(scene, "Car", 0f, "Hatchback", false);
        engine.setEnabled(false);
        GameAudio audio = new GameAudio(scene, false);

        audio.bind();

        assertEquals(0, audio.getBoundEngineCount());
        audio.dispose();
    }
}
