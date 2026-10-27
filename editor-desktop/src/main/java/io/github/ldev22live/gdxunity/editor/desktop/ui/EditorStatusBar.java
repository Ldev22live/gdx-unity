package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;

import java.util.Objects;

/**
 * Status bar docked at the bottom of the editor displaying operational messages and validation alerts.
 */
public class EditorStatusBar extends Table {
    private final Label statusLabel;
    private final Label modeLabel;

    public EditorStatusBar(EditorState editorState, Skin skin) {
        super(skin);
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(skin, "skin cannot be null");

        setBackground("header-bg");
        left();

        this.statusLabel = new Label("  " + editorState.getStatusMessage(), skin);
        add(this.statusLabel).pad(2).left().growX();

        this.modeLabel = new Label("Ready  ", skin, "muted");
        add(this.modeLabel).pad(2).right();

        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onStatusMessage(String message, boolean isError) {
                statusLabel.setText("  " + message);
                Color errColor = skin.has("error", Color.class) ? skin.getColor("error") : new Color(0.95f, 0.4f, 0.4f, 1f);
                Color normalColor = skin.has("text-primary", Color.class) ? skin.getColor("text-primary") : new Color(0.88f, 0.88f, 0.90f, 1f);
                statusLabel.setColor(isError ? errColor : normalColor);
                modeLabel.setText(isError ? "Error  " : "Ready  ");
            }
        });
    }
}
