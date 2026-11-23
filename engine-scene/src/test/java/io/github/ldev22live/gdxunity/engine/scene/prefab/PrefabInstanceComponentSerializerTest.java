package io.github.ldev22live.gdxunity.engine.scene.prefab;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrefabInstanceComponentSerializerTest {

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new PrefabInstanceComponentSerializer());
        return new SceneSerializer(registry);
    }

    @Test
    void shouldRoundTripRootLinkWithOverridesAndKnownIds() {
        UUID source = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Scene scene = new Scene("Prefabs");
        Entity entity = scene.createEntity("Crate");
        PrefabInstanceComponent link = new PrefabInstanceComponent("prefabs/Crate.prefab", source, true);
        link.setOverrides(new LinkedHashSet<>(List.of("transform/scale/x", "component/MeshPrimitiveComponent#0")));
        link.setKnownSourceIds(new LinkedHashSet<>(List.of(source, other)));
        entity.addComponent(link);

        SceneSerializer serializer = serializer();
        Scene loaded = serializer.fromJson(serializer.toJson(scene));
        PrefabInstanceComponent read = loaded.findEntityById(entity.getId()).getComponent(PrefabInstanceComponent.class);

        assertEquals("prefabs/Crate.prefab", read.getPrefabPath());
        assertEquals(source, read.getSourceId());
        assertTrue(read.isRoot());
        assertEquals(List.of("transform/scale/x", "component/MeshPrimitiveComponent#0"), List.copyOf(read.getOverrides()));
        assertEquals(Set.of(source, other), read.getKnownSourceIds());
    }

    @Test
    void shouldRejectLinkWithInvalidSourceId() {
        String json = "{\"schemaVersion\":1,\"name\":\"S\",\"entities\":[{\"id\":\"" + UUID.randomUUID()
            + "\",\"name\":\"E\",\"components\":[{\"type\":\"PrefabInstance\",\"data\":"
            + "{\"prefab\":\"prefabs/A.prefab\",\"sourceId\":\"not-a-uuid\"}}]}]}";
        assertThrows(SceneSerializationException.class, () -> serializer().fromJson(json));
    }
}
