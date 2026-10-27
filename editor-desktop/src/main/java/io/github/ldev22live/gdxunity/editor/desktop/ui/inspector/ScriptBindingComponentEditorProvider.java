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
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;

public class ScriptBindingComponentEditorProvider implements ComponentEditorProvider<EntityScriptBindingComponent, Table> {

    @Override
    public Class<EntityScriptBindingComponent> getComponentClass() {
        return EntityScriptBindingComponent.class;
    }

    @Override
    public String getDisplayName() {
        return "Script Component";
    }

    @Override
    public void buildEditor(Table container, Entity entity, EntityScriptBindingComponent scriptComp, EditorState editorState, Object skinObj) {
        Skin skin = (Skin) skinObj;

        Label classLabel = new Label("Class: " + (scriptComp.getScriptClassName() != null ? scriptComp.getScriptClassName() : "[None]"), skin);
        container.add(classLabel).left().pad(2).row();

        Label langLabel = new Label("Language: " + (scriptComp.getLanguage() != null ? scriptComp.getLanguage().getDisplayName() : "Auto"), skin, "muted");
        container.add(langLabel).left().pad(2).row();

        TextButton scriptToggle = new TextButton(scriptComp.isEnabled() ? "Enabled" : "Disabled", skin);
        Color enabledColor = skin.has("success", Color.class) ? skin.getColor("success") : new Color(0.3f, 0.75f, 0.4f, 1f);
        Color disabledColor = skin.has("text-primary", Color.class) ? skin.getColor("text-primary") : Color.WHITE;
        if (scriptComp.isEnabled()) scriptToggle.setColor(enabledColor);
        scriptToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                scriptComp.setEnabled(!scriptComp.isEnabled());
                scriptToggle.setText(scriptComp.isEnabled() ? "Enabled" : "Disabled");
                scriptToggle.setColor(scriptComp.isEnabled() ? enabledColor : disabledColor);
            }
        });
        container.add(scriptToggle).height(24).pad(2).left().row();
    }
}
