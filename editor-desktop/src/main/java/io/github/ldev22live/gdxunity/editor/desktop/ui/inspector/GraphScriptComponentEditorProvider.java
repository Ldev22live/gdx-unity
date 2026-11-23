package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Details editor for {@link GraphScriptComponent}: shows the graph asset, opens it in the Graph tab, and can switch
 * to the graph selected in the Content Browser (undoable).
 */
public class GraphScriptComponentEditorProvider implements ComponentEditorProvider<GraphScriptComponent, Table> {
    private final Consumer<AssetReference> openGraph;

    /** @param openGraph opens a graph asset in the graph editor */
    public GraphScriptComponentEditorProvider(Consumer<AssetReference> openGraph) {
        this.openGraph = Objects.requireNonNull(openGraph, "openGraph cannot be null");
    }

    @Override
    public Class<GraphScriptComponent> getComponentClass() {
        return GraphScriptComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Graph Script";
    }

    @Override
    public void buildEditor(Table container, Entity entity, GraphScriptComponent component, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        Table row = new Table(skin);
        row.left();
        row.add(new Label("Graph", skin, "muted")).width(80).left();
        row.add(new Label(component.getGraphPath(), skin)).left().expandX();
        container.add(row).growX().padTop(2).padBottom(2).row();

        Table buttons = new Table(skin);
        TextButton open = new TextButton("Open Graph", skin, "primary");
        open.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openGraph.accept(new AssetReference(component.getGraphPath()));
            }
        });
        buttons.add(open).height(24).padRight(6);
        TextButton useSelected = new TextButton("Use Selected Graph", skin, "flat");
        useSelected.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetReference selected = editorState.getSelectedAsset();
                if (selected == null || AssetType.fromPath(selected.getPath()) != AssetType.GRAPH) {
                    editorState.setStatusMessage("Select a .graph in the Content Browser first", true);
                    return;
                }
                editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), component,
                    component.getGraphPath(), selected.getPath(), component::setGraphPath, "Change Graph"));
            }
        });
        buttons.add(useSelected).height(24);
        container.add(buttons).left().padTop(4).row();
    }
}
