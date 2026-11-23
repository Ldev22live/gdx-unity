package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.TerrainCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** New terrains are walkable without a setup step (roadmap M3.3, TD-087), and the collider comes and goes with one undo step. */
class TerrainCollisionTest {
    @TempDir
    Path project;

    private EditorState editorState;
    private TerrainLibrary library;
    private Scene scene;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        library = new TerrainLibrary(AssetSource.directory(project.toFile()));
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new TerrainComponentSerializer());
        registry.register(new TerrainCollider3DComponentSerializer());
        editorState = new EditorState(new SceneSerializer(registry));
        scene = new Scene("Ground");
        editorState.setActiveScene(scene);
    }

    @Test
    void shouldGiveANewLevelGroundACollider() {
        LevelSession level = new LevelSession(editorState, new TerrainSession(library), project.toFile());

        Entity ground = level.createLevel(Surface.GRASS);

        assertNotNull(ground.getComponent(TerrainCollider3DComponent.class), "the level ground is walkable");
        assertTrue(editorState.undo(), "one undo step");
        assertNull(scene.findEntityById(ground.getId()), "undo removes the ground and its collider together");
    }

    @Test
    void shouldAddTheColliderWithANewTerrainInOneUndoStep() {
        Entity hills = editorState.createEntity("Hills");
        TerrainComponent terrain = new TerrainComponent();
        hills.addComponent(terrain);
        int steps = editorState.getCommandHistory().getUndoCount();
        String path = TerrainAssets.createNew(project.toFile(), library, "Hills", false);

        assertTrue(TerrainCollision.assignTerrain(editorState, hills, terrain, path));

        assertEquals(path, terrain.getTerrainPath());
        assertNotNull(hills.getComponent(TerrainCollider3DComponent.class));
        assertEquals(steps + 1, editorState.getCommandHistory().getUndoCount(), "one undo step");
        assertTrue(editorState.undo());
        assertEquals("", terrain.getTerrainPath());
        assertNull(hills.getComponent(TerrainCollider3DComponent.class), "the collider goes with the terrain it came with");
        assertTrue(editorState.redo());
        assertNotNull(hills.getComponent(TerrainCollider3DComponent.class));
    }

    @Test
    void shouldNotAddASecondColliderOrOneThatCouldNotWork() {
        Entity ground = editorState.createEntity("Ground");
        TerrainComponent terrain = new TerrainComponent();
        ground.addComponent(terrain);
        ground.addComponent(new TerrainCollider3DComponent());
        TerrainCollision.assignTerrain(editorState, ground, terrain, TerrainAssets.createNew(project.toFile(), library, "A", true));
        assertEquals(1, ground.getComponents(TerrainCollider3DComponent.class).size(), "already has one");

        Entity raft = editorState.createEntity("Raft");
        raft.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
        Entity hero = editorState.createEntity("Hero");
        hero.addComponent(new CharacterController3DComponent());
        assertFalse(TerrainCollision.needsCollider(raft), "the ground does not move");
        assertFalse(TerrainCollision.needsCollider(hero), "a character is its own body");
        Entity rock = editorState.createEntity("Rock");
        rock.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.STATIC));
        assertTrue(TerrainCollision.needsCollider(rock), "a static body is fine");
    }

    @Test
    void shouldChangeNothingForTheSamePathOrDuringPlay() {
        Entity ground = editorState.createEntity("Ground");
        TerrainComponent terrain = new TerrainComponent("terrains/a.terrain");
        ground.addComponent(terrain);
        assertFalse(TerrainCollision.assignTerrain(editorState, ground, terrain, "terrains/a.terrain"), "same path");

        editorState.setPlayModeController(new PlayModeController(editorState));
        assertTrue(editorState.getPlayModeController().play());
        Entity playing = editorState.getActiveScene().findEntityByName("Ground");
        TerrainComponent playingTerrain = playing.getComponent(TerrainComponent.class);
        assertFalse(TerrainCollision.assignTerrain(editorState, playing, playingTerrain, "terrains/b.terrain"), "refused during Play");
        assertNull(playing.getComponent(TerrainCollider3DComponent.class));
        editorState.getPlayModeController().stop();
    }
}
