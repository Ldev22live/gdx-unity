package io.github.ldev22live.gdxunity.editor.core.scenario;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.command.DeleteEntityCommand;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.PlayerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioRuntime;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules and objectives edited through undoable commands (TD-109, roadmap A05), and what they do when played. */
class RulesEditorTest {
    private EditorState editorState;
    private RulesEditor rules;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new BoxCollider3DComponentSerializer());
        editorState.getSerializer().getRegistry().register(new PlayerComponentSerializer());
        rules = new RulesEditor(editorState);
    }

    private Scene scene() {
        return editorState.getActiveScene();
    }

    private JsonValue rule(int index) {
        return rules.rules().get(index);
    }

    @Test
    void shouldCreateTheBlockWithoutPlayersWhenTheFirstRuleIsAdded() {
        assertNull(scene().getScenarioJson());

        String id = rules.addRule();

        assertNotNull(id);
        assertEquals(0, ScenarioSetup.read(scene()).orElseThrow().players().size(), "rules need no players");
        assertEquals("Rule 1", rule(0).getString("name"));
        assertEquals("timer", rule(0).get("when").getString("type"));
        assertEquals(List.of(), ScenarioValidator.validate(scene()), "a new rule is valid as it is");
        assertTrue(editorState.undo());
        assertNull(scene().getScenarioJson(), "undo removes the block it created");
    }

    @Test
    void shouldEditEveryPartOfARuleOneUndoStepEach() {
        String id = rules.addRule();
        String objective = rules.addObjective("Reach the house");

        assertTrue(rules.renameRule(id, " Arrive "));
        assertTrue(rules.setRuleOnce(id, false));
        assertTrue(rules.setRuleEnabled(id, false));
        assertTrue(rules.setTimer(id, 2.5f));
        assertTrue(rules.setMessage(id, 0, "Go!", 3f));
        assertTrue(rules.addEffect(id, RulesEditor.EffectType.COMPLETE_OBJECTIVE));
        assertTrue(rules.addEffect(id, RulesEditor.EffectType.END_SCENARIO));
        assertTrue(rules.setEndResult(id, 2, "defeat"));

        JsonValue edited = rule(0);
        assertEquals("Arrive", edited.getString("name"));
        assertFalse(edited.getBoolean("once"));
        assertFalse(edited.getBoolean("enabled"));
        assertEquals(2.5f, edited.get("when").getFloat("seconds"));
        assertEquals("Go!", edited.get("then").get(0).getString("text"));
        assertEquals(objective, edited.get("then").get(1).getString("objective"));
        assertEquals("defeat", edited.get("then").get(2).getString("result"));
        assertEquals(List.of(), ScenarioValidator.validate(scene()));

        for (int i = 0; i < 8; i++) {
            assertTrue(editorState.undo());
        }
        assertEquals("Rule 1", rule(0).getString("name"));
        assertEquals(1, rule(0).get("then").size);
        assertTrue(editorState.redo());
        assertEquals("Arrive", rule(0).getString("name"));
    }

    @Test
    void shouldRefuseInvalidValuesWithoutChangingAnything() {
        String id = rules.addRule();
        String before = scene().getScenarioJson();

        assertFalse(rules.renameRule(id, "  "));
        assertFalse(rules.setTimer(id, -1f));
        assertFalse(rules.setMessage(id, 0, "", 2f));
        assertFalse(rules.setMessage(id, 0, "ok", 61f));
        assertFalse(rules.setEndResult(id, 0, "victory"), "effect 1 is a message, not an end");
        assertFalse(rules.addEffect(id, RulesEditor.EffectType.COMPLETE_OBJECTIVE), "no objective yet");
        assertFalse(rules.removeEffect(id, 0), "a rule keeps one effect");
        for (int i = 1; i < ScenarioRuntime.MAX_EFFECTS; i++) {
            assertTrue(rules.addEffect(id, RulesEditor.EffectType.SHOW_MESSAGE));
        }
        assertFalse(rules.addEffect(id, RulesEditor.EffectType.SHOW_MESSAGE), "at most 8 effects");
        assertTrue(before.length() < scene().getScenarioJson().length());
    }

    @Test
    void shouldOnlyAcceptAnAreaWithATriggerColliderAndAKnownWho() {
        String id = rules.addRule();
        Entity wall = scene().createEntity("Wall");
        wall.addComponent(new BoxCollider3DComponent());

        assertFalse(rules.setEnterArea(id, wall.getId(), RulesEditor.WHO_PLAYER));
        Entity area = rules.addArea(new Vector3(100f, 0f, 200f));
        assertEquals(List.of(area), rules.areaCandidates());
        assertFalse(rules.setEnterArea(id, area.getId(), "33333333-3333-4333-8333-333333333333"), "not a player of the scenario");
        assertTrue(rules.setEnterArea(id, area.getId(), RulesEditor.WHO_PLAYER));
        assertEquals(area.getId().toString(), rule(0).get("when").getString("area"));
        assertEquals(List.of(), ScenarioValidator.validate(scene()));

        assertFalse(editorState.executeCommand(new DeleteEntityCommand(editorState, area.getId())),
            "an area a rule uses is not deleted");
        assertTrue(editorState.getStatusMessage().contains("uses it as its area"), editorState.getStatusMessage());
    }

    @Test
    void shouldReorderAndRemoveRulesAndProtectAnObjectiveARuleCompletes() {
        String first = rules.addRule();
        String second = rules.addRule();
        String objective = rules.addObjective("Hold out");
        rules.setEffectType(second, 0, RulesEditor.EffectType.COMPLETE_OBJECTIVE);

        assertTrue(rules.moveRule(second, -1));
        assertEquals(second, rule(0).getString("id"));
        assertFalse(rules.moveRule(second, -1), "already first");

        assertFalse(rules.removeObjective(objective));
        assertTrue(editorState.getStatusMessage().contains("completes it"), editorState.getStatusMessage());
        assertTrue(rules.removeRule(second));
        assertTrue(rules.removeObjective(objective));
        assertEquals(List.of(first), rules.rules().stream().map(r -> r.getString("id")).toList());
    }

    @Test
    void shouldHideAnObjectiveAndKeepUnknownMembers() {
        scene().setPreservedFields(PreservedFields.of(Map.of(Scene.SCENARIO_MEMBER, "{\"version\":1,\"players\":[],"
            + "\"objectives\":[{\"id\":\"55555555-5555-4555-8555-555555555555\",\"text\":\"A\",\"futureIcon\":\"star\"}],"
            + "\"rules\":[],\"futureCampaign\":{\"next\":\"b.json\"}}")));

        assertTrue(rules.setObjectiveHidden("55555555-5555-4555-8555-555555555555", true));
        assertTrue(rules.setObjectiveText("55555555-5555-4555-8555-555555555555", "Find the key"));

        String json = scene().getScenarioJson();
        assertTrue(json.contains("\"futureIcon\":\"star\""), json);
        assertTrue(json.contains("\"futureCampaign\":{\"next\":\"b.json\"}"), json);
        assertTrue(json.contains("\"initial\":\"hidden\""), json);
        assertTrue(rules.setObjectiveHidden("55555555-5555-4555-8555-555555555555", false));
        assertFalse(scene().getScenarioJson().contains("initial"));
    }

    @Test
    void shouldRefuseToEditANewerScenario() {
        scene().setPreservedFields(PreservedFields.of(Map.of(Scene.SCENARIO_MEMBER, "{\"version\":2,\"rules\":[]}")));

        assertNull(rules.addRule());
        assertTrue(editorState.getStatusMessage().contains("needs a newer editor"), editorState.getStatusMessage());
    }

    @Test
    void shouldPlayAReachTargetObjectiveAndATimedMessageMadeWithoutCode() {
        Entity hero = scene().createEntity("Hero");
        hero.addComponent(new PlayerComponent());
        String objective = rules.addObjective("Reach the house");
        String welcome = rules.addRule();
        rules.renameRule(welcome, "Welcome");
        rules.setTimer(welcome, 1f);
        rules.setMessage(welcome, 0, "Find the house", 3f);
        Entity door = rules.addArea(new Vector3(0f, 0f, -500f));
        String arrive = rules.addRule();
        rules.renameRule(arrive, "Arrive");
        rules.setEnterArea(arrive, door.getId(), RulesEditor.WHO_PLAYER);
        rules.setEffectType(arrive, 0, RulesEditor.EffectType.COMPLETE_OBJECTIVE);
        rules.addEffect(arrive, RulesEditor.EffectType.END_SCENARIO);

        Scene reopened = editorState.getSerializer().fromJson(editorState.getSerializer().toJson(scene()));
        List<String> log = new ArrayList<>();
        ScenarioRuntime runtime = ScenarioRuntime.attach(reopened, null, null, log::add).getRuntime();
        for (int i = 0; i < 12; i++) {
            reopened.update(0.1f);
        }
        assertEquals("Find the house", runtime.getMessage());
        runtime.onEnter(reopened.findEntityById(door.getId()), reopened.findEntityById(hero.getId()));
        reopened.update(0.1f);

        assertTrue(runtime.getObjectives().get(0).complete());
        assertEquals(objective, runtime.getObjectives().get(0).id());
        assertEquals(ScenarioRuntime.Result.VICTORY, runtime.getResult());
        assertEquals(2, log.size(), log.toString());
    }
}
