package io.github.ldev22live.gdxunity.engine.scene.audio;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineSoundComponentTest {

    private record Update(float revs, float throttle, float gain, float pan) {
    }

    private static final class RecordingVoice implements EngineVoice {
        final List<Update> updates = new ArrayList<>();

        @Override
        public void update(float revs, float throttle, float gain, float pan) {
            updates.add(new Update(revs, throttle, gain, pan));
        }
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new EngineSoundComponentSerializer());
        return new SceneSerializer(registry);
    }

    private static EngineSoundComponent engineAt(Scene scene, float x, float z, RecordingVoice voice) {
        Entity entity = scene.createEntity("Car");
        entity.getTransform().setLocalPosition(x, 0f, z);
        EngineSoundComponent engine = new EngineSoundComponent("Muscle V8");
        engine.setSpatial(true);
        engine.setDistances(100f, 500f);
        entity.addComponent(engine);
        engine.bind(voice);
        return engine;
    }

    @Test
    void shouldDoNothingWhenUpdatedWhileUnbound() {
        EngineSoundComponent engine = new EngineSoundComponent();

        engine.setState(0.5f, 1f);

        assertFalse(engine.isBound());
    }

    @Test
    void shouldSendRevsThrottleAndVolumeToTheBoundVoiceInTheMiddleWhenNotSpatial() {
        RecordingVoice voice = new RecordingVoice();
        EngineSoundComponent engine = new EngineSoundComponent();
        engine.setVolume(0.5f);
        engine.bind(voice);

        engine.setState(0.4f, 0.75f);
        engine.unbind();
        engine.setState(0.9f, 1f);

        assertEquals(List.of(new Update(0.4f, 0.75f, 0.5f, 0f)), voice.updates);
    }

    @Test
    void shouldHoldRevsAndThrottleWithinZeroToOne() {
        RecordingVoice voice = new RecordingVoice();
        EngineSoundComponent engine = new EngineSoundComponent();
        engine.bind(voice);

        engine.setState(1.7f, -0.3f);

        assertEquals(new Update(1f, 0f, 1f, 0f), voice.updates.get(0));
    }

    @Test
    void shouldBeSilentWhenDisabled() {
        RecordingVoice voice = new RecordingVoice();
        EngineSoundComponent engine = new EngineSoundComponent();
        engine.bind(voice);
        engine.setEnabled(false);

        engine.setState(0.5f, 1f);

        assertEquals(0f, voice.updates.get(0).gain());
    }

    @Test
    void shouldFadeAndPanASpatialEngineByItsPlaceRelativeToTheListener() {
        Scene scene = new Scene("Race");
        scene.createEntity("Ears").addComponent(new AudioListenerComponent());
        RecordingVoice voice = new RecordingVoice();

        engineAt(scene, 300f, 0f, voice).setState(0.6f, 1f);

        assertEquals(0.5f, voice.updates.get(0).gain(), 1e-4f, "300 of a fade between 100 and 500");
        assertEquals(1f, voice.updates.get(0).pan(), 1e-4f);
    }

    @Test
    void shouldSilenceASpatialEngineBeyondTheFarDistance() {
        Scene scene = new Scene("Race");
        scene.createEntity("Ears").addComponent(new AudioListenerComponent());
        RecordingVoice voice = new RecordingVoice();

        engineAt(scene, 0f, -900f, voice).setState(0.6f, 1f);

        assertEquals(0f, voice.updates.get(0).gain());
    }

    @Test
    void shouldPlayASpatialEngineInTheMiddleWhenTheSceneHasNoListener() {
        RecordingVoice voice = new RecordingVoice();

        engineAt(new Scene("Race"), 300f, 0f, voice).setState(0.6f, 1f);

        assertEquals(new Update(0.6f, 1f, 1f, 0f), voice.updates.get(0));
    }

    @Test
    void shouldRefuseValuesThatAreNotNumbersOrOutOfRange() {
        EngineSoundComponent engine = new EngineSoundComponent();

        assertThrows(IllegalArgumentException.class, () -> engine.setState(Float.NaN, 1f));
        assertThrows(IllegalArgumentException.class, () -> engine.setState(0.2f, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> engine.setVolume(1.5f));
        assertThrows(IllegalArgumentException.class, () -> engine.setProfile(" "));
        assertThrows(IllegalArgumentException.class, () -> engine.setDistances(300f, 300f));
        assertEquals(EngineSoundComponent.DEFAULT_MIN_DISTANCE, engine.getMinDistance());
    }

    @Test
    void shouldSaveAndLoadItsSettings() {
        SceneSerializer serializer = serializer();
        Scene scene = new Scene("Race");
        EngineSoundComponent rival = new EngineSoundComponent("Sports Coupe");
        rival.setVolume(0.6f);
        rival.setSpatial(true);
        rival.setDistances(200f, 2500f);
        scene.createEntity("Rival").addComponent(rival);

        EngineSoundComponent again = serializer.fromJson(serializer.toJson(scene))
            .findEntityByName("Rival").getComponent(EngineSoundComponent.class);

        assertEquals("Sports Coupe", again.getProfile());
        assertEquals(0.6f, again.getVolume());
        assertTrue(again.isSpatial());
        assertEquals(200f, again.getMinDistance());
        assertEquals(2500f, again.getMaxDistance());
    }

    @Test
    void shouldRejectASceneWithAnInvalidEngineVolume() {
        SceneSerializer serializer = serializer();
        Scene scene = new Scene("Race");
        scene.createEntity("Car").addComponent(new EngineSoundComponent());
        String json = serializer.toJson(scene).replace("\"volume\": 1", "\"volume\": 7");

        assertThrows(SceneSerializationException.class, () -> serializer.fromJson(json));
    }
}
