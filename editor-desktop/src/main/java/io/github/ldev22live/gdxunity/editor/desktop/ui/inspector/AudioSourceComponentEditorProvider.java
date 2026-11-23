package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.audio.SfxPlayer;
import io.github.ldev22live.gdxunity.engine.audio.SfxrParams;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;

/**
 * Details editor for an {@link AudioSourceComponent} (TD-043): the cue scripts trigger it by, the generated sound it
 * plays (a preset or a .wav clip), volume, pitch, whether it is heard from the Audio Listener (Spatial, with the distances it fades
 * between) and play on start. Every change is an undoable command; a value the component rejects (an
 * unknown sound, a volume above 1) puts the old text back.
 */
public class AudioSourceComponentEditorProvider implements ComponentEditorProvider<AudioSourceComponent, Table> {
    @Override
    public Class<AudioSourceComponent> getComponentClass() {
        return AudioSourceComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Audio Source";
    }

    @Override
    public void buildEditor(Table container, Entity entity, AudioSourceComponent source, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        InspectorFieldRows.textRow(container, skin, "Cue", source.getCue(), cue -> {
            String trimmed = cue.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("The cue cannot be blank");
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<AudioSourceComponent, String>(
                editorState, entity.getId(), source, source.getCue(), trimmed, source::setCue, "Change Audio Cue"));
        });
        InspectorFieldRows.textRow(container, skin, "Sound", source.getSound(), sound -> {
            String trimmed = sound.trim();
            if (!SfxPlayer.isKnown(trimmed) && !AudioSourceComponent.isClip(trimmed)) {
                throw new IllegalArgumentException("Unknown sound '" + trimmed + "'; use a " + String.join(" or ", AudioSourceComponent.CLIP_EXTENSIONS)
                    + " file of the project or one of " + SfxrParams.presetNames());
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<AudioSourceComponent, String>(
                editorState, entity.getId(), source, source.getSound(), trimmed, source::setSound, "Change Audio Sound"));
        });
        container.add(new Label("Sounds: " + String.join(", ", SfxrParams.presetNames()) + ", or a .wav or .ogg file of the project (drag one"
            + " into the scene to make a source)", skin, "muted")).left().padLeft(2).row();
        InspectorFieldRows.floatRow(container, skin, "Volume (0-1)", source.getVolume(), volume -> {
            if (!(volume >= 0f && volume <= 1f)) {
                throw new IllegalArgumentException("Volume must be within 0 to 1: " + volume);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<AudioSourceComponent, Float>(
                editorState, entity.getId(), source, source.getVolume(), volume, source::setVolume, "Change Audio Volume"));
        });
        InspectorFieldRows.floatRow(container, skin, "Pitch", source.getPitch(), pitch -> {
            if (!(pitch >= AudioSourceComponent.MIN_PITCH && pitch <= AudioSourceComponent.MAX_PITCH)) {
                throw new IllegalArgumentException("Pitch must be within " + AudioSourceComponent.MIN_PITCH + " to "
                    + AudioSourceComponent.MAX_PITCH + ": " + pitch);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<AudioSourceComponent, Float>(
                editorState, entity.getId(), source, source.getPitch(), pitch, source::setPitch, "Change Audio Pitch"));
        });
        InspectorFieldRows.toggleRow(container, skin, "Spatial", source.isSpatial(), spatial ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<AudioSourceComponent, Boolean>(
                editorState, entity.getId(), source, source.isSpatial(), spatial, source::setSpatial, "Change Audio Spatial")));
        InspectorFieldRows.floatRow(container, skin, "Full volume up to", source.getMinDistance(), min -> {
            float max = source.getMaxDistance();
            if (!(min >= 0f && max > min)) {
                throw new IllegalArgumentException("The full volume distance must be 0 or more and below " + max + ": " + min);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<AudioSourceComponent, Float>(
                editorState, entity.getId(), source, source.getMinDistance(), min, value -> source.setDistances(value, max),
                "Change Audio Full Volume Distance"));
        });
        InspectorFieldRows.floatRow(container, skin, "Silent beyond", source.getMaxDistance(), max -> {
            float min = source.getMinDistance();
            if (!(max > min)) {
                throw new IllegalArgumentException("The silent distance must be above " + min + ": " + max);
            }
            editorState.executeCommand(new ModifyComponentPropertyCommand<AudioSourceComponent, Float>(
                editorState, entity.getId(), source, source.getMaxDistance(), max, value -> source.setDistances(min, value),
                "Change Audio Silent Distance"));
        });
        InspectorFieldRows.toggleRow(container, skin, "Play On Start", source.isPlayOnStart(), playOnStart ->
            editorState.executeCommand(new ModifyComponentPropertyCommand<AudioSourceComponent, Boolean>(
                editorState, entity.getId(), source, source.isPlayOnStart(), playOnStart, source::setPlayOnStart,
                "Change Audio Play On Start")));
    }
}
