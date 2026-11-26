package io.github.ldev22live.gdxunity.engine.scene.scenario;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scenario block's checks (TD-106): the approved closed rule set, references by stable id, nothing executed. */
class ScenarioValidatorTest {
    private static final String P1 = "33333333-3333-4333-8333-333333333333";
    private static final String NATURE = "44444444-4444-4444-8444-444444444444";
    private static final String GOAL = "55555555-5555-4555-8555-555555555555";

    private final Scene scene = new Scene("Valley");
    private final Entity area = scene.createEntity("Door Area");
    private final Entity hero = scene.createEntity("Hero");

    private void scenario(String rules) {
        String json = """
            {"version": 1,
             "players": [{"id": "%s", "name": "Player 1", "color": "#2f6fdf", "spawn": "%s"},
                         {"id": "%s", "name": "Nature", "color": "#8a8a7a", "neutral": true}],
             "objectives": [{"id": "%s", "text": "Reach the house", "player": "%s"}],
             "rules": [%s]}""".formatted(P1, hero.getId(), NATURE, GOAL, P1, rules);
        scene.setPreservedFields(PreservedFields.of(Map.of(Scene.SCENARIO_MEMBER, json)));
    }

    private static String rule(String id, String when, String then) {
        return """
            {"id": "%s", "name": "R", "enabled": true, "once": true, "when": %s, "then": [%s]}""".formatted(id, when, then);
    }

    @Test
    void shouldAcceptTheApprovedRuleSetWithValidReferences() {
        scenario(rule(UUID.randomUUID().toString(), "{\"type\": \"timer\", \"seconds\": 2}",
            "{\"type\": \"showMessage\", \"text\": \"Go\"}") + ","
            + rule(UUID.randomUUID().toString(), "{\"type\": \"enterArea\", \"area\": \"" + area.getId() + "\", \"who\": \"player\"}",
                "{\"type\": \"completeObjective\", \"objective\": \"" + GOAL + "\"}, {\"type\": \"endScenario\", \"result\": \"victory\", "
                    + "\"player\": \"" + P1 + "\"}"));
        hero.addComponent(new OwnerComponent(P1));
        scene.createEntity("Rock").addComponent(new OwnerComponent(NATURE));

        assertEquals(List.of(), ScenarioValidator.validate(scene));
    }

    @Test
    void shouldKeepButNeverRunARuleTypeOutsideTheApprovedSet() {
        scenario(rule(UUID.randomUUID().toString(), "{\"type\": \"unitCount\", \"atLeast\": 5}",
            "{\"type\": \"runScript\", \"code\": \"System.exit(0)\"}"));

        List<ScenarioValidator.Problem> problems = ScenarioValidator.validate(scene);

        assertEquals(List.of(ScenarioValidator.Severity.NOT_RUN, ScenarioValidator.Severity.NOT_RUN),
            problems.stream().map(ScenarioValidator.Problem::severity).toList(), problems.toString());
        assertTrue(problems.get(1).message().contains("'runScript' is not in this version's rule set"), problems.toString());
    }

    @Test
    void shouldReportBrokenReferencesInsteadOfRepairingThem() {
        String missing = UUID.randomUUID().toString();
        scenario(rule(UUID.randomUUID().toString(), "{\"type\": \"enterArea\", \"area\": \"" + missing + "\", \"who\": \"Player 9\"}",
            "{\"type\": \"completeObjective\", \"objective\": \"" + missing + "\"}"));
        scene.createEntity("Tower").addComponent(new OwnerComponent(missing));

        List<String> paths = ScenarioValidator.validate(scene).stream().map(ScenarioValidator.Problem::path).toList();

        assertEquals(List.of("scenario.rules[0].when.area", "scenario.rules[0].when.who", "scenario.rules[0].then[0].objective",
            "entity 'Tower'.OwnerComponent"), paths);
        assertEquals(missing, scene.findEntityByName("Tower").getComponent(OwnerComponent.class).getPlayerId(),
            "an invalid owner is kept as written, not cleared or reassigned");
    }

    @Test
    void shouldReportDuplicateIdsAndANewerVersionWithoutReadingIt() {
        String id = UUID.randomUUID().toString();
        scenario(rule(id, "{\"type\": \"timer\", \"seconds\": 1}", "{\"type\": \"showMessage\", \"text\": \"a\"}") + ","
            + rule(id, "{\"type\": \"timer\", \"seconds\": 2}", "{\"type\": \"showMessage\", \"text\": \"b\"}"));
        assertEquals("Duplicate id " + id, ScenarioValidator.validate(scene).get(0).message());

        scene.setPreservedFields(PreservedFields.of(Map.of(Scene.SCENARIO_MEMBER, "{\"version\": 2, \"teams\": []}")));
        List<ScenarioValidator.Problem> newer = ScenarioValidator.validate(scene);
        assertEquals(ScenarioValidator.Severity.NOT_RUN, newer.get(0).severity());
        assertTrue(newer.get(0).message().contains("needs a newer editor"), newer.toString());
    }

    @Test
    void shouldOnlyAcceptAPlayerUuidAsOwner() {
        assertThrows(IllegalArgumentException.class, () -> new OwnerComponent("Player 1"));
        assertEquals(List.of(), ScenarioValidator.validate(new Scene("No scenario")));
    }
}
