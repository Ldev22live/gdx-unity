package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Details editor for {@link MeshPrimitiveComponent}: shape, size and color, each change undoable.
 */
public class MeshPrimitiveComponentEditorProvider implements ComponentEditorProvider<MeshPrimitiveComponent, Table> {

    @Override
    public Class<MeshPrimitiveComponent> getComponentClass() {
        return MeshPrimitiveComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Mesh Primitive";
    }

    @Override
    public void buildEditor(Table container, Entity entity, MeshPrimitiveComponent primitive, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();

        Table shapeRow = new Table(skin);
        shapeRow.left();
        shapeRow.add(new Label("Shape", skin, "muted")).width(80).left();
        SelectBox<MeshPrimitiveComponent.Shape> shapeSelect = new SelectBox<>(skin);
        shapeSelect.setItems(MeshPrimitiveComponent.Shape.values());
        shapeSelect.setSelected(primitive.getShape());
        shapeSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                MeshPrimitiveComponent.Shape selected = shapeSelect.getSelected();
                if (selected != primitive.getShape()) {
                    editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), primitive,
                        primitive.getShape(), selected, primitive::setShape, "Change Mesh Shape"));
                }
            }
        });
        shapeRow.add(shapeSelect).growX().height(22);
        container.add(shapeRow).growX().padTop(2).padBottom(2).row();

        InspectorFieldRows.vector3Row(container, skin, "Size", primitive.getSize(), size -> {
            if (size.x <= 0f || size.y <= 0f || size.z <= 0f) {
                editorState.setStatusMessage("Mesh size must be positive", true);
                return;
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<MeshPrimitiveComponent, Vector3>(
                editorState, entity.getId(), primitive, primitive.getSize(), size, primitive::setSize, "Resize Mesh"));
        });
        InspectorFieldRows.colorRow(container, skin, "Color (RGB)", primitive.getColor(), color ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<MeshPrimitiveComponent, Color>(
                editorState, entity.getId(), primitive, primitive.getColor(), color, primitive::setColor, "Change Mesh Color")));
    }
}
