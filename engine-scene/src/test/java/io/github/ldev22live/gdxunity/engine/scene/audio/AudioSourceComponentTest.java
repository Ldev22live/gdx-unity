package io.github.ldev22live.gdxunity.engine.scene.audio;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioSourceComponentTest {

    /** Records every sound sent to it as "sound@volume@pitch". */
    private static final class RecordingSink implements SoundSink {
        final List<String> played = new ArrayList<>();

        @Override
        public void play(String sound, float volume, float pitch) {
            played.add(sound + "@" + volume + "@" + pitch);
        }
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new AudioSourceComponentSerializer());
        return new SceneSerializer(registry);
    }

    @Test
    void shouldDoNothingWhenPlayedWhileUnbound() {
        AudioSourceComponent source = new AudioSourceComponent("jump", "Jump");

        source.play();

        assertFalse(source.isBound());
    }

    @Test
    void shouldSendItsSoundVolumeAndPitchToTheBoundSink() {
        RecordingSink sink = new RecordingSink();
        AudioSourceComponent source = new AudioSourceComponent("hit", "Hurt");
        source.setVolume(0.5f);
        source.setPitch(2f);
        source.bind(sink);

        source.play();
        source.unbind();
        source.play();

        assertEquals(List.of("Hurt@0.5@2.0"), sink.played);
    }

    @Test
    void shouldNotPlayWhileDisabled() {
        RecordingSink sink = new RecordingSink();
        AudioSourceComponent source = new AudioSourceComponent("hit", "Hurt");
        source.bind(sink);
        source.setEnabled(false);

        source.play();

        assertTrue(sink.played.isEmpty());
    }

    @Test
    void shouldPlayOnStartOnlyWhenAskedTo() {
        RecordingSink sink = new RecordingSink();
        Scene scene = new Scene("Start");
        AudioSourceComponent quiet = new AudioSourceComponent("a", "Blip");
        AudioSourceComponent loud = new AudioSourceComponent("b", "Powerup");
        loud.setPlayOnStart(true);
        scene.createEntity("Quiet").addComponent(quiet);
        scene.createEntity("Loud").addComponent(loud);
        quiet.bind(sink);
        loud.bind(sink);

        scene.update(0.016f);

        assertEquals(List.of("Powerup@1.0@1.0"), sink.played);
    }

    @Test
    void shouldPlayEveryEnabledSourceWithTheCueOnActiveEntities() {
        RecordingSink sink = new RecordingSink();
        Scene scene = new Scene("Cues");
        Entity first = scene.createEntity("First");
        Entity second = scene.createEntity("Second");
        Entity hidden = scene.createEntity("Hidden");
        Entity other = scene.createEntity("Other");
        first.addComponent(new AudioSourceComponent("coin", "Coin"));
        second.addComponent(new AudioSourceComponent("coin", "Blip"));
        hidden.addComponent(new AudioSourceComponent("coin", "Laser"));
        other.addComponent(new AudioSourceComponent("jump", "Jump"));
        hidden.setActive(false);
        for (Entity entity : scene.getAllEntities()) {
            entity.getComponent(AudioSourceComponent.class).bind(sink);
        }

        int played = AudioSourceComponent.play(scene, "coin");

        assertEquals(2, played);
        assertEquals(List.of("Coin@1.0@1.0", "Blip@1.0@1.0"), sink.played);
        assertEquals(0, AudioSourceComponent.play(scene, "missing"));
    }

    @Test
    void shouldRejectInvalidValues() {
        AudioSourceComponent source = new AudioSourceComponent();

        assertThrows(IllegalArgumentException.class, () -> source.setCue(" "));
        assertThrows(IllegalArgumentException.class, () -> source.setSound(null));
        assertThrows(IllegalArgumentException.class, () -> source.setVolume(1.1f));
        assertThrows(IllegalArgumentException.class, () -> source.setVolume(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> source.setPitch(0.1f));
        assertThrows(IllegalArgumentException.class, () -> source.setPitch(5f));
    }

    @Test
    void shouldRoundTripThroughSceneSerialization() {
        Scene scene = new Scene("Audio");
        Entity entity = scene.createEntity("Sfx");
        AudioSourceComponent source = new AudioSourceComponent("shoot", "Laser");
        source.setVolume(0.4f);
        source.setPitch(1.5f);
        source.setPlayOnStart(true);
        entity.addComponent(source);

        SceneSerializer serializer = serializer();
        Scene loaded = serializer.fromJson(serializer.toJson(scene));
        AudioSourceComponent read = loaded.findEntityById(entity.getId()).getComponent(AudioSourceComponent.class);

        assertEquals("shoot", read.getCue());
        assertEquals("Laser", read.getSound());
        assertEquals(0.4f, read.getVolume());
        assertEquals(1.5f, read.getPitch());
        assertTrue(read.isPlayOnStart());
    }

    @Test
    void shouldRejectASceneWithAnOutOfRangeVolume() {
        String json = "{\"schemaVersion\":1,\"name\":\"S\",\"entities\":[{\"id\":\"" + UUID.randomUUID()
            + "\",\"name\":\"E\",\"components\":[{\"type\":\"AudioSource\",\"data\":{\"volume\":3}}]}]}";

        assertThrows(SceneSerializationException.class, () -> serializer().fromJson(json));
    }
}
