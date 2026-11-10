package io.github.ldev22live.gdxunity.editor.core.component;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ConvexHullCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshCollider3DComponent;
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
    void shouldOfferACameraRigOncePerEntityAndUndoIt() {
        EditorState editorState = new EditorState();
        ComponentCatalog catalog = ComponentCatalog.createDefault();
        Entity camera = editorState.getActiveScene().createEntity("Camera");
        editorState.setSelectedEntity(camera);
        ComponentCatalog.Entry rig = entry(catalog, "Camera Rig");

        assertEquals("Game", rig.category());
        assertTrue(catalog.addTo(editorState, camera, rig));
        assertFalse(catalog.addTo(editorState, camera, rig), "one rig per entity");
        assertEquals(io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent.Mode.FOLLOW,
            camera.getComponent(io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent.class).getMode());

        editorState.undo();
        assertEquals(null, camera.getComponent(io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent.class));
    }

    @Test
    void shouldOfferAJointOncePerEntityRefuseCharactersAndUndo() {
        EditorState editorState = new EditorState();
        ComponentCatalog catalog = ComponentCatalog.createDefault();
        Entity door = editorState.getActiveScene().createEntity("Door");
        editorState.setSelectedEntity(door);
        ComponentCatalog.Entry joint = entry(catalog, "Joint 3D");

        assertEquals("Physics 3D", joint.category());
        assertTrue(catalog.addTo(editorState, door, joint));
        assertFalse(catalog.addTo(editorState, door, joint), "one joint per entity");
        assertEquals(Joint3DComponent.Type.HINGE, door.getComponent(Joint3DComponent.class).getType());
        editorState.undo();
        assertNull(door.getComponent(Joint3DComponent.class));

        Entity hero = editorState.getActiveScene().createEntity("Hero");
        assertTrue(catalog.addTo(editorState, hero, entry(catalog, "Character Controller 3D")));
        assertFalse(catalog.canAdd(hero, joint), "a character cannot be joined");
        Entity hung = editorState.getActiveScene().createEntity("Hung");
        assertTrue(catalog.addTo(editorState, hung, joint));
        assertFalse(catalog.canAdd(hung, entry(catalog, "Character Controller 3D")), "and an entity with a joint cannot become one");
    }

    @Test
    void shouldOfferMeshCollidersOnlyOnAnEntityThatShowsAMeshAndNotOnMovingBodies() {
        EditorState editorState = new EditorState();
        ComponentCatalog catalog = ComponentCatalog.createDefault();
        ComponentCatalog.Entry meshCollider = entry(catalog, "Mesh Collider 3D");
        ComponentCatalog.Entry hullCollider = entry(catalog, "Convex Hull Collider 3D");
        Entity bare = editorState.getActiveScene().createEntity("Bare");

        assertFalse(catalog.canAdd(bare, meshCollider), "nothing to build the collider from");
        assertFalse(catalog.canAdd(bare, hullCollider));
        assertFalse(catalog.addTo(editorState, bare, meshCollider));
        assertTrue(editorState.getStatusMessage().contains("Bare"), editorState.getStatusMessage());

        Entity hill = editorState.getActiveScene().createEntity("Hill");
        hill.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 40f, 60f, 80f, Color.WHITE));
        assertTrue(catalog.addTo(editorState, hill, meshCollider));
        assertFalse(catalog.addTo(editorState, hill, meshCollider), "one mesh collider per entity");
        assertEquals("Physics 3D", meshCollider.category());
        editorState.undo();
        assertNull(hill.getComponent(MeshCollider3DComponent.class));

        RigidBody3DComponent moving = new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC);
        hill.addComponent(moving);
        assertFalse(catalog.canAdd(hill, meshCollider), "triangle meshes need a static body");
        assertTrue(catalog.addTo(editorState, hill, hullCollider), "a convex hull works on a moving body");
        assertTrue(hill.getComponent(ConvexHullCollider3DComponent.class) != null);
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

    @Test
    void shouldKeepACharacterControllerApartFromRigidBodiesAndColliders() {
        EditorState editorState = new EditorState();
        ComponentCatalog catalog = ComponentCatalog.createDefault();
        Entity crate = editorState.getActiveScene().createEntity("Crate");
        Entity hero = editorState.getActiveScene().createEntity("Hero");
        assertTrue(catalog.addTo(editorState, crate, entry(catalog, "Box Collider 3D")));

        assertFalse(catalog.canAdd(crate, entry(catalog, "Character Controller 3D")), "a collider is already its body");
        assertFalse(catalog.addTo(editorState, crate, entry(catalog, "Character Controller 3D")));
        assertTrue(editorState.getStatusMessage().contains("Crate") && editorState.getStatusMessage().contains("Character Controller 3D"),
            editorState.getStatusMessage());

        assertTrue(catalog.addTo(editorState, hero, entry(catalog, "Character Controller 3D")));
        assertFalse(catalog.addTo(editorState, hero, entry(catalog, "Character Controller 3D")), "one controller per entity");
        assertFalse(catalog.canAdd(hero, entry(catalog, "Rigid Body 3D")));
        assertFalse(catalog.canAdd(hero, entry(catalog, "Capsule Collider 3D")));
        assertTrue(editorState.getStatusMessage().length() > 0);
        assertNotNull(hero.getComponent(CharacterController3DComponent.class));

        editorState.undo();
        assertNull(hero.getComponent(CharacterController3DComponent.class));
        assertTrue(catalog.canAdd(hero, entry(catalog, "Rigid Body 3D")), "free again after undo");
    }
}
