package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponent;

/**
 * Details editor for a {@link MusicSourceComponent} (TD-061): the {@code .song} file (a project-relative path such as
 * {@code music/demo.song}), volume, loop and play on start. Every change is an undoable command.
 */
public class MusicSourceComponentEditorProvider implements ComponentEditorProvider<MusicSourceComponent, Table> {
    @Override
    public Class<MusicSourceComponent> getComponentClass() {
        return MusicSourceComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Music Source";
    }

    @Override
    public void buildEditor(Table container, Entity entity, MusicSourceComponent source, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        InspectorFieldRows.textRow(container, skin, "Song", source.getSong(), song ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<MusicSourceComponent, String>(
                editorState, entity.getId(), source, source.getSong(), song.trim(), source::setSong, "Change Music Song")));
        InspectorFieldRows.floatRow(container, skin, "Volume (0-1)", source.getVolume(), volume -> {
            if (!(volume >= 0f && volume <= 1f)) {
                throw new IllegalArgumentException("Volume must be within 0 to 1: " + volume);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<MusicSourceComponent, Float>(
                editorState, entity.getId(), source, source.getVolume(), volume, source::setVolume, "Change Music Volume"));
        });
        InspectorFieldRows.toggleRow(container, skin, "Loop", source.isLoop(), loop ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<MusicSourceComponent, Boolean>(
                editorState, entity.getId(), source, source.isLoop(), loop, source::setLoop, "Change Music Loop")));
        InspectorFieldRows.toggleRow(container, skin, "Play On Start", source.isPlayOnStart(), playOnStart ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<MusicSourceComponent, Boolean>(
                editorState, entity.getId(), source, source.isPlayOnStart(), playOnStart, source::setPlayOnStart,
                "Change Music Play On Start")));
    }
}
