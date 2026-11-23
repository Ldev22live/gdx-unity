package io.github.ldev22live.gdxunity.editor.core.action;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PrefabActionsTest {

    @TempDir
    Path assetRoot;

    private EditorState editorState;
    private EditorActionRegistry registry;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new MeshPrimitiveComponentSerializer());
        registry = new EditorActionRegistry();
        DefaultEditorActions.registerDefaults(registry, editorState);
    }

    @Test
    void shouldDisablePrefabActionsWithoutPrefabServiceOrSelection() {
        Entity box = editorState.getActiveScene().createEntity("Box");
        editorState.setSelectedEntity(box);
        assertFalse(registry.execute(DefaultEditorActions.PREFAB_CREATE), "No asset folder, no prefabs");

        editorState.setPrefabService(new PrefabService(editorState, assetRoot.toFile()));
        editorState.clearSelection();
        assertFalse(registry.execute(DefaultEditorActions.PREFAB_CREATE));
    }

    @Test
    void shouldCreateThenApplyRevertAndUnpackFromTheSelection() {
        editorState.setPrefabService(new PrefabService(editorState, assetRoot.toFile()));
        Entity box = editorState.getActiveScene().createEntity("Box");
        box.addComponent(new MeshPrimitiveComponent());
        editorState.setSelectedEntity(box);

        assertFalse(registry.execute(DefaultEditorActions.PREFAB_APPLY), "Not an instance yet");
        assertTrue(registry.execute(DefaultEditorActions.PREFAB_CREATE));
        assertTrue(Files.exists(assetRoot.resolve("prefabs/Box.prefab")));

        Color prefabColor = new Color(box.getComponent(MeshPrimitiveComponent.class).getColor());
        box.getComponent(MeshPrimitiveComponent.class).setColor(Color.RED);
        assertTrue(registry.execute(DefaultEditorActions.PREFAB_REVERT));
        assertEquals(prefabColor, box.getComponent(MeshPrimitiveComponent.class).getColor());

        assertTrue(registry.execute(DefaultEditorActions.PREFAB_UNPACK));
        assertNull(PrefabService.linkOf(box));
        assertFalse(registry.execute(DefaultEditorActions.PREFAB_APPLY));
    }
}
