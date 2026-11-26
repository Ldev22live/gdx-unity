package io.github.ldev22live.gdxunity.editor.core.prefab;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.command.DuplicateEntityCommand;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.PreservedComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ownership (TD-106, roadmap A12.6) and preserved data travel with an entity through duplication, prefab creation, prefab
 * instantiation and undo, because those copy components through the scene serializer.
 */
class OwnershipPreservationTest {
    private static final String PLAYER_2 = "66666666-6666-4666-8666-666666666666";

    @TempDir
    Path assetRoot;

    private EditorState editorState;
    private PrefabService prefabs;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new MeshPrimitiveComponentSerializer());
        editorState.getSerializer().getRegistry().register(new OwnerComponentSerializer());
        prefabs = new PrefabService(editorState, assetRoot.toFile());
        editorState.setPrefabService(prefabs);
    }

    /** A building owned by player 2, carrying a component and a field from another client. */
    private Entity ownedTower() {
        Scene scene = editorState.getActiveScene();
        Entity tower = scene.createEntity("Tower");
        MeshPrimitiveComponent mesh = new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 300f, 100f, Color.GRAY);
        mesh.setPreservedData(PreservedFields.of(Map.of("futureWeathering", "{\"amount\": 0.4}")));
        tower.addComponent(mesh);
        tower.addComponent(new OwnerComponent(PLAYER_2));
        tower.addComponent(new PreservedComponent("GarrisonComponent", "{\"slots\": 4}"));
        return tower;
    }

    private static void assertCarriesEverything(Entity copy) {
        assertEquals(PLAYER_2, copy.getComponent(OwnerComponent.class).getPlayerId(), "owned by the same player id");
        PreservedComponent garrison = copy.getComponent(PreservedComponent.class);
        assertNotNull(garrison, "the other client's component came along");
        assertEquals("GarrisonComponent", garrison.getTypeId());
        assertEquals("{\"slots\":4}", garrison.getRawData().replace(" ", ""));
        assertEquals("{\"amount\":0.4}", copy.getComponent(MeshPrimitiveComponent.class).getPreservedData().raw("futureWeathering")
            .replace(" ", ""), "the unknown field came along");
    }

    @Test
    void shouldKeepOwnerAndPreservedDataWhenDuplicatingAndUndoing() {
        Entity tower = ownedTower();

        DuplicateEntityCommand duplicate = new DuplicateEntityCommand(editorState, tower.getId());
        assertTrue(editorState.executeCommand(duplicate));
        Entity copy = duplicate.getDuplicatedEntity();

        assertNotSame(tower, copy);
        assertCarriesEverything(copy);
        assertTrue(editorState.undo());
        assertTrue(editorState.redo());
        Entity redone = editorState.getActiveScene().findEntityById(copy.getId());
        assertCarriesEverything(redone);
        redone.getComponent(OwnerComponent.class).setPlayerId("77777777-7777-4777-8777-777777777777");
        assertEquals(PLAYER_2, tower.getComponent(OwnerComponent.class).getPlayerId(), "the copy's owner is its own");
    }

    @Test
    void shouldKeepOwnerAndPreservedDataInAPrefabAndItsInstances() {
        Entity tower = ownedTower();

        AssetReference prefab = prefabs.createPrefab(tower);
        Entity instance = prefabs.instantiate(prefab, new Vector3(500f, 0f, 0f));

        assertNotNull(instance);
        assertCarriesEverything(instance);
    }
}
