package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptDiagnostic;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeState;

import java.util.Objects;

/**
 * Main editor toolbar in the Unreal-style layout (TD-020): script compilation on the left, play controls
 * centered, panel toggles on the right. File, history and transform tools live in the menu bar and the
 * viewport overlay.
 */
public class EditorToolbar extends Table {

    private static final float BUTTON_HEIGHT = 26f;

    private final EditorState editorState;
    private final PlayModeController playModeController;
    private final EditorLog editorLog;
    private final Skin skin;

    private final TextButton playBtn;
    private final TextButton pauseBtn;
    private final TextButton stopBtn;
    private final Label playStateBadge;

    /**
     * @param onToggleAssistant toggles the AI Assistant panel; may be null to hide the button
     */
    public EditorToolbar(EditorState editorState, PlayModeController playModeController, EditorLog editorLog,
                         Skin skin, Runnable onToggleAssistant) {
        super(skin);
        this.editorLog = Objects.requireNonNull(editorLog, "editorLog cannot be null");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.playModeController = Objects.requireNonNull(playModeController, "playModeController cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");

        setBackground("header-bg");

        // --- Left: script compilation ---
        TextButton compileBtn = new TextButton("Compile Scripts", skin);
        compileBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                compileScripts();
            }
        });

        Table left = new Table();
        left.left();
        left.add(compileBtn).height(BUTTON_HEIGHT).padLeft(6);

        // --- Center: play controls ---
        playBtn = new TextButton("> Play", skin);
        playBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (playModeController.isPaused()) {
                    playModeController.resume();
                } else if (playModeController.isStopped()) {
                    playModeController.play();
                }
            }
        });

        pauseBtn = new TextButton("|| Pause", skin);
        pauseBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (playModeController.isPlaying()) {
                    playModeController.pause();
                } else if (playModeController.isPaused()) {
                    playModeController.resume();
                }
            }
        });

        stopBtn = new TextButton("[] Stop", skin);
        stopBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                playModeController.stop();
            }
        });

        playStateBadge = new Label("Stopped", skin, "muted");

        Table center = new Table();
        center.add(playBtn).height(BUTTON_HEIGHT).pad(2);
        center.add(pauseBtn).height(BUTTON_HEIGHT).pad(2);
        center.add(stopBtn).height(BUTTON_HEIGHT).pad(2);
        center.add(playStateBadge).padLeft(6);

        // --- Right: panel toggles ---
        Table right = new Table();
        right.right();
        if (onToggleAssistant != null) {
            TextButton assistantBtn = new TextButton("AI Assistant", skin);
            assistantBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    onToggleAssistant.run();
                }
            });
            right.add(assistantBtn).height(BUTTON_HEIGHT).padRight(6);
        }

        // Equal-width side columns keep the play controls centered in the window
        add(left).expandX().fillX().uniformX();
        add(center);
        add(right).expandX().fillX().uniformX();

        playModeController.addListener((oldState, newState) -> updatePlayModeUi(newState));
        updatePlayModeUi(playModeController.getState());
    }

    private void compileScripts() {
        if (editorState.getScriptManager() == null) {
            editorState.setStatusMessage("Script compilation is not available", true);
            return;
        }
        boolean success = editorState.getScriptManager().compileAll();
        if (success) {
            int count = editorState.getScriptManager().getDiscoveredScriptClasses().size();
            editorState.setStatusMessage("Scripts compiled (" + count + " classes loaded)", false);
        } else {
            var diagnostics = editorState.getScriptManager().getLatestDiagnostics();
            // Every diagnostic goes to the Output Log; the status bar shows the first
            for (ScriptDiagnostic diagnostic : diagnostics) {
                editorLog.error("Scripts", diagnostic.toString());
            }
            String error = !diagnostics.isEmpty() ? diagnostics.get(0).message() : "Unknown error";
            editorState.setStatusMessage("Compilation failed: " + error + " (see Output Log)", true);
        }
    }

    private void updatePlayModeUi(PlayModeState state) {
        Color textPrimary = skin.getColor("text-primary");
        Color textMuted = skin.getColor("text-muted");
        Color successColor = skin.getColor("success");
        Color warningColor = skin.getColor("warning");
        Color errorColor = skin.getColor("error");

        switch (state) {
            case STOPPED -> {
                setButton(playBtn, "> Play", false, textPrimary);
                setButton(pauseBtn, "|| Pause", true, textMuted);
                stopBtn.setDisabled(true);
                stopBtn.setColor(textMuted);
                playStateBadge.setText("Stopped");
                playStateBadge.setColor(textMuted);
            }
            case PLAYING -> {
                setButton(playBtn, "> Playing", true, successColor);
                setButton(pauseBtn, "|| Pause", false, textPrimary);
                stopBtn.setDisabled(false);
                stopBtn.setColor(errorColor);
                playStateBadge.setText("Playing");
                playStateBadge.setColor(successColor);
            }
            case PAUSED -> {
                setButton(playBtn, "> Resume", false, textPrimary);
                setButton(pauseBtn, "|| Paused", false, warningColor);
                stopBtn.setDisabled(false);
                stopBtn.setColor(errorColor);
                playStateBadge.setText("Paused");
                playStateBadge.setColor(warningColor);
            }
        }
    }

    private static void setButton(TextButton button, String text, boolean disabled, Color color) {
        button.setText(text);
        button.setDisabled(disabled);
        button.setColor(color);
    }
}
