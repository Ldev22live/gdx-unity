package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap A13: prefabs placed by the web World shell's palettes are linked instances the desktop treats exactly like its own. The
 * scene is the committed step {@code fixtures/contract-v1/interop/w1-web} that {@code engine-babylon/test/worldShell.test.ts}
 * writes (the CC0 Oak Tree and the CC0 House); here the desktop's {@link PrefabService} syncs it against the shipped prefabs.
 */
class WebPlacedPrefabsTest {
    private static final Path SCENE = Path.of("..", "fixtures", "contract-v1", "interop", "w1-web", "scenes", "web-placed.json");

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldReadWebPlacedPrefabsAsCleanLinkedInstances() throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        SceneSerializer serializer = new SceneSerializer(registry);
        EditorState editorState = new EditorState(serializer);
        Scene scene = serializer.fromJson(Files.readString(SCENE));
        editorState.setActiveScene(scene);
        PrefabService prefabs = new PrefabService(editorState, new File("../assets"));
        editorState.setPrefabService(prefabs);
        int entities = scene.getAllEntities().size();

        assertEquals(List.of(), prefabs.syncScene(scene), "both prefabs are found and read");

        assertEquals(entities, scene.getAllEntities().size(), "sync creates and removes nothing: the web placed every part");
        List<Entity> roots = scene.getAllEntities().stream()
            .filter(e -> PrefabService.linkOf(e) != null && PrefabService.linkOf(e).isRoot()).toList();
        assertEquals(List.of("CC0 Oak Tree", "CC0 House"), roots.stream().map(Entity::getName).toList());
        for (Entity entity : scene.getAllEntities()) {
            PrefabInstanceComponent link = PrefabService.linkOf(entity);
            assertNotNull(link, entity.getName() + " is linked");
            assertTrue(prefabs.getOverrides(entity).isEmpty(), entity.getName() + " has no overrides: " + prefabs.getOverrides(entity));
        }
        assertEquals(400f, roots.get(1).getTransform().getLocalPosition().x, 0.001f, "the web's placement is kept");
        assertEquals(-300f, roots.get(1).getTransform().getLocalPosition().z, 0.001f);
    }
}
