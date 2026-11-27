package io.github.ldev22live.gdxunity.editor.core.scenario;

import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CharacterController3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.PlayerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.SpawnPointComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioRuntime;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap A14: a scenario authored entirely in the web editor ({@code engine-babylon/test/scenarioEditing.test.ts} writes the committed
 * step {@code fixtures/contract-v1/interop/w2-web}) is one the desktop validates, edits and plays: players, a neutral slot, a spawn,
 * owners, bounds, an objective, an area and two rules.
 */
class WebAuthoredScenarioTest {
    private static final Path SCENE = Path.of("..", "fixtures", "contract-v1", "interop", "w2-web", "scenes", "web-scenario.json");

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldValidateEditAndPlayAScenarioMadeOnTheWeb() throws IOException {
        EditorState editorState = new EditorState();
        for (var serializer : List.of(new BoxCollider3DComponentSerializer(), new CharacterController3DComponentSerializer(),
            new PlayerComponentSerializer(), new SpawnPointComponentSerializer(), new OwnerComponentSerializer())) {
            editorState.getSerializer().getRegistry().register(serializer);
        }
        Scene scene = editorState.getSerializer().fromJson(Files.readString(SCENE));
        editorState.setActiveScene(scene);

        assertEquals(List.of(), ScenarioValidator.validate(scene), "the web's scenario is valid on the desktop");
        ScenarioSetup setup = ScenarioSetup.read(scene).orElseThrow();
        assertFalse(setup.isReadOnly());
        assertEquals(List.of("Player 1", "Raiders", "Neutral"), setup.players().stream().map(ScenarioSetup.PlayerSlot::name).toList());
        assertTrue(setup.players().get(2).neutral());
        Entity start = scene.findEntityByName("Start");
        assertEquals(start.getId().toString(), setup.players().get(0).spawn());
        assertEquals(setup.players().get(1).id(), scene.findEntityByName("Tower").getComponent(OwnerComponent.class).getPlayerId());
        assertEquals(new ScenarioSetup.Bounds(-2000, -2000, 2000, 2000), setup.bounds().orElseThrow());
        RulesEditor rules = new RulesEditor(editorState);
        assertEquals(List.of("Welcome", "Arrive"), rules.rules().stream().map(r -> r.getString("name")).toList());

        // The desktop can go on editing it: a rename and a timer change, each one undo step
        ScenarioEditor players = new ScenarioEditor(editorState);
        assertTrue(players.renamePlayer(setup.players().get(1).id(), "Marauders"), editorState.getStatusMessage());
        JsonValue welcome = rules.rules().get(0);
        assertTrue(rules.setTimer(welcome.getString("id"), 2f), editorState.getStatusMessage());
        assertEquals(List.of(), ScenarioValidator.validate(scene));

        // And plays it: the welcome after its timer, the win when the Hero enters the gate area
        List<String> log = new ArrayList<>();
        ScenarioRuntime runtime = ScenarioRuntime.create(scene, log::add);
        for (int i = 0; i < 8; i++) {
            runtime.update(0.25f);
        }
        assertEquals("Find the gate", runtime.getMessage());
        String gate = rules.rules().get(1).get("when").getString("area");
        runtime.onEnter(scene.findEntityById(UUID.fromString(gate)), scene.findEntityByName("Hero"));
        runtime.update(0.25f);
        assertEquals(ScenarioRuntime.Result.VICTORY, runtime.getResult());
        assertEquals(2, log.size(), log.toString());
    }
}
