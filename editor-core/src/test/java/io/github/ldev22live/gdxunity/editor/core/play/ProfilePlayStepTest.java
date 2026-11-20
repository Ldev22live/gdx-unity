package io.github.ldev22live.gdxunity.editor.core.play;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.CameraComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.play.GroundWalkMover;
import io.github.ldev22live.gdxunity.engine.render.play.PlaneRunMover;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileFix;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CharacterController3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.PlayerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Editor Play with a genre profile (roadmap M3b.3, M3b.5 and M3c.1, TD-088): applied to the copy only, refusals, fixes, Stop. */
class ProfilePlayStepTest {
    private static final float FRAME = 1f / 60f;

    private final Set<Integer> held = new HashSet<>();
    private SceneSerializer serializer;
    private EditorState editorState;
    private PlayModeController play;
    private Scene authored;
    private Entity runner;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new CameraComponentSerializer());
        registry.register(new BoxCollider3DComponentSerializer());
        registry.register(new CharacterController3DComponentSerializer());
        registry.register(new PlayerComponentSerializer());
        serializer = new SceneSerializer(registry);
        editorState = new EditorState(serializer);
        authored = new Scene("Side Level");
        authored.setGameMode(GameMode.MODE_2_5D);
        authored.setGameGenre(GameGenres.PLATFORMER);
        authored.createEntity("Main Camera").addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        Entity ground = authored.createEntity("Ground");
        ground.getTransform().setLocalPosition(new Vector3(0f, -50f, 0f));
        BoxCollider3DComponent floor = new BoxCollider3DComponent();
        floor.setSize(new Vector3(4000f, 100f, 400f));
        ground.addComponent(floor);
        runner = authored.createEntity("Runner");
        runner.getTransform().setLocalPosition(new Vector3(0f, 80f, 0f));
        runner.addComponent(new CharacterController3DComponent());
        editorState.setActiveScene(authored);
        play = new PlayModeController(editorState);
        play.getProfileStep().setInput(held::contains);
        editorState.setPlayModeController(play);
    }

    @AfterEach
    void tearDown() {
        play.stop();
    }

    @Test
    void shouldOfferToMakeTheOnlyCharacterThePlayerThenPlay() {
        assertFalse(play.play(), "no player yet");
        assertEquals(PlayModeState.STOPPED, play.getState());
        assertTrue(editorState.getStatusMessage().startsWith("Cannot play: No player"), editorState.getStatusMessage());
        ProfileFix fix = play.getProfileStep().getLastProblems().get(0).safeFix().orElseThrow();
        assertEquals("Make Runner the player", fix.label());
        assertSame(authored, editorState.getActiveScene(), "a refused Play leaves the authored scene in place");

        assertTrue(ProfileFixes.apply(editorState, fix));
        assertTrue(play.play());

        assertTrue(editorState.getStatusMessage().startsWith("Playing as Platformer / Side-scroller + 2.5D."),
            editorState.getStatusMessage());
    }

    @Test
    void shouldOfferTheFixAsAButtonThatIsOneUndoableStepAndThenGoesAway() {
        assertFalse(play.play());
        ProfilePlayStep step = play.getProfileStep();
        assertEquals(List.of("Make Runner the player"), step.getLastFixes().stream().map(ProfileFix::label).toList());
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        assertTrue(step.applyFix(editorState, step.getLastFixes().get(0)));

        assertNotNull(runner.getComponent(PlayerComponent.class), "the authored runner is the player now");
        assertEquals(undoSteps + 1, editorState.getCommandHistory().getUndoCount(), "one undoable step");
        assertEquals(List.of(), step.getLastFixes(), "the button goes away once used");
        assertEquals("Done: Make Runner the player (undoable). Press Play again.", editorState.getStatusMessage());
        assertEquals(PlayModeState.STOPPED, play.getState(), "a fix never starts Play by itself");
        editorState.undo();
        assertNull(runner.getComponent(PlayerComponent.class), "undo takes it back");

        assertFalse(play.play());
        step.clearProblems();
        assertEquals(List.of(), step.getLastFixes(), "forgotten, as when another scene is opened");
    }

    @Test
    void shouldRefusePlayWhenNothingIsUnderThePlayer() {
        runner.addComponent(new PlayerComponent());
        runner.getTransform().setLocalPosition(new Vector3(5000f, 80f, 0f)); // beyond the ground's end at x = 2000

        assertFalse(play.play());

        assertTrue(editorState.getStatusMessage().startsWith("Cannot play: Nothing under Runner to stand on"),
            editorState.getStatusMessage());
        assertSame(authored, editorState.getActiveScene());

        runner.getTransform().setLocalPosition(new Vector3(1500f, 80f, 0f));
        assertTrue(play.play(), "over the ground, Play starts");
    }

    @Test
    void shouldApplyTheProfileToTheCopyOnlyAndRestoreTheSceneAndUndoHistoryOnStop() {
        runner.addComponent(new PlayerComponent());
        String before = serializer.toJson(authored);
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        assertTrue(play.play());
        Scene copy = play.getRuntimeScene();
        Entity playing = copy.findEntityById(runner.getId());
        assertNotNull(playing.getComponent(PlaneRunMover.class));
        assertInstanceOf(CameraRigComponent.class, copy.findEntityByName("Main Camera").getComponent(CameraRigComponent.class));
        assertNull(runner.getComponent(PlaneRunMover.class), "the authored runner has none");
        held.add(Keys.D);
        for (int frame = 0; frame < 60; frame++) {
            play.update(FRAME);
        }
        assertTrue(playing.getTransform().getWorldPosition().x > 300f, "ran right in Play: " + playing.getTransform().getWorldPosition());
        assertEquals(0f, playing.getTransform().getWorldPosition().z, 0.01f);

        play.stop();

        assertSame(authored, editorState.getActiveScene());
        assertEquals(before, serializer.toJson(authored), "the authored scene is unchanged, and still saves");
        assertEquals(undoSteps, editorState.getCommandHistory().getUndoCount(), "Play added nothing to undo");
        assertNull(authored.findEntityByName("Main Camera").getComponent(CameraRigComponent.class));
    }

    @Test
    void shouldWalkTheActionRpgPlayerRelativeToTheCameraInPlayAndRestoreOnStop() {
        runner.addComponent(new PlayerComponent());
        editorState.setGameMode(GameMode.MODE_3D);
        assertTrue(editorState.setGameGenre(GameGenres.ACTION_RPG));
        String before = serializer.toJson(authored);
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        assertTrue(play.play());
        assertTrue(editorState.getStatusMessage().startsWith("Playing as Action RPG + 3D."), editorState.getStatusMessage());
        Scene copy = play.getRuntimeScene();
        Entity playing = copy.findEntityById(runner.getId());
        assertNotNull(playing.getComponent(GroundWalkMover.class));
        assertEquals(CameraRigComponent.Mode.ORBIT, copy.findEntityByName("Main Camera").getComponent(CameraRigComponent.class).getMode());
        held.add(Keys.W);
        for (int frame = 0; frame < 60; frame++) {
            play.update(FRAME);
        }
        assertTrue(playing.getTransform().getWorldPosition().z < -250f, "walked away from the camera: "
            + playing.getTransform().getWorldPosition());

        play.stop();

        assertEquals(before, serializer.toJson(authored), "the authored scene is unchanged, and still saves");
        assertEquals(undoSteps, editorState.getCommandHistory().getUndoCount(), "Play added nothing to undo");
        assertNull(runner.getComponent(GroundWalkMover.class));
    }

    @Test
    void shouldRefuseAPlannedPairWithItsReasonAndPlayScenesWithoutAGenreAsBefore() {
        runner.addComponent(new PlayerComponent());
        editorState.setGameMode(GameMode.MODE_2D);

        assertFalse(play.play());
        assertTrue(editorState.getStatusMessage().contains("Platformer / Side-scroller + 2D cannot be played yet"),
            editorState.getStatusMessage());

        assertTrue(editorState.setGameGenre(null));
        assertTrue(play.play());
        assertEquals("Play mode started", editorState.getStatusMessage());
        assertNull(play.getRuntimeScene().findEntityById(runner.getId()).getComponent(PlaneRunMover.class), "no genre, no profile");
    }
}
