package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;

/** Details for a {@link SpawnPointComponent} (roadmap M3c.2): it has no settings, so Details says what it does. */
public class SpawnPointComponentEditorProvider implements ComponentEditorProvider<SpawnPointComponent, Table> {
    @Override
    public Class<SpawnPointComponent> getComponentClass() {
        return SpawnPointComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Spawn Point";
    }

    @Override
    public void buildEditor(Table container, Entity entity, SpawnPointComponent spawn, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        container.add(new Label("In Play the player starts here, standing,", skin)).left().row();
        container.add(new Label("facing the way this faces. Move and turn it.", skin)).left().row();
    }
}
