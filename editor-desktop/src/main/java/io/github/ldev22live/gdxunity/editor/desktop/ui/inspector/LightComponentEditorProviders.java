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
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;

/**
 * Details editors for the light components. Every change is an undoable command.
 */
public final class LightComponentEditorProviders {

    private LightComponentEditorProviders() {}

    /** Direction, color, intensity and shadow casting of a {@link DirectionalLightComponent}. */
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
            InspectorFieldRows.toggleRow(container, skin, "Cast Shadows", light.isCastShadows(), castShadows ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<DirectionalLightComponent, Boolean>(
                    editorState, entity.getId(), light, light.isCastShadows(), castShadows, light::setCastShadows,
                    "Change Light Shadows")));
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

    /** Sky and ground colors and intensity of a {@link SkyLightComponent}. */
    public static class Sky implements ComponentEditorProvider<SkyLightComponent, Table> {
        @Override
        public Class<SkyLightComponent> getComponentClass() {
            return SkyLightComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Sky Light";
        }

        @Override
        public void buildEditor(Table container, Entity entity, SkyLightComponent sky, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.colorRow(container, skin, "Sky (RGB)", sky.getSkyColor(), color ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<SkyLightComponent, Color>(
                    editorState, entity.getId(), sky, sky.getSkyColor(), color, sky::setSkyColor, "Change Sky Color")));
            InspectorFieldRows.colorRow(container, skin, "Ground (RGB)", sky.getGroundColor(), color ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<SkyLightComponent, Color>(
                    editorState, entity.getId(), sky, sky.getGroundColor(), color, sky::setGroundColor, "Change Ground Color")));
            InspectorFieldRows.floatRow(container, skin, "Intensity", sky.getIntensity(), intensity -> {
                if (!(intensity >= 0f) || Float.isInfinite(intensity)) {
                    throw new IllegalArgumentException("Sky intensity must be >= 0: " + intensity);
                }
                editorState.executeCommand(new ModifyComponentPropertyCommand<SkyLightComponent, Float>(
                    editorState, entity.getId(), sky, sky.getIntensity(), intensity, sky::setIntensity, "Change Sky Intensity"));
            });
        }
    }

    /** Exposure and operator of a {@link ToneMappingComponent}. */
    public static class ToneMapping implements ComponentEditorProvider<ToneMappingComponent, Table> {
        @Override
        public Class<ToneMappingComponent> getComponentClass() {
            return ToneMappingComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Tone Mapping";
        }

        @Override
        public void buildEditor(Table container, Entity entity, ToneMappingComponent toneMapping, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.floatRow(container, skin, "Exposure", toneMapping.getExposure(), exposure -> {
                if (!(exposure > 0f) || Float.isInfinite(exposure)) {
                    throw new IllegalArgumentException("Exposure must be > 0: " + exposure);
                }
                editorState.executeCommand(new ModifyComponentPropertyCommand<ToneMappingComponent, Float>(
                    editorState, entity.getId(), toneMapping, toneMapping.getExposure(), exposure, toneMapping::setExposure,
                    "Change Exposure"));
            });
            InspectorFieldRows.enumRow(container, skin, "Operator", toneMapping.getOperator(),
                ToneMappingComponent.Operator.values(), operator ->
                    editorState.executeCommand(new ModifyComponentPropertyCommand<ToneMappingComponent, ToneMappingComponent.Operator>(
                        editorState, entity.getId(), toneMapping, toneMapping.getOperator(), operator, toneMapping::setOperator,
                        "Change Tone Mapping")));
        }
    }
}
