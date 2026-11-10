package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.render.components.serialization.DynamicMeshComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GameComponentSerializersTest {

    @Test
    void shouldRegisterEveryEngineComponentTypeIncludingGameAndPrefabComponents() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();

        GameComponentSerializers.registerAll(registry);

        Map<String, ?> all = registry.getAll();
        assertEquals(32, all.size(), () -> "registered: " + all.keySet());
        assertNotNull(registry.getByTypeId(PrefabInstanceComponentSerializer.TYPE_ID));
        assertNotNull(registry.getByTypeId(DynamicMeshComponentSerializer.TYPE_ID), "sculpts and other editable meshes");
        assertNotNull(registry.getByTypeId(AudioSourceComponentSerializer.TYPE_ID), "sound effects");
        assertNotNull(registry.getByTypeId("MusicSource"), "songs in scenes");
        assertNotNull(registry.getByTypeId("AudioListener"), "positional sound");
    }
}
