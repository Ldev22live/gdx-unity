package io.github.ldev22live.gdxunity.editor.core.asset;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
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
    void shouldPlaceModelAsSelectedModelEntityInOneUndoStep() {
        EditorState editorState = new EditorState();
        int before = editorState.getActiveScene().getAllEntities().size();

        AssetPlacement.Result result = AssetPlacement.place(editorState, new AssetReference("models/robot.glb"), new Vector3(0f, 0f, 50f));
        assertEquals(AssetPlacement.Result.PLACED, result);

        Entity placed = editorState.getSelectedEntity();
        assertEquals("robot", placed.getName());
        assertEquals(new Vector3(0f, 0f, 50f), placed.getTransform().getWorldPosition());
        assertEquals("models/robot.glb", placed.getComponent(ModelComponent.class).getModelPath());

        editorState.undo();
        assertEquals(before, editorState.getActiveScene().getAllEntities().size());
    }

    @Test
    void shouldPickModelByItsLoadedBoundsTimesImportScale() {
        EditorState editorState = new EditorState();
        Entity entity = editorState.getActiveScene().createEntity("Hero");
        ModelComponent model = entity.addComponent(new ModelComponent("models/hero.glb"));
        assertNull(ViewportPicker.computeMeshLocalBounds(entity), "nothing to pick before the model is loaded");

        model.setModelInfo(new ModelComponent.ModelInfo(java.util.Map.of(),
            new BoundingBox(new Vector3(-0.5f, 0f, -0.5f), new Vector3(0.5f, 2f, 0.5f)), 0, null));

        BoundingBox bounds = ViewportPicker.computeMeshLocalBounds(entity);
        assertEquals(200f, bounds.max.y, 1e-4f);
        assertEquals(-50f, bounds.min.x, 1e-4f);
        assertEquals(entity, ViewportPicker.pickEntity3D(editorState.getActiveScene(),
            new Ray(new Vector3(0f, 100f, 500f), new Vector3(0f, 0f, -1f))));
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
