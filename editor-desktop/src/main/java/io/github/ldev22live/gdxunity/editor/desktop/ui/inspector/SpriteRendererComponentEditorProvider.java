package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

public class SpriteRendererComponentEditorProvider implements ComponentEditorProvider<SpriteRendererComponent, Table> {

    @Override
    public Class<SpriteRendererComponent> getComponentClass() {
        return SpriteRendererComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Sprite Renderer";
    }

    @Override
    public void buildEditor(Table container, Entity entity, SpriteRendererComponent spriteComp, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;

        String pathStr = (spriteComp.getAssetReference() != null)
            ? spriteComp.getAssetReference().getPath()
            : (spriteComp.getTexture() != null ? "[Internal Texture]" : "[None]");
        Label pathLabel = new Label("Asset: " + pathStr, skin);
        container.add(pathLabel).left().pad(2).row();

        Label sizeLabel = new Label(String.format("Size: %.0f x %.0f", spriteComp.getWidth(), spriteComp.getHeight()), skin, "muted");
        container.add(sizeLabel).left().pad(2).row();

        TextButton assignBtn = new TextButton("Assign Selected Asset", skin);
        assignBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AssetReference selectedAsset = editorState.getSelectedAsset();
                if (selectedAsset == null) {
                    editorState.setStatusMessage("No asset selected in Asset Browser", true);
                    return;
                }
                editorState.executeCommand(new io.github.ldev22live.gdxunity.editor.core.command.AssignSpriteAssetCommand(
                    editorState, entity.getId(), spriteComp.getAssetReference(), selectedAsset
                ));
            }
        });
        container.add(assignBtn).height(24).pad(4).left().row();
    }
}
