package io.github.ldev22live.gdxunity.engine.scene.audio;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Positional sound (Phase 20-C): the listener, the fade with distance, the pan, and what a spatial source sends its sink. */
class SpatialAudioTest {
    private static final Vector3 EARS = new Vector3(0f, 0f, 0f);
    private static final Vector3 RIGHT = new Vector3(1f, 0f, 0f);

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** One call of a sink: the sound, volume, pitch and pan it was asked to play. */
    private record Played(String sound, float volume, float pitch, float pan) {
    }

    private static final class RecordingSink implements SoundSink {
        final List<Played> played = new ArrayList<>();

        @Override
        public void play(String sound, float volume, float pitch) {
            played.add(new Played(sound, volume, pitch, 0f));
        }

        @Override
        public void play(String sound, float volume, float pitch, float pan) {
            played.add(new Played(sound, volume, pitch, pan));
        }
    }

    // --- The model ---

    @Test
    void shouldHearASoundAtFullVolumeUpToTheNearDistanceAndNotAtAllBeyondTheFarOne() {
        assertEquals(1f, SpatialAudio.hear(EARS, RIGHT, new Vector3(0f, 0f, -100f), 100f, 500f).gain(), 1e-5f);
        assertEquals(0.75f, SpatialAudio.hear(EARS, RIGHT, new Vector3(0f, 0f, -200f), 100f, 500f).gain(), 1e-5f);
        assertEquals(0f, SpatialAudio.hear(EARS, RIGHT, new Vector3(0f, 0f, -500f), 100f, 500f).gain(), 1e-5f);
        assertEquals(0f, SpatialAudio.hear(EARS, RIGHT, new Vector3(0f, 900f, 0f), 100f, 500f).gain(), 1e-5f);
    }

    @Test
    void shouldPanTowardsTheSideTheSoundIsOn() {
        assertEquals(1f, SpatialAudio.hear(EARS, RIGHT, new Vector3(300f, 0f, 0f), 100f, 1000f).pan(), 1e-5f);
        assertEquals(-1f, SpatialAudio.hear(EARS, RIGHT, new Vector3(-300f, 0f, 0f), 100f, 1000f).pan(), 1e-5f);
        assertEquals(0f, SpatialAudio.hear(EARS, RIGHT, new Vector3(0f, 0f, -300f), 100f, 1000f).pan(), 1e-5f, "straight ahead");
        assertEquals(0f, SpatialAudio.hear(EARS, RIGHT, new Vector3(0f, 0f, 300f), 100f, 1000f).pan(), 1e-5f, "straight behind");
        assertEquals(Math.sqrt(0.5), SpatialAudio.hear(EARS, RIGHT, new Vector3(300f, 0f, -300f), 100f, 1000f).pan(), 1e-4f, "ahead and right");
    }

    @Test
    void shouldNotPanASoundAtTheListenersOwnPlaceAndNotNeedAUnitRightVector() {
        assertEquals(0f, SpatialAudio.hear(EARS, new Vector3(5f, 0f, 0f), EARS, 100f, 500f).pan());
        assertEquals(1f, SpatialAudio.hear(EARS, new Vector3(5f, 0f, 0f), new Vector3(50f, 0f, 0f), 100f, 500f).pan(), 1e-5f);
    }

    @Test
    void shouldRefuseDistancesThatDoNotGrowAndAZeroRightVector() {
        assertThrows(IllegalArgumentException.class, () -> SpatialAudio.hear(EARS, RIGHT, EARS, 500f, 500f));
        assertThrows(IllegalArgumentException.class, () -> SpatialAudio.hear(EARS, RIGHT, EARS, -1f, 500f));
        assertThrows(IllegalArgumentException.class, () -> SpatialAudio.hear(EARS, new Vector3(), EARS, 100f, 500f));
    }

    // --- The listener ---

    @Test
    void shouldFindTheFirstEnabledListenerOnAnActiveEntity() {
        Scene scene = new Scene("Level");
        assertNull(AudioListenerComponent.find(scene));
        Entity hidden = scene.createEntity("Hidden");
        hidden.addComponent(new AudioListenerComponent());
        hidden.setActive(false);
        Entity off = scene.createEntity("Off");
        AudioListenerComponent disabled = new AudioListenerComponent();
        disabled.setEnabled(false);
        off.addComponent(disabled);
        Entity camera = scene.createEntity("Camera");
        AudioListenerComponent ears = new AudioListenerComponent();
        camera.addComponent(ears);

        assertSame(ears, AudioListenerComponent.find(scene));
    }

    @Test
    void shouldSaveAndLoadAListenerAndTheSpatialSettingsOfASource() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new AudioListenerComponentSerializer());
        registry.register(new AudioSourceComponentSerializer());
        SceneSerializer serializer = new SceneSerializer(registry);
        Scene scene = new Scene("Level");
        scene.createEntity("Ears").addComponent(new AudioListenerComponent());
        AudioSourceComponent radio = new AudioSourceComponent("hum", "sounds/hum.wav");
        radio.setSpatial(true);
        radio.setDistances(50f, 800f);
        scene.createEntity("Radio").addComponent(radio);
        scene.createEntity("Plain").addComponent(new AudioSourceComponent("blip", "Coin"));

        Scene loaded = serializer.fromJson(serializer.toJson(scene));

        assertNotNull(loaded.findEntityByName("Ears").getComponent(AudioListenerComponent.class));
        AudioSourceComponent again = loaded.findEntityByName("Radio").getComponent(AudioSourceComponent.class);
        assertTrue(again.isSpatial());
        assertTrue(again.isClip());
        assertEquals(50f, again.getMinDistance());
        assertEquals(800f, again.getMaxDistance());
        AudioSourceComponent plain = loaded.findEntityByName("Plain").getComponent(AudioSourceComponent.class);
        assertFalse(plain.isSpatial());
        assertFalse(serializer.toJson(scene).contains("minDistance\": 100"), "default distances are not written");
    }

    // --- A spatial source ---

    private static AudioSourceComponent sourceAt(Scene scene, float x, float z, RecordingSink sink) {
        Entity entity = scene.createEntity("Source");
        entity.getTransform().setLocalPosition(x, 0f, z);
        AudioSourceComponent source = new AudioSourceComponent("ping", "Coin");
        source.setSpatial(true);
        source.setDistances(100f, 500f);
        entity.addComponent(source);
        source.bind(sink);
        return source;
    }

    @Test
    void shouldPlayASpatialSourceQuieterAndToTheRightWhenItIsFarAndToTheRightOfTheListener() {
        Scene scene = new Scene("Level");
        scene.createEntity("Ears").addComponent(new AudioListenerComponent());
        RecordingSink sink = new RecordingSink();
        AudioSourceComponent source = sourceAt(scene, 300f, 0f, sink);

        source.play();

        assertEquals(1, sink.played.size());
        assertEquals(0.5f, sink.played.get(0).volume(), 1e-4f, "300 of a fade between 100 and 500");
        assertEquals(1f, sink.played.get(0).pan(), 1e-4f);
    }

    @Test
    void shouldTurnThePanWithTheListenerBecauseRightIsTheListenersRight() {
        Scene scene = new Scene("Level");
        Entity ears = scene.createEntity("Ears");
        ears.addComponent(new AudioListenerComponent());
        ears.getTransform().rotate(180f, 0f, 0f);
        RecordingSink sink = new RecordingSink();
        AudioSourceComponent source = sourceAt(scene, 200f, 0f, sink);

        source.play();

        assertEquals(-1f, sink.played.get(0).pan(), 1e-4f, "the listener faces the other way, so the source is on its left");
    }

    @Test
    void shouldPlayNothingForASpatialSourceBeyondTheFarDistance() {
        Scene scene = new Scene("Level");
        scene.createEntity("Ears").addComponent(new AudioListenerComponent());
        RecordingSink sink = new RecordingSink();

        sourceAt(scene, 0f, -900f, sink).play();

        assertTrue(sink.played.isEmpty());
    }

    @Test
    void shouldPlayASpatialSourceInTheMiddleAtFullVolumeWhenTheSceneHasNoListener() {
        Scene scene = new Scene("Level");
        RecordingSink sink = new RecordingSink();

        sourceAt(scene, 300f, 0f, sink).play();

        assertEquals(new Played("Coin", 1f, 1f, 0f), sink.played.get(0));
    }

    @Test
    void shouldIgnoreTheListenerForASourceThatIsNotSpatial() {
        Scene scene = new Scene("Level");
        scene.createEntity("Ears").addComponent(new AudioListenerComponent());
        RecordingSink sink = new RecordingSink();
        AudioSourceComponent source = sourceAt(scene, 300f, 0f, sink);
        source.setSpatial(false);

        source.play();

        assertEquals(new Played("Coin", 1f, 1f, 0f), sink.played.get(0));
    }

    @Test
    void shouldRefuseDistancesThatDoNotGrowOnASource() {
        AudioSourceComponent source = new AudioSourceComponent();

        assertThrows(IllegalArgumentException.class, () -> source.setDistances(300f, 300f));
        assertEquals(AudioSourceComponent.DEFAULT_MIN_DISTANCE, source.getMinDistance());
    }

    @Test
    void shouldTellRecordedClipsFromFactoryEffectsByExtension() {
        assertTrue(AudioSourceComponent.isClip("sounds/Hit.WAV"));
        assertFalse(AudioSourceComponent.isClip("Coin"));
        assertFalse(AudioSourceComponent.isClip(null));
    }
}
