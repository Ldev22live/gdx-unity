package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.agent.DefaultAgentTools;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutor;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanStep;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeState;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.CameraComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.play.GroundWalkMover;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The assistant's genre and Play tools (roadmap M3b.7, TD-088): the footer's and Play's own behaviour, as plan steps. */
class PlayToolsTest {
    private SceneSerializer serializer;
    private EditorState editorState;
    private PlayModeController play;
    private AgentToolContext context;
    private Scene authored;
    private Entity hero;

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
        authored = new Scene("World");
        authored.setGameMode(GameMode.MODE_3D);
        authored.createEntity("Main Camera").addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        Entity ground = authored.createEntity("Ground");
        ground.getTransform().setLocalPosition(new Vector3(0f, -50f, 0f));
        BoxCollider3DComponent floor = new BoxCollider3DComponent();
        floor.setSize(new Vector3(4000f, 100f, 4000f));
        ground.addComponent(floor);
        hero = authored.createEntity("Hero");
        hero.getTransform().setLocalPosition(new Vector3(0f, 80f, 0f));
        hero.addComponent(new CharacterController3DComponent());
        editorState.setActiveScene(authored);
        play = new PlayModeController(editorState);
        play.getProfileStep().setInput(key -> false);
        editorState.setPlayModeController(play);
        context = new AgentToolContext(editorState);
    }

    @AfterEach
    void tearDown() {
        play.stop();
    }

    private static AgentToolResult run(AgentTool tool, Map<String, Object> parameters, AgentToolContext context) throws Exception {
        return tool.execute(parameters, context);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(AgentPlanExecutionResult result, String step) {
        return (Map<String, Object>) result.getStepOutputs().get(step);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstProblem(AgentToolResult result) {
        return ((List<Map<String, Object>>) result.getOutput("problems")).get(0);
    }

    @Test
    void shouldSetTheGenreAndModeAndSayWhetherThePairPlays() throws Exception {
        PlayProfileSetTool tool = new PlayProfileSetTool();
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        AgentToolResult planned = run(tool, Map.of("genre", "action-rpg", "mode", "2D"), context);
        assertTrue(planned.isSuccess(), planned.getMessage());
        assertEquals(GameGenres.ACTION_RPG, editorState.getGameGenre());
        assertEquals(false, planned.getOutput("playable"));
        assertEquals("planned", planned.getOutput("status"));
        assertEquals(List.of("2.5d", "3d"), planned.getOutput("playableModes"));
        assertTrue(planned.getMessage().contains("Play will refuse it"), planned.getMessage());

        AgentToolResult playable = run(tool, Map.of("genre", "platformer", "mode", "2D"), context);
        assertEquals(true, playable.getOutput("playable"), "Platformer + 2D since TD-105");
        assertEquals("2d", playable.getOutput("mode"));
        assertEquals(List.of("2d", "2.5d", "3d"), playable.getOutput("playableModes"));

        assertTrue(run(tool, Map.of("genre", "none"), context).isSuccess());
        assertNull(editorState.getGameGenre());
        assertEquals(undoSteps, editorState.getCommandHistory().getUndoCount(), "a scene setting, not an undo step");

        assertFalse(tool.validateParameters(Map.of("genre", "rhythm"), context).isEmpty(), "an unknown genre is refused");
        assertFalse(tool.validateParameters(Map.of("genre", "puzzle", "mode", "4d"), context).isEmpty(), "an unknown mode is refused");
    }

    @Test
    void shouldCheckTheSceneWithoutPlayingAndNameTheFixForThePerson() throws Exception {
        PlayProfileCheckTool tool = new PlayProfileCheckTool();
        AgentToolResult noGenre = run(tool, Map.of(), context);
        assertEquals("no-genre", noGenre.getOutput("outcome"));
        assertEquals(true, noGenre.getOutput("canPlay"));

        editorState.setGameGenre(GameGenres.ACTION_RPG);
        AgentToolResult check = run(tool, Map.of(), context);

        assertEquals("profile", check.getOutput("outcome"));
        assertEquals(false, check.getOutput("canPlay"));
        assertEquals("Make Hero the player", firstProblem(check).get("fix"));
        assertEquals(PlayModeState.STOPPED, play.getState(), "checking never plays");
        assertNull(hero.getComponent(PlayerComponent.class), "checking never fixes");
        assertTrue(tool.isReadOnly());
    }

    @Test
    void shouldReportARefusedPlayAsAResultThenPlayAndStop() throws Exception {
        editorState.setGameGenre(GameGenres.ACTION_RPG);
        String before = serializer.toJson(authored);

        AgentToolResult refused = run(new PlayStartTool(), Map.of(), context);
        assertTrue(refused.isSuccess(), "a refusal is a result a plan can read");
        assertEquals(false, refused.getOutput("started"));
        assertEquals("Make Hero the player", firstProblem(refused).get("fix"));
        assertNull(hero.getComponent(PlayerComponent.class), "the tool never applies a fix");

        hero.addComponent(new PlayerComponent());
        AgentToolResult started = run(new PlayStartTool(), Map.of(), context);
        assertEquals(true, started.getOutput("started"));
        assertEquals("playing", started.getOutput("state"));
        assertNotNull(play.getRuntimeScene().findEntityById(hero.getId()).getComponent(GroundWalkMover.class));
        assertFalse(new PlayProfileSetTool().validateParameters(Map.of("genre", "puzzle"), context).isEmpty(),
            "the genre cannot change during Play");

        AgentToolResult stopped = run(new PlayStopTool(), Map.of(), context);
        assertEquals(true, stopped.getOutput("stopped"));
        assertSame(authored, editorState.getActiveScene());
        hero.removeComponent(hero.getComponent(PlayerComponent.class));
        assertEquals(before, serializer.toJson(authored), "Stop returns the scene as it was");
        assertEquals(false, run(new PlayStopTool(), Map.of(), context).getOutput("stopped"), "stopping twice is not an error");
    }

    @Test
    void shouldRunGenreAndPlayStepsInAPlanWithoutAddingUndoSteps() {
        hero.addComponent(new PlayerComponent());
        AgentToolRegistry registry = DefaultAgentTools.createDefaultRegistry();
        AgentPlan plan = new AgentPlan("Play the valley as an Action RPG");
        plan.addStep(new AgentPlanStep("genre", PlayProfileSetTool.ID, Map.of("genre", "action-rpg", "mode", "3d")));
        plan.addStep(new AgentPlanStep("check", PlayProfileCheckTool.ID, Map.of()));
        plan.addStep(new AgentPlanStep("play", PlayStartTool.ID, Map.of()));
        plan.addStep(new AgentPlanStep("stop", PlayStopTool.ID, Map.of()));
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        AgentPlanExecutionResult result = new AgentPlanExecutor(registry).executePlan(plan, context);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(true, output(result, "check").get("canPlay"));
        assertEquals(true, output(result, "play").get("started"));
        assertEquals(true, output(result, "stop").get("stopped"));
        assertEquals(GameGenres.ACTION_RPG, editorState.getGameGenre(), "the genre stays set");
        assertEquals(undoSteps, editorState.getCommandHistory().getUndoCount(), "nothing went into the undo history");
        assertEquals(PlayModeState.STOPPED, play.getState());
    }
}
