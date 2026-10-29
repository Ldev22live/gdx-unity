package io.github.ldev22live.gdxunity.editor.core.component;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Physics3DCatalogTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private static ComponentCatalog.Entry entry(ComponentCatalog catalog, String name) {
        return catalog.getEntries().stream().filter(e -> e.displayName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void shouldFitNewCollidersToTheSelectedMeshAndUndo() {
        EditorState editorState = new EditorState();
        ComponentCatalog catalog = ComponentCatalog.createDefault();
        Entity box = editorState.getActiveScene().createEntity("Box");
        box.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 40f, 60f, 80f, Color.WHITE));
        editorState.setSelectedEntity(box);

        assertTrue(catalog.addTo(editorState, box, entry(catalog, "Box Collider 3D")));
        assertTrue(catalog.addTo(editorState, box, entry(catalog, "Sphere Collider 3D")));
        assertTrue(catalog.addTo(editorState, box, entry(catalog, "Rigid Body 3D")));
        assertFalse(catalog.addTo(editorState, box, entry(catalog, "Rigid Body 3D")), "One rigid body per entity");

        assertEquals(new Vector3(40f, 60f, 80f), box.getComponent(BoxCollider3DComponent.class).getSize());
        assertEquals(40f, box.getComponent(SphereCollider3DComponent.class).getRadius(), "Encloses the largest side");
        assertEquals(RigidBody3DComponent.MotionType.DYNAMIC, box.getComponent(RigidBody3DComponent.class).getMotionType());
        assertEquals("Physics 3D", entry(catalog, "Capsule Collider 3D").category());

        editorState.undo();
        assertNull(box.getComponent(RigidBody3DComponent.class));
    }
}
