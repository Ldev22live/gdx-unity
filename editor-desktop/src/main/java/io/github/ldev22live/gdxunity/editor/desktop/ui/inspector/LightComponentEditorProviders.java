package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;

/**
 * Details editors for the light components. Every change is an undoable command.
 */
public final class LightComponentEditorProviders {

    private LightComponentEditorProviders() {}

    /** Direction, color and intensity of a {@link DirectionalLightComponent}. */
    public static class Directional implements ComponentEditorProvider<DirectionalLightComponent, Table> {
        @Override
        public Class<DirectionalLightComponent> getComponentClass() {
            return DirectionalLightComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Directional Light";
        }

        @Override
        public void buildEditor(Table container, Entity entity, DirectionalLightComponent light, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.vector3Row(container, skin, "Direction", light.getDirection(), direction -> {
                if (!direction.isZero()) {
                    editorState.executeCommand(new ModifyComponentPropertyCommand<DirectionalLightComponent, Vector3>(
                        editorState, entity.getId(), light, light.getDirection(), direction, light::setDirection, "Change Light Direction"));
                }
            });
            InspectorFieldRows.colorRow(container, skin, "Color (RGB)", light.getColor(), color ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<DirectionalLightComponent, Color>(
                    editorState, entity.getId(), light, light.getColor(), color, light::setColor, "Change Light Color")));
            InspectorFieldRows.floatRow(container, skin, "Intensity", light.getIntensity(), intensity ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<DirectionalLightComponent, Float>(
                    editorState, entity.getId(), light, light.getIntensity(), intensity, light::setIntensity, "Change Light Intensity")));
        }
    }

    /** Color, intensity and range of a {@link PointLightComponent}; the light sits at the entity position. */
    public static class Point implements ComponentEditorProvider<PointLightComponent, Table> {
        @Override
        public Class<PointLightComponent> getComponentClass() {
            return PointLightComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Point Light";
        }

        @Override
        public void buildEditor(Table container, Entity entity, PointLightComponent light, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.colorRow(container, skin, "Color (RGB)", light.getColor(), color ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<PointLightComponent, Color>(
                    editorState, entity.getId(), light, light.getColor(), color, light::setColor, "Change Light Color")));
            InspectorFieldRows.floatRow(container, skin, "Intensity", light.getIntensity(), intensity ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<PointLightComponent, Float>(
                    editorState, entity.getId(), light, light.getIntensity(), intensity, light::setIntensity, "Change Light Intensity")));
            InspectorFieldRows.floatRow(container, skin, "Range", light.getRange(), range ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<PointLightComponent, Float>(
                    editorState, entity.getId(), light, light.getRange(), range, light::setRange, "Change Light Range")));
        }
    }
}
