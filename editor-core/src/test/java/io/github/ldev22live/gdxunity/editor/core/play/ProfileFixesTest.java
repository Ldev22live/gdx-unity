package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileFix;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Safe fixes as one undo step each, and choosing the scene's genre (roadmap M3b.2, TD-088). */
class ProfileFixesTest {
    private EditorState editorState;
    private Scene scene;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        scene = new Scene("World");
        scene.setGameMode(GameMode.MODE_3D);
        editorState.setActiveScene(scene);
    }

    private int undoSteps() {
        return editorState.getCommandHistory().getUndoCount();
    }

    @Test
    void shouldMakeAnEntityThePlayerInOneUndoStep() {
        Entity hero = editorState.createEntity("Hero");
        int steps = undoSteps();

        assertTrue(ProfileFixes.apply(editorState, new ProfileFix(ProfileFix.Kind.MAKE_PLAYER, hero.getId(), "Make Hero the player")));

        assertNotNull(hero.getComponent(PlayerComponent.class));
        assertEquals(steps + 1, undoSteps());
        assertTrue(editorState.undo());
        assertNull(hero.getComponent(PlayerComponent.class));
    }

    @Test
    void shouldAddACharacterControllerInOneUndoStep() {
        Entity hero = editorState.createEntity("Hero");

        assertTrue(ProfileFixes.apply(editorState, new ProfileFix(ProfileFix.Kind.ADD_CHARACTER_CONTROLLER_3D, hero.getId(), "Add")));

        assertNotNull(hero.getComponent(CharacterController3DComponent.class));
        assertTrue(editorState.undo());
        assertNull(hero.getComponent(CharacterController3DComponent.class));
    }

    @Test
    void shouldAddAnUprightTwoDBodyAndAColliderTheSizeOfTheSpriteEachInOneUndoStep() {
        Entity hero = editorState.createEntity("Hero");
        SpriteAnimatorComponent sprite = new SpriteAnimatorComponent();
        sprite.setDocument(new SpriteDocument(16, 24)); // drawn at its pixel size (Width and Height 0), as a dropped 2D sprite is
        hero.addComponent(sprite);
        int steps = undoSteps();

        assertTrue(ProfileFixes.apply(editorState, new ProfileFix(ProfileFix.Kind.ADD_RIGID_BODY_2D, hero.getId(), "Add")));
        RigidBody2DComponent body = hero.getComponent(RigidBody2DComponent.class);
        assertEquals(RigidBody2DComponent.BodyType.DYNAMIC, body.getBodyType());
        assertTrue(body.isFixedRotation(), "a 2D player stays upright");
        assertTrue(ProfileFixes.apply(editorState, new ProfileFix(ProfileFix.Kind.ADD_BOX_COLLIDER_2D, hero.getId(), "Add")));
        BoxCollider2DComponent box = hero.getComponent(BoxCollider2DComponent.class);
        assertEquals(16f, box.getWidth());
        assertEquals(24f, box.getHeight());

        assertEquals(steps + 2, undoSteps());
        assertTrue(editorState.undo());
        assertNull(hero.getComponent(BoxCollider2DComponent.class));
        assertTrue(editorState.undo());
        assertNull(hero.getComponent(RigidBody2DComponent.class));
    }

    @Test
    void shouldAddACameraThatSuitsTheGameMode() {
        assertTrue(ProfileFixes.apply(editorState, new ProfileFix(ProfileFix.Kind.ADD_CAMERA, null, "Add a camera")));

        Entity camera = scene.findEntityByName(ProfileFixes.CAMERA_NAME);
        assertEquals(CameraComponent.Projection.PERSPECTIVE, camera.getComponent(CameraComponent.class).getProjection());
        assertTrue(editorState.undo());
        assertNull(scene.findEntityByName(ProfileFixes.CAMERA_NAME), "one undo step removes it");

        editorState.newScene("Flat", GameMode.MODE_2D);
        assertTrue(ProfileFixes.apply(editorState, new ProfileFix(ProfileFix.Kind.ADD_CAMERA, null, "Add a camera")));
        assertEquals(CameraComponent.Projection.ORTHOGRAPHIC, editorState.getActiveScene().findEntityByName(ProfileFixes.CAMERA_NAME)
            .getComponent(CameraComponent.class).getProjection());
    }

    @Test
    void shouldRefuseAFixForAnEntityThatIsGoneOrDuringPlay() {
        assertFalse(ProfileFixes.apply(editorState, new ProfileFix(ProfileFix.Kind.MAKE_PLAYER, UUID.randomUUID(), "Make Ghost the player")));
        assertTrue(editorState.getStatusMessage().contains("no longer exists"), editorState.getStatusMessage());

        Entity hero = editorState.createEntity("Hero");
        editorState.setPlayModeController(new PlayModeController(editorState));
        assertTrue(editorState.getPlayModeController().play());
        assertFalse(ProfileFixes.apply(editorState, new ProfileFix(ProfileFix.Kind.MAKE_PLAYER, hero.getId(), "Make Hero the player")));
        assertTrue(editorState.getStatusMessage().contains("Stop the game first"), editorState.getStatusMessage());
        editorState.getPlayModeController().stop();
        assertNull(scene.findEntityById(hero.getId()).getComponent(PlayerComponent.class));
    }

    @Test
    void shouldChooseTheGenreWithoutAnUndoStepAndTellListeners() {
        List<String> changes = new ArrayList<>();
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onGameGenreChanged(String oldGenre, String newGenre) {
                changes.add(oldGenre + "->" + newGenre);
            }
        });
        int steps = undoSteps();

        assertTrue(editorState.setGameGenre(GameGenres.ACTION_RPG));
        assertFalse(editorState.setGameGenre(GameGenres.ACTION_RPG), "the same genre changes nothing");
        assertTrue(editorState.setGameGenre(null));

        assertEquals(List.of("null->action-rpg", "action-rpg->null"), changes);
        assertEquals(steps, undoSteps(), "a setting of the scene, like its game mode");
        assertFalse(editorState.setGameGenre("Action RPG"));
        assertTrue(editorState.isStatusError());
        assertNull(editorState.getGameGenre());
    }

    @Test
    void shouldRefuseToChangeTheGenreDuringPlay() {
        editorState.setPlayModeController(new PlayModeController(editorState));
        assertTrue(editorState.getPlayModeController().play());

        assertFalse(editorState.setGameGenre(GameGenres.PLATFORMER));

        editorState.getPlayModeController().stop();
        assertNull(editorState.getGameGenre());
    }
}
