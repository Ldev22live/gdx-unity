package io.github.ldev22live.gdxunity.editor.core.prefab;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetPlacement;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.PointLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PrefabServiceTest {

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
        editorState.getSerializer().getRegistry().register(new PointLightComponentSerializer());
        prefabs = new PrefabService(editorState, assetRoot.toFile());
        editorState.setPrefabService(prefabs);
    }

    /** "Crate" (box) at (50, 0, 0) with a child "Lamp" (point light) at (0, 30, 0). */
    private Entity createCrate() {
        Scene scene = editorState.getActiveScene();
        Entity crate = scene.createEntity("Crate");
        crate.getTransform().setLocalPosition(50f, 0f, 0f);
        crate.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 100f, 100f, Color.WHITE));
        Entity lamp = scene.createEntity("Lamp");
        lamp.getTransform().setParent(crate.getTransform(), false);
        lamp.getTransform().setLocalPosition(0f, 30f, 0f);
        lamp.addComponent(new PointLightComponent());
        return crate;
    }

    private static Entity child(Entity parent, String name) {
        for (var t : parent.getTransform().getChildren()) {
            if (t.getEntity().getName().equals(name)) {
                return t.getEntity();
            }
        }
        return null;
    }

    private static MeshPrimitiveComponent mesh(Entity entity) {
        return entity.getComponent(MeshPrimitiveComponent.class);
    }

    @Test
    void shouldSavePrefabFileAndLinkTheOriginalAsAnInstance() throws Exception {
        Entity crate = createCrate();
        AssetReference asset = prefabs.createPrefab(crate);

        assertEquals("prefabs/Crate.prefab", asset.getPath());
        assertEquals(AssetType.PREFAB, AssetType.fromPath(asset.getPath()));
        File file = new File(assetRoot.toFile(), asset.getPath());
        PrefabDocument document = PrefabDocument.parse(Files.readString(file.toPath()));
        assertEquals(2, document.getEntityIds().size());
        assertEquals(0f, document.getEntity(document.getRootId()).get("transform").get("position").getFloat("x"),
            "The prefab root sits at its own origin");
        assertFalse(Files.readString(file.toPath()).contains("PrefabInstance"), "Links are not part of the prefab");

        assertTrue(PrefabService.linkOf(crate).isRoot());
        assertNotNull(PrefabService.linkOf(child(crate, "Lamp")));
        assertEquals(crate, PrefabService.instanceRootOf(child(crate, "Lamp")));
        assertEquals(0, prefabs.countInstanceOverrides(crate), "Placement and name are not overrides");

        editorState.undo();
        assertNull(PrefabService.linkOf(crate), "Undo unlinks; the asset file stays");
        assertTrue(file.exists());
        assertNotNull(mesh(crate));

        assertEquals("prefabs/Crate 1.prefab", prefabs.createPrefab(crate).getPath(), "Existing files are not overwritten");
    }

    @Test
    void shouldPlaceIndependentLinkedInstancesInOneUndoStepEach() {
        AssetReference asset = prefabs.createPrefab(createCrate());
        int before = editorState.getActiveScene().getEntityCount();

        assertEquals(AssetPlacement.Result.PLACED, AssetPlacement.place(editorState, asset, new Vector3(300f, 0f, 0f)));
        Entity placed = editorState.getSelectedEntity();
        assertEquals("Crate", placed.getName());
        assertEquals(new Vector3(300f, 0f, 0f), placed.getTransform().getLocalPosition());
        Entity lamp = child(placed, "Lamp");
        assertEquals(30f, lamp.getTransform().getLocalPosition().y, 0.001f);
        assertNotNull(lamp.getComponent(PointLightComponent.class));
        assertEquals(before + 2, editorState.getActiveScene().getEntityCount());

        mesh(placed).setColor(Color.RED);
        Entity second = prefabs.instantiate(asset, new Vector3());
        assertEquals(Color.WHITE, mesh(second).getColor(), "Instances do not share component objects");

        editorState.undo();
        editorState.undo();
        assertEquals(before, editorState.getActiveScene().getEntityCount());
        editorState.redo();
        assertEquals(before + 2, editorState.getActiveScene().getEntityCount());
        assertSame(placed, editorState.getActiveScene().findEntityById(placed.getId()), "Redo restores the same objects");
    }

    @Test
    void shouldReportChangedPropertiesAsOverrides() {
        Entity crate = createCrate();
        prefabs.createPrefab(crate);

        crate.getTransform().setLocalPosition(999f, 1f, 1f);
        crate.setName("Renamed");
        assertTrue(prefabs.getOverrides(crate).isEmpty(), "Root name and placement belong to the instance");

        mesh(crate).setColor(Color.RED);
        crate.getTransform().setLocalScale(2f, 2f, 2f);
        Set<String> overrides = prefabs.getOverrides(crate);
        assertTrue(overrides.contains("component/MeshPrimitiveComponent#0/data/colorG"));
        assertTrue(overrides.contains("transform/scale/x"));
        assertFalse(overrides.contains("component/MeshPrimitiveComponent#0/data/colorR"), "Red stayed 1");

        Entity lamp = child(crate, "Lamp");
        lamp.removeComponent(lamp.getComponent(PointLightComponent.class));
        assertEquals(Set.of("component/PointLightComponent#0"), prefabs.getOverrides(lamp), "A removed component is one override");
    }

    @Test
    void shouldApplyOverridesToThePrefabAndOtherInstancesKeepingTheirOwn() throws Exception {
        AssetReference asset = prefabs.createPrefab(createCrate());
        Entity a = prefabs.instantiate(asset, new Vector3(0f, 0f, 0f));
        Entity b = prefabs.instantiate(asset, new Vector3(500f, 0f, 0f));
        mesh(b).setColor(Color.BLUE);
        mesh(a).setSize(10f, 20f, 30f);
        String fileBefore = Files.readString(assetRoot.resolve(asset.getPath()));
        MeshPrimitiveComponent meshOfBBefore = mesh(b);

        assertTrue(prefabs.applyAll(child(a, "Lamp")), "Apply works from any entity of the instance");

        assertEquals(new Vector3(10f, 20f, 30f), mesh(b).getSize(), "Other instances follow the prefab");
        assertEquals(Color.BLUE, mesh(b).getColor(), "...but keep their own overrides");
        assertEquals(new Vector3(500f, 0f, 0f), b.getTransform().getLocalPosition());
        assertTrue(prefabs.getOverrides(a).isEmpty());
        assertTrue(prefabs.getOverrides(b).contains("component/MeshPrimitiveComponent#0/data/colorR"));
        assertNotEquals(fileBefore, Files.readString(assetRoot.resolve(asset.getPath())));

        editorState.undo();
        assertEquals(fileBefore, Files.readString(assetRoot.resolve(asset.getPath())), "Undo restores the prefab file");
        assertEquals(new Vector3(100f, 100f, 100f), mesh(b).getSize());
        assertSame(meshOfBBefore, mesh(b), "Undo puts back the original component object, keeping older commands valid");
        assertEquals(new Vector3(10f, 20f, 30f), mesh(a).getSize(), "The instance keeps its edit as an override again");
        assertFalse(prefabs.getOverrides(a).isEmpty());
    }

    @Test
    void shouldAddAndRemovePrefabEntitiesInOtherInstancesOnApply() {
        AssetReference asset = prefabs.createPrefab(createCrate());
        Entity a = prefabs.instantiate(asset, new Vector3());
        Entity b = prefabs.instantiate(asset, new Vector3(500f, 0f, 0f));

        Entity handle = editorState.getActiveScene().createEntity("Handle");
        handle.getTransform().setParent(a.getTransform(), false);
        editorState.getActiveScene().removeEntity(child(a, "Lamp"));
        prefabs.applyAll(a);

        assertNotNull(child(b, "Handle"), "Entity added to the prefab appears in other instances");
        assertNull(child(b, "Lamp"), "Entity removed from the prefab disappears from other instances");
        assertNotNull(PrefabService.linkOf(handle), "The added entity is now linked");
        assertEquals(6, editorState.getActiveScene().getEntityCount(), "Three instances (the original crate too) of Crate + Handle");
    }

    @Test
    void shouldRevertAllOverridesRestoringDeletedAndRemovingAddedEntities() {
        AssetReference asset = prefabs.createPrefab(createCrate());
        Entity instance = prefabs.instantiate(asset, new Vector3(70f, 0f, 0f));
        mesh(instance).setColor(Color.RED);
        editorState.getActiveScene().removeEntity(child(instance, "Lamp"));
        Entity extra = editorState.getActiveScene().createEntity("Extra");
        extra.getTransform().setParent(instance.getTransform(), false);

        assertTrue(prefabs.revertAll(instance));
        assertEquals(Color.WHITE, mesh(instance).getColor());
        assertNotNull(child(instance, "Lamp"), "Deleted prefab entity comes back");
        assertNull(child(instance, "Extra"), "Added entity is removed");
        assertEquals(new Vector3(70f, 0f, 0f), instance.getTransform().getLocalPosition(), "Placement is kept");

        editorState.undo();
        assertEquals(Color.RED, mesh(instance).getColor());
        assertNotNull(child(instance, "Extra"));
    }

    @Test
    void shouldRevertOneComponentOnly() {
        AssetReference asset = prefabs.createPrefab(createCrate());
        Entity instance = prefabs.instantiate(asset, new Vector3());
        mesh(instance).setColor(Color.RED);
        instance.getTransform().setLocalScale(3f, 3f, 3f);

        assertTrue(prefabs.revertComponent(instance, mesh(instance)));
        assertEquals(Color.WHITE, mesh(instance).getColor());
        assertEquals(3f, instance.getTransform().getLocalScale().x, 0.001f, "Other overrides stay");
        assertFalse(prefabs.revertComponent(instance, mesh(instance)), "Nothing left to revert");
    }

    @Test
    void shouldKeepSavedOverridesWhenThePrefabChangedWhileTheSceneWasClosed() throws Exception {
        AssetReference asset = prefabs.createPrefab(createCrate());
        Entity instance = prefabs.instantiate(asset, new Vector3(5f, 0f, 0f));
        mesh(instance).setColor(Color.RED);
        File sceneFile = assetRoot.resolve("level.json").toFile();
        editorState.saveSceneToFile(sceneFile);

        editorState.newScene("Other");
        Entity editor = prefabs.instantiate(asset, new Vector3());
        mesh(editor).setSize(1f, 2f, 3f);
        Entity bulb = editorState.getActiveScene().createEntity("Bulb");
        bulb.getTransform().setParent(editor.getTransform(), false);
        prefabs.applyAll(editor);

        editorState.loadSceneFromFile(sceneFile);
        Entity loaded = editorState.getActiveScene().findEntityById(instance.getId());
        assertEquals(new Vector3(1f, 2f, 3f), mesh(loaded).getSize(), "Prefab change reaches the saved instance");
        assertEquals(Color.RED, mesh(loaded).getColor(), "Its override survives");
        assertEquals(new Vector3(5f, 0f, 0f), loaded.getTransform().getLocalPosition());
        assertNotNull(child(loaded, "Bulb"), "Entity added to the prefab later is created");
        assertFalse(editorState.isStatusError());
    }

    @Test
    void shouldLoadSceneWithMissingPrefabAsSavedAndSayWhich() throws Exception {
        AssetReference asset = prefabs.createPrefab(createCrate());
        File sceneFile = assetRoot.resolve("level.json").toFile();
        editorState.saveSceneToFile(sceneFile);
        Files.delete(assetRoot.resolve(asset.getPath()));

        editorState.loadSceneFromFile(sceneFile);
        assertEquals(2, editorState.getActiveScene().getEntityCount());
        assertTrue(editorState.isStatusError());
        assertTrue(editorState.getStatusMessage().contains("Crate"));
    }

    @Test
    void shouldUnpackIntoPlainEntities() {
        AssetReference asset = prefabs.createPrefab(createCrate());
        Entity instance = prefabs.instantiate(asset, new Vector3());
        assertTrue(prefabs.unpack(child(instance, "Lamp")));
        assertNull(PrefabService.linkOf(instance));
        assertNull(PrefabService.linkOf(child(instance, "Lamp")));
        assertNotNull(mesh(instance));
        assertFalse(prefabs.applyAll(instance), "Nothing to apply to once unpacked");
        editorState.undo();
        assertNotNull(PrefabService.linkOf(instance));
        assertNotNull(PrefabService.linkOf(child(instance, "Lamp")));
    }

    @Test
    void shouldRoundTripLinksThroughTheSceneFile() {
        Entity crate = createCrate();
        prefabs.createPrefab(crate);
        String json = editorState.getSerializer().toJson(editorState.getActiveScene());
        Scene loaded = editorState.getSerializer().fromJson(json);
        PrefabInstanceComponent link = PrefabService.linkOf(loaded.findEntityById(crate.getId()));
        assertEquals("prefabs/Crate.prefab", link.getPrefabPath());
        assertEquals(PrefabService.linkOf(crate).getSourceId(), link.getSourceId());
        assertEquals(2, link.getKnownSourceIds().size());
        assertTrue(link.isRoot());
    }

    @Test
    void shouldRejectInvalidPrefabDocuments() {
        List<String> invalid = List.of(
            "",
            "{\"schemaVersion\":1,\"kind\":\"scene\",\"rootId\":\"00000000-0000-0000-0000-000000000001\",\"entities\":[]}",
            "{\"schemaVersion\":9,\"kind\":\"prefab\",\"rootId\":\"00000000-0000-0000-0000-000000000001\",\"entities\":[]}",
            "{\"schemaVersion\":1,\"kind\":\"prefab\",\"rootId\":\"00000000-0000-0000-0000-000000000001\",\"entities\":["
                + "{\"id\":\"00000000-0000-0000-0000-000000000001\"},"
                + "{\"id\":\"00000000-0000-0000-0000-000000000002\",\"parentId\":\"00000000-0000-0000-0000-000000000003\"},"
                + "{\"id\":\"00000000-0000-0000-0000-000000000003\",\"parentId\":\"00000000-0000-0000-0000-000000000002\"}]}");
        for (String text : invalid) {
            assertThrows(RuntimeException.class, () -> PrefabDocument.parse(text), text);
        }
    }
}
