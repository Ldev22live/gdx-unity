package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ShapeRendererComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.DirectionalLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.RigidBody3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateEntityCommandTest {
    private EditorState editorState;

    @BeforeEach
    void setUp() {
        // Duplicate copies components through their serializers, so the registry must know the shape, as the editor's does
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new ShapeRendererComponentSerializer());
        editorState = new EditorState(new SceneSerializer(registry));
        editorState.newScene("Duplicate Test Scene");
    }

    @Test
    void shouldDuplicateEntityWithIndependentIdAndUndoRedo() {
        Entity original = editorState.createEntity("Player");
        original.getTransform().setLocalPosition(100f, 200f, 0f);
        original.addComponent(new ShapeRendererComponent(50f, 50f, com.badlogic.gdx.graphics.Color.RED));

        editorState.setSelectedEntity(original);
        assertEquals(1, editorState.getActiveScene().getEntityCount());

        // Duplicate
        Entity copy = editorState.duplicateSelectedEntity();
        assertNotNull(copy);
        assertNotEquals(original.getId(), copy.getId());
        assertEquals("Player (Copy)", copy.getName());
        assertEquals(120f, copy.getTransform().getLocalPosition().x);
        assertEquals(220f, copy.getTransform().getLocalPosition().y);
        assertNotNull(copy.getComponent(ShapeRendererComponent.class));
        assertEquals(2, editorState.getActiveScene().getEntityCount());

        // Selection should be updated to copy
        assertEquals(copy.getId(), editorState.getSelectedEntityId());

        // Undo duplication
        assertTrue(editorState.undo());
        assertEquals(1, editorState.getActiveScene().getEntityCount());
        assertNull(editorState.getActiveScene().findEntityById(copy.getId()));

        // Redo duplication
        assertTrue(editorState.redo());
        assertEquals(2, editorState.getActiveScene().getEntityCount());
        assertNotNull(editorState.getActiveScene().findEntityById(copy.getId()));
        assertNotNull(copy.getComponent(ShapeRendererComponent.class), "redo must bring the copy's components back");
    }

    @Test
    void shouldSayHowManyComponentsHadNoSerializerAndWereNotCopied() {
        // The default registry knows no component types, so the shape cannot be copied
        EditorState bare = new EditorState();
        bare.newScene("Bare Scene");
        Entity original = bare.createEntity("Player");
        original.addComponent(new ShapeRendererComponent(50f, 50f, com.badlogic.gdx.graphics.Color.RED));
        bare.setSelectedEntity(original);

        Entity copy = bare.duplicateSelectedEntity();

        assertNotNull(copy);
        assertNull(copy.getComponent(ShapeRendererComponent.class));
        assertTrue(bare.getStatusMessage().contains("1 component(s) without a serializer were not copied"), bare.getStatusMessage());
    }

    @Test
    void shouldCopyEveryRegisteredComponentTypeNotOnlyTheShapeAndPhysics2DOnes() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new ModelComponentSerializer());
        registry.register(new DirectionalLightComponentSerializer());
        registry.register(new RigidBody3DComponentSerializer());
        registry.register(new BoxCollider3DComponentSerializer());
        EditorState state = new EditorState(new SceneSerializer(registry));
        state.newScene("Duplicate Registry Scene");

        Entity original = state.createEntity("Crate");
        ModelComponent model = new ModelComponent("models/crate.glb");
        model.setImportScale(3f);
        original.addComponent(model);
        DirectionalLightComponent sun = new DirectionalLightComponent();
        sun.setCastShadows(false);
        original.addComponent(sun);
        RigidBody3DComponent body = new RigidBody3DComponent();
        body.setMass(7f);
        original.addComponent(body);
        original.addComponent(new BoxCollider3DComponent());
        state.setSelectedEntity(original);

        Entity copy = state.duplicateSelectedEntity();

        assertNotNull(copy);
        ModelComponent copiedModel = copy.getComponent(ModelComponent.class);
        assertNotNull(copiedModel, "model must survive Duplicate");
        assertNotSame(model, copiedModel);
        assertEquals("models/crate.glb", copiedModel.getModelPath());
        assertEquals(3f, copiedModel.getImportScale(), 0.001f);
        assertFalse(copy.getComponent(DirectionalLightComponent.class).isCastShadows());
        assertEquals(7f, copy.getComponent(RigidBody3DComponent.class).getMass(), 0.001f);
        assertNotNull(copy.getComponent(BoxCollider3DComponent.class));
        assertEquals(original.getAllComponents().size(), copy.getAllComponents().size());

        // Undo and redo keep working with the copied components
        assertTrue(state.undo());
        assertNull(state.getActiveScene().findEntityById(copy.getId()));
        assertTrue(state.redo());
        assertNotNull(state.getActiveScene().findEntityById(copy.getId()).getComponent(ModelComponent.class));
    }

    @Test
    void shouldFailGracefullyWhenNoEntitySelected() {
        assertNull(editorState.duplicateSelectedEntity());
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }
}
