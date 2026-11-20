package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainPathComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;

/**
 * Details for a {@link TerrainPathComponent} (roadmap M3.7): kind, width, depth, paint, the prefab beside it and its spacing, each an
 * undoable change. Points are added with the level editor's Path tool, which also applies the path.
 */
public class TerrainPathComponentEditorProvider implements ComponentEditorProvider<TerrainPathComponent, Table> {
    @Override
    public Class<TerrainPathComponent> getComponentClass() {
        return TerrainPathComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Terrain Path";
    }

    @Override
    public void buildEditor(Table container, Entity entity, TerrainPathComponent path, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        container.add(new Label(path.getPointCount() + " points. Add points and Apply with Level > Terrain > Path.", skin))
            .colspan(2).left().padBottom(4f).row();
        InspectorFieldRows.enumRow(container, skin, "Kind", path.getKind(), TerrainPath.Kind.values(),
            value -> editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), path, path.getKind(),
                value, path::setKind, "Path Kind")));
        InspectorFieldRows.floatRow(container, skin, "Width", path.getWidth(), value -> {
            if (value > 0f) {
                editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), path, path.getWidth(), value,
                    path::setWidth, "Path Width"));
            }
        });
        InspectorFieldRows.floatRow(container, skin, "Depth", path.getDepth(), value -> {
            if (value >= 0f) {
                editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), path, path.getDepth(), value,
                    path::setDepth, "Path Depth"));
            }
        });
        InspectorFieldRows.enumRow(container, skin, "Paint", path.getPaint(), Surface.values(),
            value -> editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), path, path.getPaint(),
                value, path::setPaint, "Path Paint")));
        InspectorFieldRows.textRow(container, skin, "Beside", path.getPrefabPath(),
            value -> editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), path,
                path.getPrefabPath(), value, path::setPrefabPath, "Path Prefab")));
        InspectorFieldRows.floatRow(container, skin, "Spacing", path.getSpacing(), value -> {
            if (value > 0f) {
                editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), path, path.getSpacing(), value,
                    path::setSpacing, "Path Spacing"));
            }
        });
    }
}
