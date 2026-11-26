package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff.Capability;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff.Notice;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3c.4: what 3D Modeling says it can do for each kind of selection. Until R7a no object's mesh is editable, the notice says so
 * plainly, and a transform is never described as a mesh edit.
 */
class ModelingHandOffTest {
    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldHandAGltfHouseToTheMeshSourceEditor() {
        Entity house = new Entity("House");
        house.addComponent(new ModelComponent("models/house.glb"));

        Notice notice = ModelingHandOff.noticeFor(house);

        assertTrue(ModelingHandOff.offersEdit3DModel(house));
        assertEquals("Edit 3D Model of House (opens mesh source)", ModelingHandOff.edit3DModelLabel(house));
        assertEquals(Capability.MESH_SOURCE, notice.capability());
        assertTrue(notice.isShown());
        assertTrue(notice.meshEditing());
        assertTrue(notice.sculptCopy());
        assertFalse(notice.materialSlot(), "a glTF model has no material slot (the M2.2 gap)");
        assertEquals("models/house.glb", notice.modelPath());
    }

    @Test
    void shouldPointAShapeToItsMaterialInDetails() {
        Entity box = new Entity("Box");
        box.addComponent(new MeshPrimitiveComponent());

        Notice notice = ModelingHandOff.noticeFor(box);

        assertTrue(ModelingHandOff.offersEdit3DModel(box));
        assertEquals(Capability.MESH_PLANNED, notice.capability());
        assertEquals("Editing the vertices, edges and faces of Box is planned (roadmap R7a).", notice.lines().get(0),
            "a shape has no file to name");
        assertEquals("Its material: Customize > Edit Material, in Details.", notice.lines().get(3));
        assertTrue(notice.materialSlot());
        assertNull(notice.modelPath());
    }

    @Test
    void shouldNotOfferEdit3DModelForASculptAnObjectWithNoMeshOrNoSelection() {
        Entity rock = new Entity("Rock");
        rock.addComponent(new DynamicMeshComponent("sculpts/rock.sculpt"));
        Entity lamp = new Entity("Lamp");

        assertFalse(ModelingHandOff.offersEdit3DModel(rock), "a sculpt has its own entry, Edit Rock in 3D");
        assertFalse(ModelingHandOff.offersEdit3DModel(lamp));
        assertFalse(ModelingHandOff.offersEdit3DModel(null));

        assertEquals(Capability.SCULPT, ModelingHandOff.noticeFor(rock).capability());
        assertFalse(ModelingHandOff.noticeFor(rock).isShown(), "the brush strip says what a sculpt can do");
        assertEquals(Capability.NO_MESH, ModelingHandOff.noticeFor(lamp).capability());
        assertEquals("Lamp has no 3D mesh", ModelingHandOff.noticeFor(lamp).title());
        assertEquals(Capability.NO_SELECTION, ModelingHandOff.noticeFor(null).capability());
        assertEquals("Nothing selected. Select a model, shape or sculpt in the World to work on it here.",
            ModelingHandOff.noticeFor(null).summary());
    }

    @Test
    void shouldOfferMeshSourceEditingOnlyForModelParts() {
        Entity house = new Entity("House");
        house.addComponent(new ModelComponent("models/house.glb"));
        Entity rock = new Entity("Rock");
        rock.addComponent(new DynamicMeshComponent("sculpts/rock.sculpt"));
        Entity box = new Entity("Box");
        box.addComponent(new MeshPrimitiveComponent());

        for (Entity entity : Arrays.asList(house, rock, box, new Entity("Empty"), null)) {
            Notice notice = ModelingHandOff.noticeFor(entity);
            assertEquals(entity == house, notice.meshEditing());
            for (String line : notice.lines()) {
                assertFalse(line.startsWith("Move") && !line.contains("never its mesh"), "a transform is never a mesh edit: " + line);
            }
        }
    }
}
