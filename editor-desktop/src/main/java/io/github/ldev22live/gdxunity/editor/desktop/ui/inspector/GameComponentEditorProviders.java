package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Details editors for the game components (TD-034). Every change is an undoable command; invalid values are
 * rejected by the component and the field shows the old value again.
 */
public final class GameComponentEditorProviders {

    private GameComponentEditorProviders() {}

    /** Projection, orthographic height and field of view of a {@link CameraComponent}. */
    public static class Camera implements ComponentEditorProvider<CameraComponent, Table> {
        @Override
        public Class<CameraComponent> getComponentClass() {
            return CameraComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Camera";
        }

        @Override
        public void buildEditor(Table container, Entity entity, CameraComponent camera, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.enumRow(container, skin, "Projection", camera.getProjection(),
                CameraComponent.Projection.values(), projection ->
                    editorState.executeCommand(new ModifyComponentPropertyCommand<CameraComponent, CameraComponent.Projection>(
                        editorState, entity.getId(), camera, camera.getProjection(), projection, camera::setProjection,
                        "Change Camera Projection")));
            InspectorFieldRows.floatRow(container, skin, "View Height", camera.getOrthographicHeight(), height -> {
                new CameraComponent().setOrthographicHeight(height);
                editorState.executeCommand(new ModifyComponentPropertyCommand<CameraComponent, Float>(
                    editorState, entity.getId(), camera, camera.getOrthographicHeight(), height,
                    camera::setOrthographicHeight, "Change Camera View Height"));
            });
            InspectorFieldRows.floatRow(container, skin, "Field of View", camera.getFieldOfView(), fieldOfView -> {
                new CameraComponent().setFieldOfView(fieldOfView);
                editorState.executeCommand(new ModifyComponentPropertyCommand<CameraComponent, Float>(
                    editorState, entity.getId(), camera, camera.getFieldOfView(), fieldOfView, camera::setFieldOfView,
                    "Change Camera Field of View"));
            });
        }
    }

    /** Text, color, size, anchor and offset of a {@link TextComponent}. */
    public static class Text implements ComponentEditorProvider<TextComponent, Table> {
        @Override
        public Class<TextComponent> getComponentClass() {
            return TextComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Text";
        }

        @Override
        public void buildEditor(Table container, Entity entity, TextComponent text, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            InspectorFieldRows.textRow(container, skin, "Text", text.getText(), value ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<TextComponent, String>(
                    editorState, entity.getId(), text, text.getText(), value, text::setText, "Change Text")));
            InspectorFieldRows.colorRow(container, skin, "Color", text.getColor(), color ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<TextComponent, Color>(
                    editorState, entity.getId(), text, new Color(text.getColor()), color, text::setColor,
                    "Change Text Color")));
            InspectorFieldRows.floatRow(container, skin, "Size", text.getScale(), scale -> {
                new TextComponent().setScale(scale);
                editorState.executeCommand(new ModifyComponentPropertyCommand<TextComponent, Float>(
                    editorState, entity.getId(), text, text.getScale(), scale, text::setScale, "Change Text Size"));
            });
            InspectorFieldRows.enumRow(container, skin, "Anchor", text.getAnchor(), TextComponent.Anchor.values(), anchor ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<TextComponent, TextComponent.Anchor>(
                    editorState, entity.getId(), text, text.getAnchor(), anchor, text::setAnchor, "Change Text Anchor")));
            InspectorFieldRows.floatRow(container, skin, "Offset X", text.getOffsetX(), x ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<TextComponent, Float>(
                    editorState, entity.getId(), text, text.getOffsetX(), x, value -> text.setOffset(value, text.getOffsetY()),
                    "Change Text Offset")));
            InspectorFieldRows.floatRow(container, skin, "Offset Y", text.getOffsetY(), y ->
                editorState.executeCommand(new ModifyComponentPropertyCommand<TextComponent, Float>(
                    editorState, entity.getId(), text, text.getOffsetY(), y, value -> text.setOffset(text.getOffsetX(), value),
                    "Change Text Offset")));
        }
    }
}
