package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeState;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.CameraComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.play.GroundWalkMover;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CharacterController3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.TerrainCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.PlayerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.SpawnPointComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3c.2: the World strip's Characters places the shipped Player and Spawn Point prefabs on a level, and pressing Play starts
 * the player at the spawn point, or names what is missing. Real prefab files, real Jolt (editor Play).
 */
class CharacterPlacementTest {
    private static final String PLAYER = LevelSession.CHARACTER_FOLDER + "/Player.prefab";
    private static final String SPAWN = LevelSession.CHARACTER_FOLDER + "/Spawn Point.prefab";
    /** The Player prefab's capsule: radius 30, height 80, so its centre stands 70 above the ground, placed 1 above that. */
    private static final float STANDING = 71f;
    private static final float EPSILON = 0.01f;

    @TempDir
    Path project;

    private TerrainLibrary library;
    private EditorState editorState;
    private LevelSession level;
    private Scene scene;
    private SceneSerializer serializer;
    private PlayModeController play;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws IOException {
        // The shipped prefabs, in a project of their own
        Path characters = Files.createDirectories(project.resolve(LevelSession.CHARACTER_FOLDER));
        for (String name : List.of("Player.prefab", "Spawn Point.prefab")) {
            Files.copy(Path.of("..", "assets", LevelSession.CHARACTER_FOLDER, name), characters.resolve(name),
                StandardCopyOption.REPLACE_EXISTING);
        }
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new CameraComponentSerializer());
        registry.register(new MeshPrimitiveComponentSerializer());
        registry.register(new TerrainComponentSerializer());
        registry.register(new TerrainCollider3DComponentSerializer());
        registry.register(new CharacterController3DComponentSerializer());
        registry.register(new PlayerComponentSerializer());
        registry.register(new SpawnPointComponentSerializer());
        registry.register(new PrefabInstanceComponentSerializer());
        serializer = new SceneSerializer(registry);
        library = new TerrainLibrary(AssetSource.directory(project.toFile()));
        editorState = new EditorState(serializer);
        scene = new Scene("Level");
        scene.setGameMode(GameMode.MODE_3D);
        // The editor's renderer gives the scene its terrains; here the test does
        scene.setHeightFields(new TerrainHeightFields(library));
        editorState.setActiveScene(scene);
        editorState.setPrefabService(new PrefabService(editorState, project.toFile()));
        level = new LevelSession(editorState, new TerrainSession(library), project.toFile());
        play = new PlayModeController(editorState);
        play.getProfileStep().setInput(key -> false);
        editorState.setPlayModeController(play);
    }

    @AfterEach
    void tearDown() {
        play.stop();
    }

    private Entity place(String path, float x, float z) {
        level.setObjectPath(path);
        return level.placeObject(x, 0f, z); // the new level is flat at height 0
    }

    @Test
    void shouldOfferThePlayerAndTheSpawnPointUnderCharactersWithOnlyPlace() {
        level.setFocus(LevelSession.Focus.CHARACTERS);

        assertEquals(List.of(PLAYER, SPAWN), level.listObjects());
        assertEquals(LevelSession.Tool.PLACE, level.getTool());
        for (LevelSession.Tool tool : LevelSession.Tool.values()) {
            assertEquals(tool == LevelSession.Tool.PLACE, LevelSession.Focus.CHARACTERS.offers(tool), tool.toString());
        }
        level.setObjectPath(PLAYER);
        level.setFocus(LevelSession.Focus.OBJECTS);
        assertEquals("", level.getObjectPath(), "a character is not one of the objects");
    }

    @Test
    void shouldPlaceCharactersStandingFacingForwardAndOnlyOneEach() {
        level.createLevel(Surface.GRASS);
        level.setFocus(LevelSession.Focus.CHARACTERS);
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        Entity player = place(PLAYER, 200f, 300f);
        Entity spawn = place(SPAWN, -400f, 100f);

        assertNotNull(player.getComponent(PlayerComponent.class));
        assertNotNull(player.getComponent(CharacterController3DComponent.class));
        assertEquals(new Vector3(200f, STANDING, 300f), player.getTransform().getWorldPosition(), "lifted to stand on the ground");
        assertEquals(0f, player.getTransform().getWorldRotation().getYaw(), EPSILON, "never turned at random");
        assertEquals(new Vector3(1f, 1f, 1f), player.getTransform().getLocalScale(), "never scaled at random");
        assertNotNull(spawn.getComponent(SpawnPointComponent.class));
        assertEquals(new Vector3(-400f, 0f, 100f), spawn.getTransform().getWorldPosition(), "a spawn point lies on the ground");
        assertEquals(undoSteps + 2, editorState.getCommandHistory().getUndoCount(), "one undo step each");

        assertNull(place(PLAYER, 0f, 0f), "a scene has one player");
        assertTrue(editorState.getStatusMessage().startsWith("Player is already the player"), editorState.getStatusMessage());
        assertNull(place(SPAWN, 0f, 0f), "a scene has one spawn point");
        assertTrue(editorState.getStatusMessage().startsWith("Spawn Point is already the spawn point"), editorState.getStatusMessage());
    }

    @Test
    void shouldPlayFromTheSpawnPointAfterPlacingBothAndRestoreOnStop() {
        level.createLevel(Surface.GRASS);
        Entity camera = editorState.createEntity("Main Camera");
        camera.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        assertTrue(editorState.setGameGenre(GameGenres.ACTION_RPG));
        level.setFocus(LevelSession.Focus.CHARACTERS);
        Entity player = place(PLAYER, 200f, 300f);
        Entity spawn = place(SPAWN, -400f, 100f);
        spawn.getTransform().setLocalEulerAngles(90f, 0f, 0f); // face -X
        String before = serializer.toJson(scene);

        assertTrue(play.play(), editorState.getStatusMessage());

        assertTrue(editorState.getStatusMessage().contains("Player starts at Spawn Point."), editorState.getStatusMessage());
        Entity playing = play.getRuntimeScene().findEntityById(player.getId());
        assertNotNull(playing.getComponent(GroundWalkMover.class));
        Vector3 start = playing.getTransform().getWorldPosition();
        assertEquals(-400f, start.x, EPSILON);
        assertEquals(100f, start.z, EPSILON);
        assertEquals(STANDING, start.y, EPSILON, "standing on the spawn point");
        assertEquals(90f, playing.getTransform().getWorldRotation().getYaw(), 0.5f, "facing the way the spawn point faces");
        for (int frame = 0; frame < 60; frame++) {
            play.update(1f / 60f);
        }
        assertTrue(play.getRuntimeScene().getPhysics3D().isCharacterGrounded(playing), "stands on the level");

        play.stop();
        assertEquals(PlayModeState.STOPPED, play.getState());
        assertEquals(before, serializer.toJson(scene), "the authored player is where it was placed");
    }

    @Test
    void shouldNameWhatIsMissingWhenOnlyTheSpawnPointIsPlaced() {
        level.createLevel(Surface.GRASS);
        Entity camera = editorState.createEntity("Main Camera");
        camera.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        editorState.setGameGenre(GameGenres.ACTION_RPG);
        level.setFocus(LevelSession.Focus.CHARACTERS);
        place(SPAWN, 0f, 0f);

        assertFalse(play.play());

        assertTrue(editorState.getStatusMessage().startsWith("Cannot play: No player"), editorState.getStatusMessage());
    }
}
