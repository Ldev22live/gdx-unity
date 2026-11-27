package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.scenario.RulesEditor;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CharacterController3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.PlayerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioRuntime;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioRuntimeComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Editor Play runs the scene's rules on the copy it plays (TD-109), logs them, pauses them with Play, and leaves the scene alone. */
class ScenarioPlayStepTest {
    @BeforeAll
    static void initNatives() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldRunRulesOnThePlayCopyAndLeaveTheAuthoredSceneUnchanged() {
        EditorState editorState = new EditorState();
        PlayModeController controller = new PlayModeController(editorState);
        List<String> log = new ArrayList<>();
        controller.getScenarioStep().setLog(log::add);
        RulesEditor rules = new RulesEditor(editorState);
        String rule = rules.addRule();
        rules.setTimer(rule, 1f);
        rules.setMessage(rule, 0, "Go!", 5f);
        Scene authored = editorState.getActiveScene();
        String scenario = authored.getScenarioJson();
        int entities = authored.getAllEntities().size();

        assertTrue(controller.play());
        ScenarioRuntime runtime = controller.getScenarioStep().getRuntime();
        assertNotNull(runtime);
        for (int i = 0; i < 5; i++) {
            controller.update(0.1f);
        }
        assertTrue(controller.pause());
        controller.update(5f);
        assertNull(runtime.getMessage(), "Pause holds game time");
        assertTrue(controller.resume());
        for (int i = 0; i < 6; i++) {
            controller.update(0.1f);
        }
        assertEquals("Go!", runtime.getMessage());
        assertEquals(1, log.size(), log.toString());
        assertEquals(List.of(log.get(0)), controller.getScenarioStep().getRecentLog());

        controller.stop();
        assertSame(authored, editorState.getActiveScene());
        assertNull(controller.getScenarioStep().getRuntime());
        assertEquals(scenario, authored.getScenarioJson());
        assertEquals(entities, authored.getAllEntities().size(), "the runtime-only rules entity stayed in the copy");
        assertTrue(authored.getAllEntities().stream().noneMatch(e -> e.getComponent(ScenarioRuntimeComponent.class) != null));
    }

    @Test
    void shouldNotAddARulesEntityToASceneWithoutAScenario() {
        EditorState editorState = new EditorState();
        PlayModeController controller = new PlayModeController(editorState);

        assertTrue(controller.play());

        assertNull(controller.getScenarioStep().getRuntime());
        assertTrue(editorState.getActiveScene().getAllEntities().stream()
            .noneMatch(e -> e.getName().equals(ScenarioRuntimeComponent.ENTITY_NAME)));
        controller.stop();
    }

    @Test
    void shouldStartFreshRulesEachPlayAndLeaveNothingBehindAfterRepeatedPlayStopWithJolt() {
        EditorState editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new BoxCollider3DComponentSerializer());
        editorState.getSerializer().getRegistry().register(new CharacterController3DComponentSerializer());
        editorState.getSerializer().getRegistry().register(new PlayerComponentSerializer());
        PlayModeController controller = new PlayModeController(editorState);
        List<String> log = new ArrayList<>();
        controller.getScenarioStep().setLog(log::add);
        Scene authored = editorState.getActiveScene();
        Entity ground = authored.createEntity("Ground");
        ground.getTransform().setLocalPosition(0f, -50f, 0f);
        ground.addComponent(new BoxCollider3DComponent(new Vector3(4000f, 100f, 4000f)));
        Entity hero = authored.createEntity("Hero");
        hero.getTransform().setLocalPosition(0f, 71f, 600f);
        hero.addComponent(new CharacterController3DComponent());
        hero.addComponent(new PlayerComponent());
        RulesEditor rules = new RulesEditor(editorState);
        Entity door = rules.addArea(new Vector3(0f, 60f, -400f));
        String arrive = rules.addRule();
        rules.renameRule(arrive, "Arrive");
        rules.setEnterArea(arrive, door.getId(), RulesEditor.WHO_PLAYER);
        rules.setMessage(arrive, 0, "Home", 3f);
        String scenario = authored.getScenarioJson();
        int entities = authored.getAllEntities().size();
        int undoable = editorState.getCommandHistory().getUndoCount();

        for (int round = 1; round <= 5; round++) {
            assertTrue(controller.play(), "round " + round + ": " + editorState.getStatusMessage());
            assertNotNull(controller.getPhysicsSystem3D());
            Scene playing = editorState.getActiveScene();
            controller.update(1f / 60f); // the character body is created at the first physics update
            controller.getPhysicsSystem3D().teleportCharacter(playing.findEntityById(hero.getId()), new Vector3(0f, 71f, -400f));
            for (int i = 0; i < 10; i++) {
                controller.update(1f / 60f);
            }
            assertEquals("Home", controller.getScenarioStep().getRuntime().getMessage(), "round " + round);
            assertEquals(round, log.size(), "the once rule fires once per Play, and listeners do not pile up: " + log);
            controller.stop();

            assertSame(authored, editorState.getActiveScene());
            assertNull(controller.getPhysicsSystem3D(), "3D physics disposed at Stop");
            assertNull(controller.getScenarioStep().getRuntime());
            assertEquals(scenario, authored.getScenarioJson(), "rule state never leaks back");
            assertEquals(entities, authored.getAllEntities().size());
            assertEquals(new Vector3(0f, 71f, 600f), authored.findEntityById(hero.getId()).getTransform().getLocalPosition());
            assertEquals(undoable, editorState.getCommandHistory().getUndoCount(), "Play adds nothing to undo");
        }
    }
}
