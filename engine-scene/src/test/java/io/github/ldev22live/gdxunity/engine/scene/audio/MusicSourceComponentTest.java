package io.github.ldev22live.gdxunity.engine.scene.audio;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSourceComponentTest {

    /** Records "start:<song>" and "stop:<song>" for every request. */
    private static final class RecordingSink implements MusicSink {
        final List<String> requests = new ArrayList<>();

        @Override
        public void start(MusicSourceComponent source) {
            requests.add("start:" + source.getSong());
        }

        @Override
        public void stop(MusicSourceComponent source) {
            requests.add("stop:" + source.getSong());
        }
    }

    @Test
    void shouldDoNothingWhileUnboundOrDisabled() {
        RecordingSink sink = new RecordingSink();
        MusicSourceComponent source = new MusicSourceComponent("music/demo.song");

        source.play();
        source.stop();
        assertFalse(source.isBound());

        source.bind(sink);
        source.setEnabled(false);
        source.play();
        assertTrue(sink.requests.isEmpty(), "a disabled source does not start");
        source.stop();
        assertEquals(List.of("stop:music/demo.song"), sink.requests, "but it can always be stopped");
    }

    @Test
    void shouldStartByItselfWhenTheSceneStartsUnlessToldNotTo() {
        RecordingSink sink = new RecordingSink();
        Scene scene = new Scene("Music");
        MusicSourceComponent auto = new MusicSourceComponent("music/a.song");
        MusicSourceComponent manual = new MusicSourceComponent("music/b.song");
        manual.setPlayOnStart(false);
        scene.createEntity("Auto").addComponent(auto);
        scene.createEntity("Manual").addComponent(manual);
        auto.bind(sink);
        manual.bind(sink);

        scene.update(0.016f);

        assertEquals(List.of("start:music/a.song"), sink.requests);
    }

    @Test
    void shouldRejectInvalidValues() {
        MusicSourceComponent source = new MusicSourceComponent();

        assertThrows(IllegalArgumentException.class, () -> source.setSong(null));
        assertThrows(IllegalArgumentException.class, () -> source.setVolume(1.5f));
        assertThrows(IllegalArgumentException.class, () -> source.setVolume(Float.NaN));
    }

    @Test
    void shouldRoundTripThroughSceneSerialization() {
        Scene scene = new Scene("Music");
        Entity entity = scene.createEntity("Band");
        MusicSourceComponent source = new MusicSourceComponent("music/neon-drive.song");
        source.setVolume(0.3f);
        source.setLoop(false);
        source.setPlayOnStart(false);
        entity.addComponent(source);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new MusicSourceComponentSerializer());
        SceneSerializer serializer = new SceneSerializer(registry);

        MusicSourceComponent read = serializer.fromJson(serializer.toJson(scene))
            .findEntityById(entity.getId()).getComponent(MusicSourceComponent.class);

        assertEquals("music/neon-drive.song", read.getSong());
        assertEquals(0.3f, read.getVolume(), 1e-6f);
        assertFalse(read.isLoop());
        assertFalse(read.isPlayOnStart());
    }
}
