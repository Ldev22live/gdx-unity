package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;

/** Details for a {@link PlayerComponent} (TD-088): what it is for, and the Use Profile Movement switch as an undoable change. */
public class PlayerComponentEditorProvider implements ComponentEditorProvider<PlayerComponent, Table> {
    @Override
    public Class<PlayerComponent> getComponentClass() {
        return PlayerComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Player";
    }

    @Override
    public void buildEditor(Table container, Entity entity, PlayerComponent player, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        container.add(new Label("The entity the Game Genre controls in Play, from here.", skin)).colspan(2).left().row();
        container.add(new Label("Its own script or graph wins unless Use profile is on.", skin)).colspan(2).left().padBottom(4f).row();
        InspectorFieldRows.toggleRow(container, skin, "Use profile", player.isUseProfileMovement(),
            value -> editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), player,
                player.isUseProfileMovement(), value, player::setUseProfileMovement, "Use Profile Movement")));
    }
}
