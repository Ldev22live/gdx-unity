package io.github.ldev22live.gdxunity.editor.core.asset;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssetPlacementTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldPlaceTextureAsSelectedSpriteEntityInOneUndoStep() {
        EditorState editorState = new EditorState();
        int before = editorState.getActiveScene().getAllEntities().size();

        AssetPlacement.Result result = AssetPlacement.place(editorState, new AssetReference("textures/crate.png"), new Vector3(10f, 20f, 0f));
        assertEquals(AssetPlacement.Result.PLACED, result);

        Entity placed = editorState.getSelectedEntity();
        assertEquals("crate", placed.getName());
        assertEquals(new Vector3(10f, 20f, 0f), placed.getTransform().getWorldPosition());
        SpriteRendererComponent sprite = placed.getComponent(SpriteRendererComponent.class);
        assertEquals("textures/crate.png", sprite.getAssetReference().getPath());

        editorState.undo();
        assertEquals(before, editorState.getActiveScene().getAllEntities().size());
    }

    @Test
    void shouldRefuseModelsWithAnExplanationUntilTheLoaderExists() {
        EditorState editorState = new EditorState();
        int before = editorState.getActiveScene().getAllEntities().size();

        AssetPlacement.Result result = AssetPlacement.place(editorState, new AssetReference("models/robot.glb"), new Vector3());
        assertEquals(AssetPlacement.Result.UNSUPPORTED_TYPE, result);
        assertEquals(before, editorState.getActiveScene().getAllEntities().size());
        assertTrue(editorState.isStatusError());
        assertTrue(editorState.getStatusMessage().contains("robot.glb"));
    }

    @Test
    void shouldRecognizeModelFilesByExtension() {
        assertEquals(AssetType.MODEL, AssetType.fromPath("models/robot.GLB"));
        assertEquals(AssetType.MODEL, AssetType.fromPath("a/b/scene.gltf"));
        assertEquals(AssetType.TEXTURE, AssetType.fromPath("crate.png"));
        assertEquals(AssetType.UNKNOWN, AssetType.fromPath("folder.d/noextension"));
        assertEquals(AssetType.UNKNOWN, AssetType.fromPath("trailingdot."));
        assertEquals("crate", AssetPlacement.baseName("crate.png"));
        assertEquals(".hidden", AssetPlacement.baseName(".hidden"));
    }
}
