package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.function.Consumer;

/**
 * Details editor of a {@link CameraRigComponent} (TD-050): the mode, the target's name and the settings of every mode. Each
 * change is an undoable command; values the component rejects are refused with a status message.
 */
public class CameraRigComponentEditorProvider implements ComponentEditorProvider<CameraRigComponent, Table> {
    @Override
    public Class<CameraRigComponent> getComponentClass() {
        return CameraRigComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Camera Rig";
    }

    @Override
    public void buildEditor(Table container, Entity entity, CameraRigComponent rig, EditorState state, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        InspectorFieldRows.enumRow(container, skin, "Mode", rig.getMode(), CameraRigComponent.Mode.values(),
            value -> change(state, entity, rig, rig.getMode(), value, rig::setMode, "Change Camera Rig Mode"));
        InspectorFieldRows.textRow(container, skin, "Target name", rig.getTargetName(),
            value -> change(state, entity, rig, rig.getTargetName(), value, rig::setTargetName, "Change Camera Rig Target"));
        InspectorFieldRows.vector3Row(container, skin, "Follow offset", rig.getFollowOffset(),
            value -> change(state, entity, rig, rig.getFollowOffset(), value, rig::setFollowOffset, "Change Follow Offset"));
        InspectorFieldRows.floatRow(container, skin, "Follow rate", rig.getFollowRate(),
            value -> change(state, entity, rig, rig.getFollowRate(), value, rig::setFollowRate, "Change Follow Rate"));
        InspectorFieldRows.floatRow(container, skin, "Look-at height", rig.getPivotHeight(),
            value -> change(state, entity, rig, rig.getPivotHeight(), value, rig::setPivotHeight, "Change Pivot Height"));
        InspectorFieldRows.floatRow(container, skin, "Eye height", rig.getEyeHeight(),
            value -> change(state, entity, rig, rig.getEyeHeight(), value, rig::setEyeHeight, "Change Eye Height"));
        InspectorFieldRows.floatRow(container, skin, "Yaw", rig.getYaw(),
            value -> change(state, entity, rig, rig.getYaw(), value, rig::setYaw, "Change Camera Yaw"));
        InspectorFieldRows.floatRow(container, skin, "Pitch", rig.getPitch(),
            value -> change(state, entity, rig, rig.getPitch(), value, rig::setPitch, "Change Camera Pitch"));
        InspectorFieldRows.floatRow(container, skin, "Distance", rig.getDistance(),
            value -> change(state, entity, rig, rig.getDistance(), value, rig::setDistance, "Change Camera Distance"));
        InspectorFieldRows.toggleRow(container, skin, "Keep out of walls", rig.isOcclusionEnabled(),
            value -> change(state, entity, rig, rig.isOcclusionEnabled(), value, rig::setOcclusionEnabled, "Toggle Camera Occlusion"));
        InspectorFieldRows.floatRow(container, skin, "Wall margin", rig.getOcclusionMargin(),
            value -> change(state, entity, rig, rig.getOcclusionMargin(), value, rig::setOcclusionMargin, "Change Occlusion Margin"));
    }

    /** Applies a change as an undoable command after checking the component accepts it. */
    private static <V> void change(EditorState state, Entity entity, CameraRigComponent rig, V oldValue, V newValue,
                                   Consumer<V> setter, String description) {
        try {
            setter.accept(newValue);
        } catch (IllegalArgumentException e) {
            state.setStatusMessage(e.getMessage(), true);
            return;
        }
        setter.accept(oldValue);
        state.executeCommand(new ModifyComponentPropertyCommand<>(state, entity.getId(), rig, oldValue, newValue, setter, description));
    }
}
