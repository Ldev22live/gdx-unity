package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CapsuleCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ConvexHullCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;

import java.util.function.Consumer;

/**
 * Details editors for the 3D physics components (Phase 15). Every change is an undoable command; values the
 * component rejects (a zero mass, a negative radius) are refused with a status message.
 */
public final class Physics3DComponentEditorProviders {

    private Physics3DComponentEditorProviders() {}

    private static void note(Table container, Skin skin, String text) {
        container.add(new Label(text, skin)).colspan(2).left().padBottom(4f).row();
    }

    /** Motion type, mass, damping, gravity, material and collision layer of a {@link RigidBody3DComponent}. */
    public static class RigidBody implements ComponentEditorProvider<RigidBody3DComponent, Table> {
        @Override
        public Class<RigidBody3DComponent> getComponentClass() {
            return RigidBody3DComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Rigid Body 3D";
        }

        @Override
        public void buildEditor(Table container, Entity entity, RigidBody3DComponent body, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.enumRow(container, skin, "Motion", body.getMotionType(), RigidBody3DComponent.MotionType.values(),
                value -> change(editorState, entity, body, body.getMotionType(), value, body::setMotionType, "Change Motion Type"));
            InspectorFieldRows.floatRow(container, skin, "Mass (kg)", body.getMass(),
                value -> change(editorState, entity, body, body.getMass(), value, body::setMass, "Change Mass"));
            InspectorFieldRows.floatRow(container, skin, "Lin. Damping", body.getLinearDamping(),
                value -> change(editorState, entity, body, body.getLinearDamping(), value, body::setLinearDamping, "Change Linear Damping"));
            InspectorFieldRows.floatRow(container, skin, "Ang. Damping", body.getAngularDamping(),
                value -> change(editorState, entity, body, body.getAngularDamping(), value, body::setAngularDamping, "Change Angular Damping"));
            InspectorFieldRows.floatRow(container, skin, "Gravity", body.getGravityFactor(),
                value -> change(editorState, entity, body, body.getGravityFactor(), value, body::setGravityFactor, "Change Gravity Factor"));
            InspectorFieldRows.floatRow(container, skin, "Friction", body.getFriction(),
                value -> change(editorState, entity, body, body.getFriction(), value, body::setFriction, "Change Friction"));
            InspectorFieldRows.floatRow(container, skin, "Bounciness", body.getRestitution(),
                value -> change(editorState, entity, body, body.getRestitution(), value, body::setRestitution, "Change Restitution"));
            InspectorFieldRows.intRow(container, skin, "Layer", body.getLayer(),
                value -> change(editorState, entity, body, body.getLayer(), value, body::setLayer, "Change Collision Layer"));
            InspectorFieldRows.intRow(container, skin, "Collides with", body.getCollisionMask(),
                value -> change(editorState, entity, body, body.getCollisionMask(), value, body::setCollisionMask, "Change Collision Mask"));
        }
    }

    /** Size, offset and trigger of a {@link BoxCollider3DComponent}. */
    public static class Box implements ComponentEditorProvider<BoxCollider3DComponent, Table> {
        @Override
        public Class<BoxCollider3DComponent> getComponentClass() {
            return BoxCollider3DComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Box Collider 3D";
        }

        @Override
        public void buildEditor(Table container, Entity entity, BoxCollider3DComponent box, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.vector3Row(container, skin, "Size", box.getSize(),
                value -> change(editorState, entity, box, box.getSize(), value, box::setSize, "Resize Box Collider"));
            colliderRows(container, skin, entity, box, editorState);
        }
    }

    /** Radius, offset and trigger of a {@link SphereCollider3DComponent}. */
    public static class Sphere implements ComponentEditorProvider<SphereCollider3DComponent, Table> {
        @Override
        public Class<SphereCollider3DComponent> getComponentClass() {
            return SphereCollider3DComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Sphere Collider 3D";
        }

        @Override
        public void buildEditor(Table container, Entity entity, SphereCollider3DComponent sphere, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.floatRow(container, skin, "Radius", sphere.getRadius(),
                value -> change(editorState, entity, sphere, sphere.getRadius(), value, sphere::setRadius, "Resize Sphere Collider"));
            colliderRows(container, skin, entity, sphere, editorState);
        }
    }

    /** Radius, height, offset and trigger of a {@link CapsuleCollider3DComponent}. */
    public static class Capsule implements ComponentEditorProvider<CapsuleCollider3DComponent, Table> {
        @Override
        public Class<CapsuleCollider3DComponent> getComponentClass() {
            return CapsuleCollider3DComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Capsule Collider 3D";
        }

        @Override
        public void buildEditor(Table container, Entity entity, CapsuleCollider3DComponent capsule, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.floatRow(container, skin, "Radius", capsule.getRadius(),
                value -> change(editorState, entity, capsule, capsule.getRadius(), value, capsule::setRadius, "Resize Capsule Collider"));
            InspectorFieldRows.floatRow(container, skin, "Height", capsule.getHeight(),
                value -> change(editorState, entity, capsule, capsule.getHeight(), value, capsule::setHeight, "Resize Capsule Collider"));
            colliderRows(container, skin, entity, capsule, editorState);
        }
    }

    /** Offset and trigger of a {@link ConvexHullCollider3DComponent}; its points come from the entity's mesh (TD-052). */
    public static class ConvexHull implements ComponentEditorProvider<ConvexHullCollider3DComponent, Table> {
        @Override
        public Class<ConvexHullCollider3DComponent> getComponentClass() {
            return ConvexHullCollider3DComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Convex Hull Collider 3D";
        }

        @Override
        public void buildEditor(Table container, Entity entity, ConvexHullCollider3DComponent hull, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            note(container, skin, "Built from this entity's mesh when Play starts.");
            colliderRows(container, skin, entity, hull, editorState);
        }
    }

    /** Offset and trigger of a {@link MeshCollider3DComponent}; its triangles come from the entity's mesh, static bodies only (TD-052). */
    public static class Mesh implements ComponentEditorProvider<MeshCollider3DComponent, Table> {
        @Override
        public Class<MeshCollider3DComponent> getComponentClass() {
            return MeshCollider3DComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Mesh Collider 3D";
        }

        @Override
        public void buildEditor(Table container, Entity entity, MeshCollider3DComponent mesh, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            note(container, skin, "Built from this entity's mesh. Static bodies only.");
            colliderRows(container, skin, entity, mesh, editorState);
        }
    }

    /** Capsule size, slope, step, mass, gravity and collision layer of a {@link CharacterController3DComponent} (TD-049). */
    public static class CharacterController implements ComponentEditorProvider<CharacterController3DComponent, Table> {
        @Override
        public Class<CharacterController3DComponent> getComponentClass() {
            return CharacterController3DComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Character Controller 3D";
        }

        @Override
        public void buildEditor(Table container, Entity entity, CharacterController3DComponent character, EditorState editorState,
                                Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.floatRow(container, skin, "Radius", character.getRadius(),
                value -> change(editorState, entity, character, character.getRadius(), value, character::setRadius, "Resize Character"));
            InspectorFieldRows.floatRow(container, skin, "Height", character.getHeight(),
                value -> change(editorState, entity, character, character.getHeight(), value, character::setHeight, "Resize Character"));
            InspectorFieldRows.floatRow(container, skin, "Slope limit", character.getSlopeLimitDegrees(),
                value -> change(editorState, entity, character, character.getSlopeLimitDegrees(), value, character::setSlopeLimitDegrees,
                    "Change Slope Limit"));
            InspectorFieldRows.floatRow(container, skin, "Step height", character.getStepHeight(),
                value -> change(editorState, entity, character, character.getStepHeight(), value, character::setStepHeight,
                    "Change Step Height"));
            InspectorFieldRows.floatRow(container, skin, "Mass (kg)", character.getMass(),
                value -> change(editorState, entity, character, character.getMass(), value, character::setMass, "Change Mass"));
            InspectorFieldRows.floatRow(container, skin, "Gravity", character.getGravityScale(),
                value -> change(editorState, entity, character, character.getGravityScale(), value, character::setGravityScale,
                    "Change Gravity Scale"));
            InspectorFieldRows.intRow(container, skin, "Layer", character.getLayer(),
                value -> change(editorState, entity, character, character.getLayer(), value, character::setLayer, "Change Collision Layer"));
            InspectorFieldRows.intRow(container, skin, "Seen by", character.getCollisionMask(),
                value -> change(editorState, entity, character, character.getCollisionMask(), value, character::setCollisionMask,
                    "Change Collision Mask"));
        }
    }

    private static void colliderRows(Table container, Skin skin, Entity entity, Collider3DComponent collider, EditorState editorState) {
        InspectorFieldRows.vector3Row(container, skin, "Offset", collider.getOffset(),
            value -> change(editorState, entity, collider, collider.getOffset(), value, collider::setOffset, "Move Collider"));
        InspectorFieldRows.toggleRow(container, skin, "Trigger", collider.isTrigger(),
            value -> change(editorState, entity, collider, collider.isTrigger(), value, collider::setTrigger, "Toggle Trigger"));
    }

    /**
     * Applies a change as an undoable command after checking the component accepts it, so an invalid value
     * leaves the component and the history untouched.
     */
    private static <C extends Component, V> void change(EditorState editorState, Entity entity, C component,
                                                        V oldValue, V newValue, Consumer<V> setter, String description) {
        try {
            setter.accept(newValue);
        } catch (IllegalArgumentException e) {
            editorState.setStatusMessage(e.getMessage(), true);
            return;
        }
        setter.accept(oldValue);
        editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), component,
            oldValue, newValue, setter, description));
    }
}
