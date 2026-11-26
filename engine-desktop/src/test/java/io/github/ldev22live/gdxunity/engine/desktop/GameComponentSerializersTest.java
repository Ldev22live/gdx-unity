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
        assertEquals(44, all.size(), () -> "registered: " + all.keySet());
        assertNotNull(registry.getByTypeId("OwnerComponent"), "which scenario player owns an entity (TD-106)");
        assertNotNull(registry.getByTypeId(PrefabInstanceComponentSerializer.TYPE_ID));
        assertNotNull(registry.getByTypeId(DynamicMeshComponentSerializer.TYPE_ID), "sculpts and other editable meshes");
        assertNotNull(registry.getByTypeId(AudioSourceComponentSerializer.TYPE_ID), "sound effects");
        assertNotNull(registry.getByTypeId("MusicSource"), "songs in scenes");
        assertNotNull(registry.getByTypeId("AudioListener"), "positional sound");
        assertNotNull(registry.getByTypeId("EngineSound"), "running engines");
        assertNotNull(registry.getByTypeId("Terrain"), "heightmap ground");
        assertNotNull(registry.getByTypeId("TerrainPath"), "roads, rivers and cliffs on the ground (roadmap M3.7)");
        assertNotNull(registry.getByTypeId("TerrainCollider3DComponent"), "ground that characters stand on (TD-087)");
        assertNotNull(registry.getByTypeId("PlayerComponent"), "the entity a genre profile controls (TD-088)");
        assertNotNull(registry.getByTypeId("AtmosphereComponent"), "sky, fog and weather (roadmap M3.9)");
        assertNotNull(registry.getByTypeId("WaterBodyComponent"), "lakes and seas (roadmap M3.9)");
        assertNotNull(registry.getByTypeId("PostProcessVolumeComponent"), "bloom, grading, occlusion, depth of field (roadmap M6.2)");
        assertNotNull(registry.getByTypeId("NavMeshBoundsComponent"), "where agents find their way (roadmap M6.4)");
        assertNotNull(registry.getByTypeId("NavAgent3DComponent"), "agents that walk round obstacles (roadmap M6.4)");
    }
}
