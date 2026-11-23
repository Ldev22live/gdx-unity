package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.TerrainCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A model downloaded from the Asset Library is placed standing on the terrain, at its size, in one undo step, and survives a reopen (UI M7). */
class DownloadedModelPlacementTest {
    private static final String CRATE = "models/thirdparty/polyhaven/wooden_crate_02/wooden_crate_02_2k.gltf";
    private static final float HILL = 250f;
    private static final float TOLERANCE = 0.01f;

    @TempDir
    Path project;

    private EditorState editorState;
    private LevelSession level;
    private Scene scene;
    private SceneSerializer serializer;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new TerrainComponentSerializer());
        registry.register(new TerrainCollider3DComponentSerializer());
        registry.register(new ModelComponentSerializer());
        serializer = new SceneSerializer(registry);
        TerrainLibrary library = new TerrainLibrary(AssetSource.directory(project.toFile()));
        editorState = new EditorState(serializer);
        scene = new Scene("Level");
        editorState.setActiveScene(scene);
        level = new LevelSession(editorState, new TerrainSession(library), project.toFile());
    }

    /** A level whose whole ground is raised to {@link #HILL}. */
    private Entity raisedGround() {
        Entity ground = level.createLevel(Surface.GRASS);
        Terrain terrain = level.findTerrain();
        for (int iz = 0; iz < terrain.getResolutionZ(); iz++) {
            for (int ix = 0; ix < terrain.getResolutionX(); ix++) {
                terrain.setHeight(ix, iz, HILL);
            }
        }
        return ground;
    }

    @Test
    void shouldStandADownloadedModelOnTheGroundAtItsSizeSelectedInOneUndoStep() {
        raisedGround();
        int before = scene.getAllEntities().size();

        Entity crate = level.placeModelOnGround(CRATE, 30f, -40f, 0f);

        assertNotNull(crate);
        assertEquals("wooden_crate_02_2k", crate.getName());
        assertEquals(30f, crate.getTransform().getWorldPosition().x, TOLERANCE);
        assertEquals(HILL, crate.getTransform().getWorldPosition().y, TOLERANCE, "standing on the raised ground, not at the view's height");
        assertEquals(-40f, crate.getTransform().getWorldPosition().z, TOLERANCE);
        ModelComponent model = crate.getComponent(ModelComponent.class);
        assertEquals(CRATE, model.getModelPath());
        assertEquals(ModelComponent.DEFAULT_IMPORT_SCALE, model.getImportScale(), "glTF metres to scene units");
        assertSame(crate, editorState.getSelectedEntity());
        assertTrue(editorState.getStatusMessage().contains("on the ground"), editorState.getStatusMessage());

        assertTrue(editorState.undo());
        assertEquals(before, scene.getAllEntities().size(), "one undo step removes it");
    }

    @Test
    void shouldFollowAGroundThatWasMoved() {
        Entity ground = raisedGround();
        ground.getTransform().setLocalPosition(1000f, 100f, 0f);

        Entity crate = level.placeModelOnGround(CRATE, 1000f, 0f, 0f);

        assertEquals(HILL + 100f, crate.getTransform().getWorldPosition().y, TOLERANCE);
    }

    @Test
    void shouldSurviveSaveAndReopen() {
        raisedGround();
        Entity crate = level.placeModelOnGround(CRATE, 30f, -40f, 0f);

        Scene reopened = serializer.fromJson(serializer.toJson(scene));

        Entity again = reopened.getAllEntities().stream().filter(e -> e.getId().equals(crate.getId())).findFirst().orElseThrow();
        assertEquals(CRATE, again.getComponent(ModelComponent.class).getModelPath());
        assertEquals(HILL, again.getTransform().getWorldPosition().y, TOLERANCE);
        assertEquals(ModelComponent.DEFAULT_IMPORT_SCALE, again.getComponent(ModelComponent.class).getImportScale());
    }

    @Test
    void shouldSayWhenThereIsNoGroundOrThePointIsBeyondIt() {
        Entity noGround = level.placeModelOnGround(CRATE, 5f, 6f, 70f);
        assertEquals(70f, noGround.getTransform().getWorldPosition().y, TOLERANCE);
        assertTrue(editorState.getStatusMessage().contains("this scene has no ground"), editorState.getStatusMessage());

        raisedGround();
        Entity beyond = level.placeModelOnGround(CRATE, 1_000_000f, 0f, 70f);
        assertEquals(70f, beyond.getTransform().getWorldPosition().y, TOLERANCE);
        assertTrue(editorState.getStatusMessage().contains("beyond the edge of the ground"), editorState.getStatusMessage());
    }

    @Test
    void shouldRefuseAFileThatIsNotAModel() {
        int before = scene.getAllEntities().size();

        assertNull(level.placeModelOnGround("textures/thirdparty/polyhaven/red_brick_03/red_brick_03_diff_2k.jpg", 0f, 0f, 0f));

        assertEquals(before, scene.getAllEntities().size());
        assertFalse(editorState.getStatusMessage().isEmpty());
    }
}
