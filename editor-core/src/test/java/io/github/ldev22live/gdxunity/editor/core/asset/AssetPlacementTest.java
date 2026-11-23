package io.github.ldev22live.gdxunity.editor.core.asset;

import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteFile;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
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
    void shouldPlaceASpriteAsAnEntityThatPlaysItInOneUndoStep() {
        EditorState editorState = new EditorState();
        int before = editorState.getActiveScene().getAllEntities().size();

        AssetPlacement.Result result = AssetPlacement.place(editorState, new AssetReference("sprites/hero.sprite"), new Vector3(5f, 6f, 0f));

        assertEquals(AssetPlacement.Result.PLACED, result);
        Entity placed = editorState.getSelectedEntity();
        assertEquals("hero", placed.getName());
        assertEquals("sprites/hero.sprite", placed.getComponent(io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent.class)
            .getAssetReference().getPath());
        editorState.undo();
        assertEquals(before, editorState.getActiveScene().getAllEntities().size());
    }

    /**
     * Roadmap M3c.8: in a 2.5D or 3D world a dropped sprite gets a size that can be seen beside the characters (its longer side
     * {@link AssetPlacement#DROPPED_SPRITE_SIZE}, its shape kept); a 2D scene keeps the pixel size; a sprite that cannot be read is
     * placed at its pixel size and the status bar says why.
     */
    @Test
    void shouldSizeADroppedSpriteForA25DOr3DWorldKeepingItsShape(@TempDir Path project)
        throws IOException {
        Files.createDirectories(project.resolve("sprites"));
        Files.writeString(project.resolve("sprites/banner.sprite"),
            SpriteFile.write(new SpriteDocument(16, 8)));
        for (GameMode mode : GameMode.values()) {
            EditorState editorState = new EditorState();
            editorState.getActiveScene().setGameMode(mode);
            editorState.setAssetService(new AssetService(
                AssetSource.directory(project.toFile())));

            assertEquals(AssetPlacement.Result.PLACED,
                AssetPlacement.place(editorState, new AssetReference("sprites/banner.sprite"), new Vector3()));

            var sprite = editorState.getSelectedEntity()
                .getComponent(SpriteAnimatorComponent.class);
            boolean depth = mode != GameMode.MODE_2D;
            assertEquals(depth ? AssetPlacement.DROPPED_SPRITE_SIZE : 0f, sprite.getWidth(), 0.001f, mode + " width");
            assertEquals(depth ? AssetPlacement.DROPPED_SPRITE_SIZE / 2f : 0f, sprite.getHeight(), 0.001f, mode + " height, shape kept");
        }
        EditorState missing = new EditorState();
        missing.getActiveScene().setGameMode(GameMode.MODE_3D);
        missing.setAssetService(new AssetService(
            AssetSource.directory(project.toFile())));
        assertEquals(AssetPlacement.Result.PLACED, AssetPlacement.place(missing, new AssetReference("sprites/gone.sprite"), new Vector3()));
        assertTrue(missing.getStatusMessage().startsWith("Placed gone.sprite at its pixel size: cannot read it"),
            missing.getStatusMessage());
    }

    @Test
    void shouldPlaceASculptAsSelectedEntityShowingThatFileInOneUndoStep() {
        EditorState editorState = new EditorState();
        int before = editorState.getActiveScene().getAllEntities().size();

        AssetPlacement.Result result = AssetPlacement.place(editorState, new AssetReference("sculpts/Rock.sculpt"), new Vector3(5f, 0f, 7f));

        assertEquals(AssetPlacement.Result.PLACED, result);
        Entity placed = editorState.getSelectedEntity();
        assertEquals("Rock", placed.getName());
        assertEquals(new Vector3(5f, 0f, 7f), placed.getTransform().getWorldPosition());
        assertEquals("sculpts/Rock.sculpt",
            placed.getComponent(io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent.class).getMeshPath());
        assertEquals(io.github.ldev22live.gdxunity.engine.core.asset.AssetType.SCULPT,
            io.github.ldev22live.gdxunity.engine.core.asset.AssetType.fromPath("sculpts/Rock.sculpt"));
        editorState.undo();
        assertEquals(before, editorState.getActiveScene().getAllEntities().size());
    }

    @Test
    void shouldPlaceADroppedWavClipAsAnAudioSourceNamedAfterTheFileInOneUndoStep() {
        EditorState editorState = new EditorState();
        int before = editorState.getActiveScene().getAllEntities().size();

        AssetPlacement.Result result = AssetPlacement.place(editorState, new AssetReference("sounds/door-slam.wav"), new Vector3(0f, 0f, 0f));

        assertEquals(AssetPlacement.Result.PLACED, result);
        Entity placed = editorState.getSelectedEntity();
        assertEquals("door-slam", placed.getName());
        io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent source =
            placed.getComponent(io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent.class);
        assertEquals("sounds/door-slam.wav", source.getSound());
        assertEquals("door-slam", source.getCue());
        assertTrue(source.isClip());
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
