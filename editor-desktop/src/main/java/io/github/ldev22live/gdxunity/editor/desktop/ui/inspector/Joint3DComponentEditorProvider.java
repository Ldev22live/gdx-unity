package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent;

import java.util.function.Consumer;

/**
 * Details editor of a {@link Joint3DComponent} (TD-051): the type, what it is joined to, anchor, axis, limits and motor. Every
 * change is an undoable command; values the component rejects are refused with a status message.
 */
public class Joint3DComponentEditorProvider implements ComponentEditorProvider<Joint3DComponent, Table> {
    @Override
    public Class<Joint3DComponent> getComponentClass() {
        return Joint3DComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Joint 3D";
    }

    @Override
    public void buildEditor(Table container, Entity entity, Joint3DComponent joint, EditorState state, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        InspectorFieldRows.enumRow(container, skin, "Type", joint.getType(), Joint3DComponent.Type.values(),
            value -> change(state, entity, joint, joint.getType(), value, joint::setType, "Change Joint Type"));
        InspectorFieldRows.textRow(container, skin, "Joined to", joint.getConnectedName(),
            value -> change(state, entity, joint, joint.getConnectedName(), value, joint::setConnectedName, "Change Joint Connection"));
        InspectorFieldRows.vector3Row(container, skin, "Anchor", joint.getAnchor(),
            value -> change(state, entity, joint, joint.getAnchor(), value, joint::setAnchor, "Change Joint Anchor"));
        InspectorFieldRows.vector3Row(container, skin, "Other end", joint.getConnectedAnchor(),
            value -> change(state, entity, joint, joint.getConnectedAnchor(), value, joint::setConnectedAnchor, "Change Joint Other End"));
        InspectorFieldRows.vector3Row(container, skin, "Axis", joint.getAxis(),
            value -> change(state, entity, joint, joint.getAxis(), value, joint::setAxis, "Change Joint Axis"));
        InspectorFieldRows.toggleRow(container, skin, "Collide", joint.isCollideConnected(),
            value -> change(state, entity, joint, joint.isCollideConnected(), value, joint::setCollideConnected, "Toggle Joint Collision"));
        InspectorFieldRows.toggleRow(container, skin, "Limits", joint.isLimitsEnabled(),
            value -> change(state, entity, joint, joint.isLimitsEnabled(), value, joint::setLimitsEnabled, "Toggle Joint Limits"));
        InspectorFieldRows.floatRow(container, skin, "Lower", joint.getLowerLimit(),
            value -> change(state, entity, joint, joint.getLowerLimit(), value, v -> joint.setLimits(v, joint.getUpperLimit()), "Change Lower Limit"));
        InspectorFieldRows.floatRow(container, skin, "Upper", joint.getUpperLimit(),
            value -> change(state, entity, joint, joint.getUpperLimit(), value, v -> joint.setLimits(joint.getLowerLimit(), v), "Change Upper Limit"));
        InspectorFieldRows.enumRow(container, skin, "Motor", joint.getMotorMode(), Joint3DComponent.MotorMode.values(),
            value -> change(state, entity, joint, joint.getMotorMode(), value, joint::setMotorMode, "Change Joint Motor"));
        InspectorFieldRows.floatRow(container, skin, "Motor goal", joint.getMotorTarget(),
            value -> change(state, entity, joint, joint.getMotorTarget(), value, joint::setMotorTarget, "Change Motor Target"));
        InspectorFieldRows.floatRow(container, skin, "Max force", joint.getMotorMaxForce(),
            value -> change(state, entity, joint, joint.getMotorMaxForce(), value, joint::setMotorMaxForce, "Change Motor Force"));
    }

    /** Applies a change as an undoable command after checking the component accepts it. */
    private static <V> void change(EditorState state, Entity entity, Joint3DComponent joint, V oldValue, V newValue,
                                   Consumer<V> setter, String description) {
        try {
            setter.accept(newValue);
        } catch (IllegalArgumentException e) {
            state.setStatusMessage(e.getMessage(), true);
            return;
        }
        setter.accept(oldValue);
        state.executeCommand(new ModifyComponentPropertyCommand<>(state, entity.getId(), joint, oldValue, newValue, setter, description));
    }
}
