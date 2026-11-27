package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.scenario.ScenarioEditor;
import io.github.ldev22live.gdxunity.editor.core.scenario.ScenarioSetup;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.TerrainCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The placement Owner selector (TD-108, roadmap A04): Unowned by default; a chosen owner applies to new placements only, in the same
 * undo step as the placement; a removed owner makes placement refuse instead of silently placing unowned; owned objects come back after
 * save and reopen. Real starter-pack prefabs on a real level.
 */
class PlacementOwnerTest {
    private static final File SHIPPED = new File("../assets");
    private static final String TREE = "prefabs/level/CC0 Oak Tree.prefab";
    private static final String HOUSE = "prefabs/buildings/CC0 House.prefab";

    @TempDir
    Path projects;

    private EditorState editorState;
    private LevelSession level;
    private ScenarioEditor scenario;
    private SceneSerializer serializer;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws IOException {
        File project = Files.createDirectories(projects.resolve("Owned Game")).toFile();
        StarterPalette.addTo(SHIPPED, new TemplateProject.Result(project, "scenes/level.json", List.of()), GameMode.MODE_3D);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new TerrainComponentSerializer());
        registry.register(new TerrainCollider3DComponentSerializer());
        registry.register(new ModelComponentSerializer());
        registry.register(new PrefabInstanceComponentSerializer());
        registry.register(new OwnerComponentSerializer());
        serializer = new SceneSerializer(registry);
        editorState = new EditorState(serializer);
        Scene scene = new Scene("Level");
        scene.setGameMode(GameMode.MODE_3D);
        editorState.setActiveScene(scene);
        editorState.setPrefabService(new PrefabService(editorState, project));
        level = new LevelSession(editorState, new TerrainSession(new TerrainLibrary(AssetSource.directory(project))), project);
        assertNotNull(level.createLevel(Surface.GRASS));
        scenario = new ScenarioEditor(editorState);
        assertTrue(scenario.createScenario());
    }

    private ScenarioSetup.PlayerSlot player(int index) {
        return scenario.current().orElseThrow().players().get(index);
    }

    private Entity place(String prefab, float x, float z) {
        level.setObjectPath(prefab);
        Entity placed = level.placeObject(x, 0f, z);
        assertNotNull(placed, prefab + ": " + editorState.getStatusMessage());
        return placed;
    }

    private static String ownerOf(Entity entity) {
        OwnerComponent owner = entity.getComponent(OwnerComponent.class);
        return owner == null ? null : owner.getPlayerId();
    }

    @Test
    void shouldPlaceUnownedByDefault() {
        assertNull(level.getPlacementOwner());
        assertNull(ownerOf(place(TREE, 0f, 0f)));
    }

    @Test
    void shouldGiveTwoGroupsTheirOwnersAndKeepEarlierPlacementsWhenTheSelectorChanges() {
        String p1 = player(0).id();
        String p2 = player(1).id();

        level.setPlacementOwner(p1);
        Entity tree1 = place(TREE, -200f, 0f);
        Entity house1 = place(HOUSE, -400f, 0f);
        level.setPlacementOwner(p2);
        Entity tree2 = place(TREE, 200f, 0f);
        Entity house2 = place(HOUSE, 400f, 0f);

        assertEquals(p1, ownerOf(tree1));
        assertEquals(p1, ownerOf(house1), "choosing another owner leaves earlier placements alone");
        assertEquals(p2, ownerOf(tree2));
        assertEquals(p2, ownerOf(house2));
        assertEquals(List.of(), ScenarioValidator.validate(editorState.getActiveScene()));

        assertTrue(editorState.undo(), "placing and owning are one step");
        assertNull(editorState.getActiveScene().findEntityById(house2.getId()));
        assertTrue(editorState.redo());
        Entity redone = editorState.getActiveScene().findEntityById(house2.getId());
        assertEquals(p2, ownerOf(redone));
        assertEquals(1, redone.getComponents(OwnerComponent.class).size());

        assertTrue(scenario.assignOwner(List.of(tree1.getId()), p2), "reassignment in the selection");
        assertEquals(p2, ownerOf(tree1));
    }

    @Test
    void shouldRefusePlacementWhenTheChosenOwnerIsNoLongerAPlayer() {
        assertTrue(scenario.addPlayer());
        String p3 = player(2).id();
        level.setPlacementOwner(p3);
        level.setObjectPath(TREE);
        assertNull(level.placementRefusal());

        assertTrue(scenario.removePlayer(p3));

        assertTrue(level.placementRefusal().contains("not a player of this scene"), level.placementRefusal());
        int before = editorState.getActiveScene().getAllEntities().size();
        assertNull(level.placeObject(0f, 0f, 0f), "never placed silently unowned");
        assertEquals(before, editorState.getActiveScene().getAllEntities().size());
        level.setPlacementOwner(null);
        assertNull(level.placementRefusal());
    }

    @Test
    void shouldKeepOwnedPlacementsThroughSaveAndReopen() throws Exception {
        String p1 = player(0).id();
        level.setPlacementOwner(p1);
        Entity house = place(HOUSE, 0f, 0f);
        String scenarioBefore = editorState.getActiveScene().getScenarioJson();

        editorState.getPrefabService().storeOverrides(editorState.getActiveScene());
        Scene reopened = serializer.fromJson(serializer.toJson(editorState.getActiveScene()));
        assertEquals(List.of(), editorState.getPrefabService().syncScene(reopened));

        assertEquals(p1, ownerOf(reopened.findEntityById(house.getId())));
        assertEquals(scenarioBefore, reopened.getScenarioJson());
        assertEquals(List.of(), ScenarioValidator.validate(reopened));
    }
}
