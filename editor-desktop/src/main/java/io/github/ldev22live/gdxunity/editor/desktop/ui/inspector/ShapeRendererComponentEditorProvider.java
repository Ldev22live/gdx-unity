package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Details editor for {@link ShapeRendererComponent}, the flat rectangles the 2D samples are drawn with: width, height, colour,
 * filled or outline, and whether the entity's position is its centre or its corner. Every change is an undoable command. It
 * did not exist before the editability audit (roadmap R1) found 2D samples whose shapes showed "No editable properties".
 */
public class ShapeRendererComponentEditorProvider implements ComponentEditorProvider<ShapeRendererComponent, Table> {
    @Override
    public Class<ShapeRendererComponent> getComponentClass() {
        return ShapeRendererComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Shape Renderer";
    }

    @Override
    public void buildEditor(Table container, Entity entity, ShapeRendererComponent shape, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        InspectorFieldRows.floatRow(container, skin, "Width", shape.getWidth(), width -> {
            if (!(width > 0f)) {
                throw new IllegalArgumentException("Width must be positive: " + width);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<ShapeRendererComponent, Float>(
                editorState, entity.getId(), shape, shape.getWidth(), width, shape::setWidth, "Change Shape Width"));
        });
        InspectorFieldRows.floatRow(container, skin, "Height", shape.getHeight(), height -> {
            if (!(height > 0f)) {
                throw new IllegalArgumentException("Height must be positive: " + height);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<ShapeRendererComponent, Float>(
                editorState, entity.getId(), shape, shape.getHeight(), height, shape::setHeight, "Change Shape Height"));
        });
        InspectorFieldRows.colorRow(container, skin, "Color (RGB)", shape.getColor(), color ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<ShapeRendererComponent, Color>(
                editorState, entity.getId(), shape, shape.getColor(), color, shape::setColor, "Change Shape Color")));
        InspectorFieldRows.enumRow(container, skin, "Style", shape.getShapeType(), ShapeRendererComponent.ShapeType.values(), type ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<ShapeRendererComponent, ShapeRendererComponent.ShapeType>(
                editorState, entity.getId(), shape, shape.getShapeType(), type, shape::setShapeType, "Change Shape Style")));
        InspectorFieldRows.toggleRow(container, skin, "Centered", shape.isCentered(), centered ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<ShapeRendererComponent, Boolean>(
                editorState, entity.getId(), shape, shape.isCentered(), centered, shape::setCentered, "Change Shape Centered")));
    }
}
