package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The extent of a whole scene for the full-map view (UI M2). */
class WorldBoundsTest {
    @BeforeAll
    static void natives() {
        EditorTestHelper.initNatives();
    }

    private static Terrain hill() {
        // 11 by 11 points 100 apart: 1000 across, centred on its origin; one point raised to 250
        Terrain terrain = new Terrain(11, 11, 100f);
        terrain.setHeight(5, 5, 250f);
        return terrain;
    }

    @Test
    void shouldSpanTheTerrainGridAndItsHeightsWhereItsEntityStands() {
        Scene scene = new Scene("Map");
        Entity ground = scene.createEntity("Ground");
        ground.addComponent(new TerrainComponent("terrains/Hill.terrain"));
        ground.getTransform().setLocalPosition(new Vector3(2000f, 0f, 0f));

        BoundingBox box = WorldBounds.of(scene, Map.of("terrains/Hill.terrain", hill())::get).orElseThrow();

        assertEquals(1500f, box.min.x, 0.01f);
        assertEquals(2500f, box.max.x, 0.01f);
        assertEquals(-500f, box.min.z, 0.01f);
        assertEquals(500f, box.max.z, 0.01f);
        assertEquals(0f, box.min.y, 0.01f);
        assertEquals(250f, box.max.y, 0.01f, "up to the highest point");
    }

    @Test
    void shouldGiveOneBoxPerThingAndLeaveOutInactiveEntitiesLightsAndMissingTerrain() {
        Scene scene = new Scene("Map");
        Entity crate = scene.createEntity("Crate");
        crate.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 100f, 100f, Color.WHITE));
        crate.getTransform().setLocalPosition(new Vector3(-400f, 50f, 0f));
        Entity hidden = scene.createEntity("Hidden");
        hidden.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 100f, 100f, Color.WHITE));
        hidden.getTransform().setLocalPosition(new Vector3(90000f, 0f, 0f));
        hidden.setActive(false);
        scene.createEntity("Sun");
        scene.createEntity("Lost").addComponent(new TerrainComponent("terrains/Gone.terrain"));

        List<BoundingBox> boxes = WorldBounds.boxes(scene, path -> null);

        assertEquals(1, boxes.size(), "only the crate has an extent");
        assertEquals(-450f, boxes.get(0).min.x, 0.01f);
        assertTrue(WorldBounds.of(new Scene("Empty"), path -> null).isEmpty(), "an empty scene has none");
    }

    @Test
    void shouldWaitForEveryModelToLoadOrFailBeforeTheBoundsAreComplete() {
        Scene scene = new Scene("Map");
        ModelComponent model = new ModelComponent("models/tree.gltf");
        scene.createEntity("Tree").addComponent(model);

        assertFalse(WorldBounds.isSettled(scene), "not loaded yet: no bounds");
        model.setModelInfo(ModelComponent.ModelInfo.failed("file not found"));
        assertTrue(WorldBounds.isSettled(scene), "a model that failed will never have bounds");
        model.setModelInfo(new ModelComponent.ModelInfo(Map.of(), new BoundingBox(new Vector3(-1f, 0f, -1f), new Vector3(1f, 2f, 1f)), 0, null));
        assertTrue(WorldBounds.isSettled(scene));
        assertEquals(200f, WorldBounds.of(scene, path -> null).orElseThrow().max.y, 0.01f, "scaled from metres by the import scale");
    }
}
