package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.audio.EngineProfile;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.EngineSoundComponent;

/**
 * Details editor for an {@link EngineSoundComponent} (TD-080): the engine it sounds like, volume, and whether it is heard from
 * the Audio Listener (Spatial, with the distances it fades between). Every change is an undoable command; a value the
 * component rejects (an unknown engine, a volume above 1) puts the old text back.
 */
public class EngineSoundComponentEditorProvider implements ComponentEditorProvider<EngineSoundComponent, Table> {
    @Override
    public Class<EngineSoundComponent> getComponentClass() {
        return EngineSoundComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Engine Sound";
    }

    @Override
    public void buildEditor(Table container, Entity entity, EngineSoundComponent engine, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        InspectorFieldRows.textRow(container, skin, "Engine", engine.getProfile(), profile -> {
            String trimmed = profile.trim();
            if (!EngineProfile.isKnown(trimmed)) {
                throw new IllegalArgumentException("Unknown engine '" + trimmed + "'; use one of " + EngineProfile.names());
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<EngineSoundComponent, String>(
                editorState, entity.getId(), engine, engine.getProfile(), EngineProfile.byName(trimmed).name(),
                engine::setProfile, "Change Engine"));
        });
        container.add(new Label("Engines: " + String.join(", ", EngineProfile.names())
            + ". A script sets its revs (0 idle to 1 redline) and throttle every frame with setState.", skin, "muted")).left().padLeft(2).row();
        InspectorFieldRows.floatRow(container, skin, "Volume (0-1)", engine.getVolume(), volume -> {
            if (!(volume >= 0f && volume <= 1f)) {
                throw new IllegalArgumentException("Volume must be within 0 to 1: " + volume);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<EngineSoundComponent, Float>(
                editorState, entity.getId(), engine, engine.getVolume(), volume, engine::setVolume, "Change Engine Volume"));
        });
        InspectorFieldRows.toggleRow(container, skin, "Spatial", engine.isSpatial(), spatial ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<EngineSoundComponent, Boolean>(
                editorState, entity.getId(), engine, engine.isSpatial(), spatial, engine::setSpatial, "Change Engine Spatial")));
        InspectorFieldRows.floatRow(container, skin, "Full volume up to", engine.getMinDistance(), min -> {
            float max = engine.getMaxDistance();
            if (!(min >= 0f && max > min)) {
                throw new IllegalArgumentException("The full volume distance must be 0 or more and below " + max + ": " + min);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<EngineSoundComponent, Float>(
                editorState, entity.getId(), engine, engine.getMinDistance(), min, value -> engine.setDistances(value, max),
                "Change Engine Full Volume Distance"));
        });
        InspectorFieldRows.floatRow(container, skin, "Silent beyond", engine.getMaxDistance(), max -> {
            float min = engine.getMinDistance();
            if (!(max > min)) {
                throw new IllegalArgumentException("The silent distance must be above " + min + ": " + max);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<EngineSoundComponent, Float>(
                editorState, entity.getId(), engine, engine.getMaxDistance(), max, value -> engine.setDistances(min, value),
                "Change Engine Silent Distance"));
        });
    }
}
