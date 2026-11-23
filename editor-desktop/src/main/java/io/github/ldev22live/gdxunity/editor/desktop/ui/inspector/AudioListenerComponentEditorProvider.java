package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioListenerComponent;

/**
 * Details editor for an {@link AudioListenerComponent}. It has no settings, so the editor says what the component does and how to
 * use it, rather than showing "No editable properties", which reads as a missing editor (found by the editability audit, roadmap R1).
 */
public class AudioListenerComponentEditorProvider implements ComponentEditorProvider<AudioListenerComponent, Table> {
    /** What the component is for, shown in Details. */
    static final String DESCRIPTION = "The player's ears: spatial Audio Sources and Engine Sounds are heard from this entity's position "
        + "and facing. Put it on the camera or the player. Only the first enabled one in the scene is used. It has no settings.";

    @Override
    public Class<AudioListenerComponent> getComponentClass() {
        return AudioListenerComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Audio Listener";
    }

    @Override
    public void buildEditor(Table container, Entity entity, AudioListenerComponent listener, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;
        container.left();
        Label description = new Label(DESCRIPTION, skin, "muted");
        description.setWrap(true);
        container.add(description).growX().left().padLeft(2).row();
    }
}
