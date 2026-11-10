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
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Details editor for a {@link TilemapComponent} (TD-066, milestone E): the tile set (a .sprite assigned from the Content Browser), the cell size, whether solid
 * tiles collide and the tint. The cells themselves are painted in the Tiles tab. Every change is an undoable command.
 */
public class TilemapComponentEditorProvider implements ComponentEditorProvider<TilemapComponent, Table> {
    @Override
    public Class<TilemapComponent> getComponentClass() {
        return TilemapComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Tilemap";
    }

    @Override
    public void buildEditor(Table container, Entity entity, TilemapComponent tilemap, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        AssetReference asset = tilemap.getTileSetReference();
        container.add(new Label("Tile set: " + (asset != null ? asset.getPath() : "[None]"), skin)).left().pad(2).row();
        String info = tilemap.getMap().getCols() + " x " + tilemap.getMap().getRows() + " cells, " + tilemap.getMap().getLayerCount() + " layer(s)"
            + (tilemap.getTileSet() != null ? ", " + tilemap.getTileSet().getFrameCount() + " tile(s)" : "");
        container.add(new Label(info, skin, "muted")).left().pad(2).row();
        if (tilemap.getLoadError() != null) {
            container.add(new Label(tilemap.getLoadError(), skin, "muted")).left().pad(2).row();
        }
        TextButton assign = new TextButton("Assign Selected Sprite as Tile Set", skin);
        assign.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetReference selected = editorState.getSelectedAsset();
                if (selected == null || AssetType.fromPath(selected.getPath()) != AssetType.SPRITE) {
                    editorState.setStatusMessage("Select a .sprite file in the Content Browser first", true);
                    return;
                }
                editorState.executeCommand(new ModifyComponentPropertyCommand<TilemapComponent, AssetReference>(
                    editorState, entity.getId(), tilemap, tilemap.getTileSetReference(), selected, tilemap::setTileSetReference, "Assign Tile Set"));
            }
        });
        container.add(assign).height(24).pad(4).left().row();
        InspectorFieldRows.floatRow(container, skin, "Tile width", tilemap.getTileWidth(), width -> {
            if (!(width > 0f)) {
                throw new IllegalArgumentException("A tile width must be above 0: " + width);
            }
            float height = tilemap.getTileHeight();
            editorState.executeCommand(new ModifyComponentPropertyCommand<TilemapComponent, Float>(
                editorState, entity.getId(), tilemap, tilemap.getTileWidth(), width, value -> tilemap.setTileSize(value, height), "Change Tile Width"));
        });
        InspectorFieldRows.floatRow(container, skin, "Tile height", tilemap.getTileHeight(), height -> {
            if (!(height > 0f)) {
                throw new IllegalArgumentException("A tile height must be above 0: " + height);
            }
            float width = tilemap.getTileWidth();
            editorState.executeCommand(new ModifyComponentPropertyCommand<TilemapComponent, Float>(
                editorState, entity.getId(), tilemap, tilemap.getTileHeight(), height, value -> tilemap.setTileSize(width, value), "Change Tile Height"));
        });
        InspectorFieldRows.toggleRow(container, skin, "Solid tiles collide", tilemap.isCollides(), collides ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<TilemapComponent, Boolean>(
                editorState, entity.getId(), tilemap, tilemap.isCollides(), collides, tilemap::setCollides, "Change Tilemap Collision")));
        InspectorFieldRows.colorRow(container, skin, "Tint", tilemap.getTint(), tint ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<TilemapComponent, com.badlogic.gdx.graphics.Color>(
                editorState, entity.getId(), tilemap, new com.badlogic.gdx.graphics.Color(tilemap.getTint()), tint, tilemap::setTint, "Change Tilemap Tint")));
        container.add(new Label("Paint the cells in the Tiles tab. The entity's position is the map's bottom left corner.", skin, "muted")).left().pad(2).row();
    }
}
