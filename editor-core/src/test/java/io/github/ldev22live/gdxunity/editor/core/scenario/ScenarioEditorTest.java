package io.github.ldev22live.gdxunity.editor.core.scenario;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.command.DeleteEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.SpawnPointComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scenario players, bounds and ownership edited through undoable commands (TD-108, roadmap A04). */
class ScenarioEditorTest {
    @TempDir
    Path assetRoot;

    private EditorState editorState;
    private ScenarioEditor editor;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new OwnerComponentSerializer());
        editorState.getSerializer().getRegistry().register(new SpawnPointComponentSerializer());
        editor = new ScenarioEditor(editorState);
    }

    private Scene scene() {
        return editorState.getActiveScene();
    }

    private ScenarioSetup setup() {
        return editor.current().orElseThrow();
    }

    private ScenarioSetup.PlayerSlot player(int index) {
        return setup().players().get(index);
    }

    private void importScenario(String json) {
        scene().setPreservedFields(PreservedFields.of(Map.of(Scene.SCENARIO_MEMBER, json)));
    }

    @Test
    void shouldCreateTwoNamedColouredSlotsOnlyOnTheExplicitAction() {
        assertTrue(editor.current().isEmpty(), "a scene has no scenario until players are added");

        assertTrue(editor.createScenario());

        assertEquals(List.of("Player 1", "Player 2"), setup().players().stream().map(ScenarioSetup.PlayerSlot::name).toList());
        assertFalse(player(0).color().equals(player(1).color()));
        assertFalse(player(0).neutral());
        assertEquals(List.of(), ScenarioValidator.validate(scene()));
        assertFalse(editor.createScenario(), "a second create is refused");
        assertTrue(editorState.undo());
        assertNull(scene().getScenarioJson(), "undo removes the whole block");
    }

    @Test
    void shouldKeepThePlayerIdThroughRenameRecolourUndoAndRedo() {
        editor.createScenario();
        String id = player(0).id();

        assertTrue(editor.renamePlayer(id, "  Red Banner "));
        assertTrue(editor.setPlayerColor(id, "#AA0000"));
        assertEquals("Red Banner", player(0).name());
        assertEquals("#aa0000", player(0).color());
        assertEquals(id, player(0).id());

        assertTrue(editorState.undo());
        assertTrue(editorState.undo());
        assertEquals("Player 1", player(0).name());
        assertEquals(id, player(0).id());
        assertTrue(editorState.redo());
        assertEquals("Red Banner", player(0).name());
        assertEquals(id, player(0).id());
    }

    @Test
    void shouldRefuseBlankLongAndDuplicateNamesIgnoringCase() {
        editor.createScenario();
        String id = player(0).id();
        String before = scene().getScenarioJson();

        assertFalse(editor.renamePlayer(id, "   "));
        assertFalse(editor.renamePlayer(id, "x".repeat(ScenarioSetup.MAX_NAME_LENGTH + 1)));
        assertFalse(editor.renamePlayer(id, "PLAYER 2"));
        assertTrue(editorState.getStatusMessage().contains("already called 'Player 2'"), editorState.getStatusMessage());
        assertFalse(editor.setPlayerColor(id, "red"));
        assertSame(before, scene().getScenarioJson(), "refused edits leave the scenario untouched");
        assertTrue(editor.renamePlayer(id, "player 1 "), "a slot may change the case of its own name");
    }

    @Test
    void shouldShowImportedDuplicateNamesByIdWithoutChangingThem() {
        String a = "11111111-1111-4111-8111-111111111111";
        String b = "22222222-2222-4222-8222-222222222222";
        importScenario("{\"version\":1,\"players\":[{\"id\":\"" + a + "\",\"name\":\"Blue\",\"color\":\"#0000ff\"},"
            + "{\"id\":\"" + b + "\",\"name\":\"blue\",\"color\":\"#0000aa\"}],\"objectives\":[],\"rules\":[]}");

        assertEquals("Blue (11111111)", setup().displayName(player(0)));
        assertEquals("blue (22222222)", setup().displayName(player(1)));
        assertTrue(editor.setPlayerColor(b, "#123456"), "other edits still work");
        assertEquals("blue", player(1).name(), "imported duplicates are kept");
    }

    @Test
    void shouldAddPlayersUpToTheLimitAndOneNeutral() {
        editor.createScenario();

        assertTrue(editor.addNeutral());
        assertFalse(editor.addNeutral(), "one neutral slot");
        ScenarioSetup.PlayerSlot neutral = player(2);
        assertTrue(neutral.neutral());
        assertEquals(ScenarioSetup.NEUTRAL_NAME, neutral.name());
        while (setup().players().size() < ScenarioSetup.MAX_PLAYERS) {
            assertTrue(editor.addPlayer());
        }
        assertFalse(editor.addPlayer());
        assertEquals("Player 3", player(3).name(), "the next free name");
        assertEquals(ScenarioSetup.MAX_PLAYERS, setup().players().stream().map(ScenarioSetup.PlayerSlot::id).distinct().count());
    }

    @Test
    void shouldAssignReassignAndClearOwnershipInOneUndoStepEach() {
        editor.createScenario();
        editor.addNeutral();
        String p1 = player(0).id();
        String p2 = player(1).id();
        String nature = player(2).id();
        Entity tower = scene().createEntity("Tower");
        Entity wall = scene().createEntity("Wall");
        Entity tree = scene().createEntity("Tree");

        assertTrue(editor.assignOwner(List.of(tower.getId(), wall.getId()), p1));
        assertTrue(editor.assignOwner(List.of(tree.getId()), nature));
        assertTrue(editor.assignOwner(List.of(wall.getId()), p2));
        assertEquals(p2, wall.getComponent(OwnerComponent.class).getPlayerId());
        assertEquals(1, wall.getComponents(OwnerComponent.class).size(), "reassigning replaces, never stacks");
        assertEquals(List.of(), ScenarioValidator.validate(scene()));

        assertTrue(editorState.undo());
        assertEquals(p1, wall.getComponent(OwnerComponent.class).getPlayerId());
        assertTrue(editorState.undo());
        assertNull(tree.getComponent(OwnerComponent.class));
        assertTrue(editorState.undo());
        assertNull(tower.getComponent(OwnerComponent.class));
        assertNull(wall.getComponent(OwnerComponent.class));
        assertTrue(editorState.redo());
        assertEquals(p1, tower.getComponent(OwnerComponent.class).getPlayerId());

        assertTrue(editor.assignOwner(List.of(tower.getId()), null));
        assertNull(tower.getComponent(OwnerComponent.class), "Unowned has no component");
        assertFalse(editor.assignOwner(List.of(tower.getId()), null), "no change, no undo step");
        assertFalse(editor.assignOwner(List.of(tower.getId()), UUID.randomUUID().toString()), "unknown player refused");
    }

    @Test
    void shouldRefuseToRemoveAPlayerThatOwnsObjectsOrIsNamedByAnObjectiveOrRule() {
        editor.createScenario();
        String p1 = player(0).id();
        String p2 = player(1).id();
        Entity tower = scene().createEntity("Tower");
        editor.assignOwner(List.of(tower.getId()), p1);

        assertFalse(editor.removePlayer(p1));
        assertTrue(editorState.getStatusMessage().contains("it owns 'Tower'"), editorState.getStatusMessage());

        ScenarioSetup withObjective = ScenarioSetup.parse(scene().getScenarioJson().replace("\"objectives\":[]",
            "\"objectives\":[{\"id\":\"" + UUID.randomUUID() + "\",\"text\":\"Hold the hill\",\"player\":\"" + p2 + "\"}]"));
        importScenario(withObjective.toJson());
        assertFalse(editor.removePlayer(p2));
        assertTrue(editorState.getStatusMessage().contains("objective 'Hold the hill'"), editorState.getStatusMessage());

        editor.assignOwner(List.of(tower.getId()), null);
        assertTrue(editor.removePlayer(p1));
        assertEquals(1, setup().players().size());
        assertTrue(editorState.undo());
        assertEquals(p1, player(0).id(), "undo brings back the same player");
    }

    @Test
    void shouldRefuseToRemoveAnyPlayerWhileARuleCannotBeChecked() {
        editor.createScenario();
        String p2 = player(1).id();
        importScenario(scene().getScenarioJson().replace("\"rules\":[]", "\"rules\":[{\"id\":\"" + UUID.randomUUID()
            + "\",\"name\":\"Future\",\"enabled\":true,\"once\":true,\"when\":{\"type\":\"unitCount\"},"
            + "\"then\":[{\"type\":\"showMessage\",\"text\":\"x\"}]}]"));

        assertFalse(editor.removePlayer(p2));
        assertTrue(editorState.getStatusMessage().contains("rule 'Future' has a type this version cannot check"),
            editorState.getStatusMessage());
    }

    @Test
    void shouldOnlyAcceptASpawnPointAsSpawnAndProtectItFromDeletion() {
        editor.createScenario();
        String p1 = player(0).id();
        Entity rock = scene().createEntity("Rock");
        Entity spawn = scene().createEntity("Start");
        spawn.addComponent(new SpawnPointComponent());

        assertFalse(editor.setSpawn(p1, rock.getId()));
        assertEquals(List.of(spawn), editor.spawnCandidates());
        assertTrue(editor.setSpawn(p1, spawn.getId()));
        assertEquals(spawn.getId().toString(), player(0).spawn());

        assertFalse(editorState.executeCommand(new DeleteEntityCommand(editorState, spawn.getId())));
        assertTrue(editorState.getStatusMessage().contains("spawn of Player 1"), editorState.getStatusMessage());
        assertNotNull(scene().findEntityById(spawn.getId()));
        assertTrue(editorState.executeCommand(new DeleteEntityCommand(editorState, rock.getId())), "unreferenced entities delete");

        assertTrue(editor.setSpawn(p1, null));
        assertTrue(editorState.executeCommand(new DeleteEntityCommand(editorState, spawn.getId())));
    }

    @Test
    void shouldSetAndClearFiniteBoundsAndRejectInvalidOnes() {
        editor.createScenario();

        assertThrows(IllegalArgumentException.class, () -> new ScenarioSetup.Bounds(5, 0, 5, 10));
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSetup.Bounds(0, 0, Double.POSITIVE_INFINITY, 10));
        assertTrue(editor.setBounds(new ScenarioSetup.Bounds(-50, -40.5, 50, 40.5)));
        assertEquals(new ScenarioSetup.Bounds(-50, -40.5, 50, 40.5), setup().bounds().orElseThrow());
        assertTrue(scene().getScenarioJson().contains("\"bounds\":{\"minX\":-50,\"minZ\":-40.5,\"maxX\":50,\"maxZ\":40.5}"),
            scene().getScenarioJson());
        assertEquals(List.of(), ScenarioValidator.validate(scene()));
        assertTrue(editor.setBounds(null));
        assertTrue(setup().bounds().isEmpty());
        assertTrue(editorState.undo());
        assertTrue(setup().bounds().isPresent());
    }

    @Test
    void shouldPreserveObjectivesRulesAndUnknownDataWhenEditingPlayers() {
        String p1 = "11111111-1111-4111-8111-111111111111";
        String objectives = "[{\"id\":\"55555555-5555-4555-8555-555555555555\",\"text\":\"Reach the house\",\"futureFlag\":3}]";
        String rules = "[{\"id\":\"66666666-6666-4666-8666-666666666666\",\"name\":\"Hello\",\"enabled\":true,\"once\":true,"
            + "\"when\":{\"type\":\"timer\",\"seconds\":2},\"then\":[{\"type\":\"showMessage\",\"text\":\"Go\"}]}]";
        importScenario("{\"version\":1,\"teams\":{\"a\":[1,2]},\"players\":[{\"id\":\"" + p1
            + "\",\"name\":\"One\",\"color\":\"#010203\",\"faction\":\"north\"}],\"objectives\":" + objectives
            + ",\"rules\":" + rules + "}");

        assertTrue(editor.renamePlayer(p1, "First"));

        String json = scene().getScenarioJson();
        assertEquals("{\"version\":1,\"teams\":{\"a\":[1,2]},\"players\":[{\"id\":\"" + p1
            + "\",\"name\":\"First\",\"color\":\"#010203\",\"faction\":\"north\"}],\"objectives\":" + objectives
            + ",\"rules\":" + rules + "}", json, "only the name changed; order and unknown members kept");
    }

    @Test
    void shouldKeepANewerScenarioReadOnly() {
        String newer = "{\"version\":2,\"players\":[{\"id\":\"x\"}],\"teams\":[]}";
        importScenario(newer);
        Entity tower = scene().createEntity("Tower");

        assertTrue(setup().isReadOnly());
        assertFalse(editor.addPlayer());
        assertTrue(editorState.getStatusMessage().contains("needs a newer editor"), editorState.getStatusMessage());
        assertFalse(editor.assignOwner(List.of(tower.getId()), "11111111-1111-4111-8111-111111111111"));
        assertEquals(newer, scene().getScenarioJson());
        assertEquals(newer, setup().toJson(), "a read-only block is written back exactly");
    }

    @Test
    void shouldSurviveSaveAndReopenWithOwnersSpawnAndBounds() {
        editor.createScenario();
        editor.addNeutral();
        Entity spawn = scene().createEntity("Start");
        spawn.addComponent(new SpawnPointComponent());
        Entity tower = scene().createEntity("Tower");
        Entity tree = scene().createEntity("Tree");
        editor.setSpawn(player(0).id(), spawn.getId());
        editor.assignOwner(List.of(tower.getId()), player(1).id());
        editor.assignOwner(List.of(tree.getId()), player(2).id());
        editor.setBounds(new ScenarioSetup.Bounds(-10, -10, 10, 10));
        String before = scene().getScenarioJson();

        Scene reopened = editorState.getSerializer().fromJson(editorState.getSerializer().toJson(scene()));

        ScenarioSetup setup = ScenarioSetup.read(reopened).orElseThrow();
        assertEquals(before, setup.toJson());
        assertEquals(spawn.getId().toString(), setup.players().get(0).spawn());
        assertEquals(setup.players().get(1).id(), reopened.findEntityByName("Tower").getComponent(OwnerComponent.class).getPlayerId());
        assertEquals(setup.players().get(2).id(), reopened.findEntityByName("Tree").getComponent(OwnerComponent.class).getPlayerId());
        assertEquals(List.of(), ScenarioValidator.validate(reopened));
    }

    @Test
    void shouldKeepOwnershipGivenToAPrefabInstanceThroughSaveAndReopen() throws Exception {
        PrefabService prefabs = new PrefabService(editorState, assetRoot.toFile());
        editorState.setPrefabService(prefabs);
        editor.createScenario();
        String p2 = player(1).id();
        AssetReference prefab = prefabs.createPrefab(scene().createEntity("Barracks"));
        Entity instance = prefabs.instantiate(prefab, new Vector3(10f, 0f, 0f));
        assertTrue(editor.assignOwner(List.of(instance.getId()), p2));

        prefabs.storeOverrides(scene());
        Scene reopened = editorState.getSerializer().fromJson(editorState.getSerializer().toJson(scene()));
        assertEquals(List.of(), prefabs.syncScene(reopened));

        Entity again = reopened.findEntityById(instance.getId());
        assertEquals(p2, again.getComponent(OwnerComponent.class).getPlayerId(), "the owner is an instance override, kept by sync");
    }
}
