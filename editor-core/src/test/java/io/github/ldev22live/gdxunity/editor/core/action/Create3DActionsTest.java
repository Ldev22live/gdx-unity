package io.github.ldev22live.gdxunity.editor.core.action;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Create3DActionsTest {

    private static final Vector3 SPAWN = new Vector3(320f, 240f, 0f);

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private EditorState editorState;
    private EditorActionRegistry registry;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        registry = new EditorActionRegistry();
        DefaultEditorActions.registerCreate3DActions(registry, editorState, () -> new Vector3(SPAWN));
    }

    @Test
    void shouldCreateSelectedBoxAtSpawnPointAsOneUndoStep() {
        int before = editorState.getActiveScene().getAllEntities().size();
        assertTrue(registry.execute(DefaultEditorActions.CREATE_BOX));

        Entity box = editorState.getSelectedEntity();
        assertNotNull(box);
        assertEquals("Box", box.getName());
        assertEquals(SPAWN, box.getTransform().getWorldPosition());
        assertEquals(MeshPrimitiveComponent.Shape.BOX, box.getComponent(MeshPrimitiveComponent.class).getShape());

        editorState.undo();
        assertEquals(before, editorState.getActiveScene().getAllEntities().size(), "One undo removes entity and component");

        editorState.redo();
        Entity redone = editorState.getActiveScene().findEntityByName("Box");
        assertSame(box, redone);
        assertNotNull(redone.getComponent(MeshPrimitiveComponent.class), "Redo keeps the component");
    }

    @Test
    void shouldCreatePointLightWithEditorRange() {
        assertTrue(registry.execute(DefaultEditorActions.CREATE_POINT_LIGHT));
        PointLightComponent light = editorState.getSelectedEntity().getComponent(PointLightComponent.class);
        assertEquals(ComponentCatalog.createPointLight().getRange(), light.getRange(), 0.0001f);
    }

    @Test
    void shouldPickAndBoundPrimitiveInThreeDimensions() {
        registry.execute(DefaultEditorActions.CREATE_BOX);
        Entity box = editorState.getSelectedEntity();

        BoundingBox bounds = ViewportPicker.computeMeshBounds(box);
        assertEquals(new Vector3(270f, 190f, -50f), bounds.min);
        assertEquals(new Vector3(370f, 290f, 50f), bounds.max);

        // A ray from above hits the box top; a 2D-style plane test at z=0 would also work, but this ray is vertical
        Ray fromAbove = new Ray(new Vector3(320f, 1000f, 0f), new Vector3(0f, -1f, 0f));
        assertEquals(box, ViewportPicker.pickEntity3D(editorState.getActiveScene(), fromAbove));

        Ray miss = new Ray(new Vector3(600f, 1000f, 0f), new Vector3(0f, -1f, 0f));
        assertNull(ViewportPicker.pickEntity3D(editorState.getActiveScene(), miss));

        // The 2D picker sees the box's XY footprint
        assertEquals(box, ViewportPicker.pickEntityAt(editorState.getActiveScene(), 360f, 280f));
    }
}
