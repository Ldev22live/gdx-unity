package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportFocus;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshWorkspaceTest {
    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldOpenPrefabPartWithItsPlacedWorldTransform() throws IOException {
        String prefab = "prefabs/buildings/CC0 House.prefab";
        String wallPath = "thirdparty/kenney/building-kit/Models/GLB format/wall.glb";
        Scene scene = new Scene("World");
        Entity house = scene.createEntity("House");
        house.addComponent(new PrefabInstanceComponent(prefab, UUID.randomUUID(), true));
        house.getTransform().setLocalPosition(100, 0, 200);
        Entity wall = scene.createEntity("Wall");
        wall.addComponent(new ModelComponent(wallPath));
        wall.getComponent(ModelComponent.class).setImportScale(2);
        wall.getTransform().setParent(house.getTransform());
        wall.getTransform().setLocalPosition(10, 20, 30);
        Entity roof = scene.createEntity("Roof");
        roof.addComponent(new ModelComponent("models/missing-roof.glb"));
        roof.getTransform().setParent(house.getTransform());
        roof.getTransform().setLocalPosition(200, 20, 30);
        MeshWorkspace workspace = new MeshWorkspace(Path.of("..", "assets"), house);

        assertTrue(ModelingHandOff.offersEdit3DModel(house));
        assertTrue(ViewportFocus.boundingRadius(house) >= 95f);
        assertEquals(prefab, workspace.prefabPath());
        assertEquals(List.of(wall, roof), workspace.parts());

        MeshWorkspace.OpenPart opened = workspace.open(wall);
        assertSame(opened, workspace.activePart());
        assertEquals(wallPath, opened.mesh().sourcePath());
        assertEquals(new Vector3(110, 20, 230), opened.worldPlacement().getTranslation(new Vector3()));
        assertEquals(new Vector3(2, 2, 2), opened.worldPlacement().getScale(new Vector3()));
        assertSame(house.getTransform(), wall.getTransform().getParent());
        assertEquals(wallPath, wall.getComponent(ModelComponent.class).getModelPath());
        assertThrows(IOException.class, () -> workspace.open(roof));
        assertNull(workspace.activePart());
    }
}
