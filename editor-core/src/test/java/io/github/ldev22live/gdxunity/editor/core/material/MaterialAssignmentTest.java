package io.github.ldev22live.gdxunity.editor.core.material;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M2.2: a material dropped on a shape, or put in its slot from Details, applies in one undo step. */
class MaterialAssignmentTest {
    @TempDir
    Path project;

    private EditorState editorState;
    private MaterialLibrary library;
    private Entity box;
    private MeshPrimitiveComponent shape;
    private String rock;
    private String wood;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new MeshPrimitiveComponentSerializer());
        registry.register(new ModelComponentSerializer());
        editorState = new EditorState(new SceneSerializer(registry));
        library = new MaterialLibrary(AssetSource.directory(project.toFile()));
        rock = MaterialAssets.createNew(project.toFile(), library, "Rock");
        wood = MaterialAssets.createNew(project.toFile(), library, "Wood");
        box = editorState.getActiveScene().createEntity("Box");
        shape = box.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 100f, 100f, Color.WHITE));
    }

    @Test
    void shouldApplyAMaterialInOneUndoStepThatUndoesAndRedoes() {
        assertEquals(MaterialAssignment.Result.APPLIED, MaterialAssignment.assign(editorState, library, box, rock));
        assertEquals(rock, shape.getMaterialPath());
        assertEquals("Apply Material Rock.material to Box", editorState.getStatusMessage());

        assertEquals(MaterialAssignment.Result.APPLIED, MaterialAssignment.assign(editorState, library, box, wood));
        assertEquals(wood, shape.getMaterialPath());

        assertTrue(editorState.undo());
        assertEquals(rock, shape.getMaterialPath(), "one undo takes back one drop");
        assertTrue(editorState.undo());
        assertEquals("", shape.getMaterialPath());
        assertFalse(editorState.getCommandHistory().canUndo(), "each drop was exactly one step");
        assertTrue(editorState.redo());
        assertEquals(rock, shape.getMaterialPath());
    }

    @Test
    void shouldKeepTheAppliedMaterialThroughSaveAndReopen() {
        MaterialAssignment.assign(editorState, library, box, rock);

        SceneSerializer serializer = editorState.getSerializer();
        Scene reopened = serializer.fromJson(serializer.toJson(editorState.getActiveScene()));

        assertEquals(rock, reopened.findEntityById(box.getId()).getComponent(MeshPrimitiveComponent.class).getMaterialPath());
    }

    @Test
    void shouldRemoveTheMaterialWithABlankPath() {
        MaterialAssignment.assign(editorState, library, box, rock);

        assertEquals(MaterialAssignment.Result.APPLIED, MaterialAssignment.assign(editorState, library, box, "  "));
        assertEquals("", shape.getMaterialPath());
        assertEquals("Remove Material from Box", editorState.getStatusMessage());
    }

    @Test
    void shouldAddNoUndoStepWhenTheSlotAlreadyHasTheMaterial() {
        MaterialAssignment.assign(editorState, library, box, rock);
        editorState.undo();
        editorState.redo();

        assertEquals(MaterialAssignment.Result.UNCHANGED, MaterialAssignment.assign(editorState, library, box, rock));
        assertTrue(editorState.undo());
        assertFalse(editorState.getCommandHistory().canUndo(), "only the first assignment is in the history");
    }

    @Test
    void shouldRefuseWithTheReasonAndChangeNothingWhenItCannotApply() {
        Entity robot = editorState.getActiveScene().createEntity("Robot");
        robot.addComponent(new ModelComponent("models/robot.glb"));

        assertEquals(MaterialAssignment.Result.NO_TARGET, MaterialAssignment.assign(editorState, library, null, rock));
        assertTrue(editorState.getStatusMessage().startsWith("Drop the material on a shape"), editorState.getStatusMessage());

        assertEquals(MaterialAssignment.Result.NO_SLOT, MaterialAssignment.assign(editorState, library, robot, rock));
        assertEquals("Robot has no material slot: only shapes (Mesh Primitive) take a material so far", editorState.getStatusMessage());

        assertEquals(MaterialAssignment.Result.NOT_A_MATERIAL, MaterialAssignment.assign(editorState, library, box, "textures/rock.png"));
        assertEquals(MaterialAssignment.Result.UNREADABLE, MaterialAssignment.assign(editorState, library, box, "materials/Gone.material"));
        assertEquals(library.getError("materials/Gone.material"), editorState.getStatusMessage());

        assertEquals("", shape.getMaterialPath());
        assertFalse(editorState.getCommandHistory().canUndo());
        assertTrue(editorState.isStatusError());
    }

    @Test
    void shouldRefuseDuringPlay() {
        editorState.setPlayModeController(new io.github.ldev22live.gdxunity.editor.core.play.PlayModeController(editorState));
        assertTrue(editorState.getPlayModeController().play());

        assertEquals(MaterialAssignment.Result.REJECTED, MaterialAssignment.assign(editorState, library, box, rock));
        editorState.getPlayModeController().stop();
        assertEquals("", box.getComponent(MeshPrimitiveComponent.class).getMaterialPath());
    }

    @Test
    void shouldKnowWhichEntitiesHaveASlot() {
        Entity robot = editorState.getActiveScene().createEntity("Robot");
        robot.addComponent(new ModelComponent("models/robot.glb"));

        assertTrue(MaterialAssignment.hasSlot(box));
        assertFalse(MaterialAssignment.hasSlot(robot));
        assertFalse(MaterialAssignment.hasSlot(null));
    }
}
