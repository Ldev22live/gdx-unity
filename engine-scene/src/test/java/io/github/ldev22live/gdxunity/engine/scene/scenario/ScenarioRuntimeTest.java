package io.github.ldev22live.gdxunity.engine.scene.scenario;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bounded rule interpreter (TD-109): conditions, effects, order, once and repeat, no chaining, skipped rules, isolation. */
class ScenarioRuntimeTest {
    private static final String P1 = "11111111-1111-4111-8111-111111111111";
    private static final String P2 = "22222222-2222-4222-8222-222222222222";
    private static final String GOAL = "55555555-5555-4555-8555-555555555555";
    private static final String SECRET = "66666666-6666-4666-8666-666666666666";

    private final Scene scene = new Scene("Valley");
    private final Entity house = scene.createEntity("House Area");
    private final Entity hero = scene.createEntity("Hero");
    private final Entity rider = scene.createEntity("Rider");
    private final List<String> log = new ArrayList<>();

    {
        BoxCollider3DComponent zone = new BoxCollider3DComponent();
        zone.setTrigger(true);
        house.addComponent(zone);
        hero.addComponent(new PlayerComponent());
        rider.addComponent(new OwnerComponent(P2));
    }

    private ScenarioRuntime start(String... rules) {
        String json = """
            {"version": 1,
             "players": [{"id": "%s", "name": "One", "color": "#2f6fdf"}, {"id": "%s", "name": "Two", "color": "#d9432f"}],
             "objectives": [{"id": "%s", "text": "Reach the house"},
                            {"id": "%s", "text": "Find the cellar", "initial": "hidden"}],
             "rules": [%s]}""".formatted(P1, P2, GOAL, SECRET, String.join(",", rules));
        scene.setPreservedFields(PreservedFields.of(Map.of(Scene.SCENARIO_MEMBER, json)));
        return ScenarioRuntime.create(scene, log::add);
    }

    private static String rule(String name, boolean once, String when, String... then) {
        return """
            {"id": "%s", "name": "%s", "enabled": true, "once": %s, "when": %s, "then": [%s]}"""
            .formatted(UUID.randomUUID(), name, once, when, String.join(",", then));
    }

    private static String timer(float seconds) {
        return "{\"type\": \"timer\", \"seconds\": " + seconds + "}";
    }

    private String enter(String who) {
        return "{\"type\": \"enterArea\", \"area\": \"" + house.getId() + "\", \"who\": \"" + who + "\"}";
    }

    private static String say(String text) {
        return "{\"type\": \"showMessage\", \"text\": \"" + text + "\", \"seconds\": 2}";
    }

    private static String complete(String objective) {
        return "{\"type\": \"completeObjective\", \"objective\": \"" + objective + "\"}";
    }

    private static String end(String result) {
        return "{\"type\": \"endScenario\", \"result\": \"" + result + "\"}";
    }

    private static void run(ScenarioRuntime runtime, float seconds) {
        for (float t = 0f; t < seconds - 1e-4f; t += 0.1f) {
            runtime.update(0.1f);
        }
    }

    @Test
    void shouldShowATimedMessageOnceAndClearItAfterItsSeconds() {
        ScenarioRuntime runtime = start(rule("Welcome", true, timer(1f), say("Find the house")));

        run(runtime, 0.9f);
        assertNull(runtime.getMessage());
        run(runtime, 0.2f);
        assertEquals("Find the house", runtime.getMessage());
        run(runtime, 2.1f);
        assertNull(runtime.getMessage(), "gone after its 2 seconds");
        run(runtime, 5f);
        assertEquals(1, log.size(), "a once timer fires once: " + log);
        assertTrue(log.get(0).startsWith("Rule 'Welcome' at 1.0 s: message \"Find the house\""), log.toString());
    }

    @Test
    void shouldRepeatATimerEveryPeriodAtMostOnceAFrame() {
        ScenarioRuntime runtime = start(rule("Tick", false, timer(1f), say("tick")));

        run(runtime, 3.05f);
        assertEquals(3, log.size(), log.toString());
        runtime.update(10f);
        assertEquals(4, log.size(), "a long frame fires once, not seven times");
    }

    @Test
    void shouldCompleteTheObjectiveWhenThePlayerEntersTheArea() {
        ScenarioRuntime runtime = start(rule("Arrive", true, enter("player"), complete(GOAL), say("You made it")));

        runtime.onContact(new PhysicsSystem3D.Contact(rider, house, PhysicsSystem3D.ContactKind.TRIGGER_ENTER));
        runtime.update(0.1f);
        assertFalse(runtime.getObjectives().get(0).complete(), "someone who is not the player does not count");

        runtime.onContact(new PhysicsSystem3D.Contact(house, hero, PhysicsSystem3D.ContactKind.TRIGGER_EXIT));
        runtime.update(0.1f);
        assertFalse(runtime.getObjectives().get(0).complete(), "leaving is not entering");

        runtime.onContact(new PhysicsSystem3D.Contact(hero, house, PhysicsSystem3D.ContactKind.TRIGGER_ENTER));
        runtime.update(0.1f);
        assertTrue(runtime.getObjectives().get(0).complete());
        assertEquals("You made it", runtime.getMessage());
    }

    @Test
    void shouldMatchAScenarioPlayerByOwnershipIncludingAnOwnedParent() {
        Entity saddle = scene.createEntity("Saddle");
        saddle.getTransform().setParent(rider.getTransform(), false);
        ScenarioRuntime runtime = start(rule("Two arrives", false, enter(P2), say("Two is in")),
            rule("One arrives", false, enter(P1), say("One is in")));

        runtime.onEnter(house, saddle);
        runtime.update(0.1f);

        assertEquals(List.of("Two arrives"), log.stream().map(line -> line.split("'")[1]).toList());
    }

    @Test
    void shouldFireAOnceAreaRuleOnlyForTheFirstEntryAndARepeatRuleEachTime() {
        ScenarioRuntime runtime = start(rule("First", true, enter("player"), say("first")),
            rule("Every", false, enter("player"), say("again")));

        for (int i = 0; i < 3; i++) {
            runtime.onEnter(house, hero);
            runtime.onEnter(house, hero); // two entries in one frame fire once
            runtime.update(0.1f);
        }

        assertEquals(List.of("First", "Every", "Every", "Every"), log.stream().map(line -> line.split("'")[1]).toList());
    }

    @Test
    void shouldRunRulesInListOrderAndStopEverythingAtTheEnd() {
        ScenarioRuntime runtime = start(rule("A", true, timer(0f), say("a")),
            rule("Win", true, timer(0f), end("victory"), say("never shown")),
            rule("C", true, timer(0f), say("c")));

        runtime.update(0.1f);
        assertEquals("a", runtime.getMessage(), "effects after the end, and later rules, do not run");
        run(runtime, 5f);

        assertEquals(ScenarioRuntime.Result.VICTORY, runtime.getResult());
        assertEquals("Victory", runtime.getResult().label());
        assertEquals(List.of("A", "Win"), log.stream().map(line -> line.split("'")[1]).toList());
    }

    @Test
    void shouldRevealAHiddenObjectiveWhenItCompletes() {
        ScenarioRuntime runtime = start(rule("Secret", true, timer(1f), complete(SECRET)));
        ScenarioRuntimeComponent component = new ScenarioRuntimeComponent(runtime);

        assertEquals(List.of("[ ] Reach the house"), component.objectiveLines());
        run(runtime, 1.1f);
        assertEquals(List.of("[ ] Reach the house", "[x] Find the cellar"), component.objectiveLines());
    }

    @Test
    void shouldSkipDisabledAndFaultyRulesAndKeepUnknownOnesUnrun() {
        String broken = rule("Broken", true, "{\"type\": \"enterArea\", \"area\": \"" + UUID.randomUUID() + "\", \"who\": \"player\"}",
            say("x"));
        String future = rule("Future", true, "{\"type\": \"unitCount\", \"atLeast\": 3}", say("y"));
        String off = rule("Off", true, timer(0f), say("z")).replace("\"enabled\": true", "\"enabled\": false");
        ScenarioRuntime runtime = start(broken, future, off, rule("Fine", true, timer(0f), say("ok")));

        run(runtime, 1f);

        assertEquals(1, runtime.getRuleCount());
        assertEquals(List.of("Fine"), log.stream().map(line -> line.split("'")[1]).toList());
        assertEquals(2, runtime.getSkipped().size(), runtime.getSkipped().toString());
        assertTrue(runtime.getSkipped().get(0).startsWith("Rule 1 is not run"), runtime.getSkipped().toString());
    }

    @Test
    void shouldRunNothingForANewerVersionOrNoScenario() {
        scene.setPreservedFields(PreservedFields.of(Map.of(Scene.SCENARIO_MEMBER, "{\"version\": 2, \"rules\": []}")));
        ScenarioRuntime newer = ScenarioRuntime.create(scene, log::add);
        assertEquals(0, newer.getRuleCount());
        assertTrue(newer.getSkipped().get(0).contains("needs a newer editor"));

        assertNull(ScenarioRuntime.create(new Scene("Plain"), log::add));
    }

    @Test
    void shouldRefuseMoreEffectsThanTheLimit() {
        String[] effects = new String[ScenarioRuntime.MAX_EFFECTS + 1];
        Arrays.fill(effects, say("x"));
        assertThrows(IllegalArgumentException.class, () -> start(rule("Too many", true, timer(0f), effects)));
    }

    @Test
    void shouldAttachAsARuntimeOnlyEntityThatAdvancesWithTheSceneAndLeavesTheDataAlone() {
        start(rule("Hello", true, timer(0.5f), say("hello")));
        String before = scene.getScenarioJson();

        ScenarioRuntimeComponent component = ScenarioRuntime.attach(scene, null, null, log::add);
        for (int i = 0; i < 10; i++) {
            scene.update(0.1f);
        }

        assertEquals("hello", component.getRuntime().getMessage());
        assertEquals(ScenarioRuntimeComponent.ENTITY_NAME, component.getEntity().getName());
        assertEquals(before, scene.getScenarioJson(), "playing never changes the scenario data");
    }
}
