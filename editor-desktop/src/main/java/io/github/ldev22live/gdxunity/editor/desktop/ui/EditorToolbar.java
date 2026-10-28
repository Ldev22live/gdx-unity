package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeState;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;

import java.io.File;
import java.util.Objects;

/**
 * Clean, modern editor toolbar inspired by Unity.
 * Uses ASCII text icons and labels supported by all BitmapFonts.
 * Features logical grouping: Scene / File, History, Scripts, Play Mode, and Transform Tools.
 */
public class EditorToolbar extends Table {
    private final EditorState editorState;
    private final PlayModeController playModeController;
    private final Camera camera;
    private final Skin skin;

    // File buttons
    private final TextButton newBtn;
    private final TextButton saveBtn;
    private final TextButton loadBtn;

    // History buttons
    private final TextButton undoBtn;
    private final TextButton redoBtn;

    // Script compile
    private final TextButton compileBtn;

    // Simulation controls
    private final TextButton playBtn;
    private final TextButton pauseBtn;
    private final TextButton stopBtn;
    private final Label playStateBadge;

    // Transform tools
    private final TextButton selectToolBtn;
    private final TextButton moveToolBtn;
    private final TextButton rotateToolBtn;
    private final TextButton scaleToolBtn;

    // Assistant toggle
    private final TextButton assistantToggleBtn;

    public EditorToolbar(EditorState editorState, PlayModeController playModeController, Camera camera, Skin skin) {
        this(editorState, playModeController, camera, skin, null, null);
    }

    public EditorToolbar(EditorState editorState, PlayModeController playModeController, Camera camera, Skin skin, Runnable onToggleAssistant) {
        this(editorState, playModeController, camera, skin, onToggleAssistant, null);
    }

    public EditorToolbar(EditorState editorState, PlayModeController playModeController, Camera camera, Skin skin, Runnable onToggleAssistant, Runnable onToggleGrid) {
        this(editorState, playModeController, camera, skin, onToggleAssistant, onToggleGrid, null);
    }

    public EditorToolbar(EditorState editorState, PlayModeController playModeController, Camera camera, Skin skin, Runnable onToggleAssistant, Runnable onToggleGrid, Runnable onTogglePhysics) {
        super(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.playModeController = Objects.requireNonNull(playModeController, "playModeController cannot be null");
        this.camera = camera;
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");

        setBackground("header-bg");
        left();

        // --- GROUP 1: File Operations ---
        newBtn = createToolButton("New", "Create a new scene");
        newBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.newScene("Untitled Scene");
            }
        });

        saveBtn = createToolButton("Save", "Save scene backup");
        saveBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    File saveFile = new File("scene_backup.json");
                    editorState.saveSceneToFile(saveFile);
                } catch (Exception e) {
                    editorState.setStatusMessage("Save failed: " + e.getMessage(), true);
                }
            }
        });

        loadBtn = createToolButton("Load", "Load scene backup");
        loadBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                try {
                    File loadFile = new File("scene_backup.json");
                    editorState.loadSceneFromFile(loadFile);
                } catch (Exception e) {
                    editorState.setStatusMessage("Load failed: " + e.getMessage(), true);
                }
            }
        });

        add(newBtn).height(26).pad(2);
        add(saveBtn).height(26).pad(2);
        add(loadBtn).height(26).pad(2);

        addSeparator();

        // --- GROUP 2: Undo / Redo ---
        undoBtn = createToolButton("Undo", "Undo last action");
        undoBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.undo();
            }
        });

        redoBtn = createToolButton("Redo", "Redo undone action");
        redoBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.redo();
            }
        });

        Runnable updateUndoRedoState = () -> {
            boolean canUndo = editorState.getCommandHistory().canUndo();
            boolean canRedo = editorState.getCommandHistory().canRedo();
            undoBtn.setDisabled(!canUndo);
            redoBtn.setDisabled(!canRedo);
        };
        editorState.getCommandHistory().addListener(history -> updateUndoRedoState.run());
        updateUndoRedoState.run();

        add(undoBtn).height(26).pad(2);
        add(redoBtn).height(26).pad(2);

        addSeparator();

        // --- GROUP 3: Script Compilation ---
        compileBtn = createToolButton("Compile", "Recompile scripts");
        compileBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (editorState.getScriptManager() != null) {
                    boolean success = editorState.getScriptManager().compileAll();
                    if (success) {
                        int count = editorState.getScriptManager().getDiscoveredScriptClasses().size();
                        editorState.setStatusMessage("Scripts compiled (" + count + " classes loaded)", false);
                    } else {
                        var diag = editorState.getScriptManager().getLatestDiagnostics();
                        String err = !diag.isEmpty() ? diag.get(0).message() : "Unknown error";
                        editorState.setStatusMessage("Compilation failed: " + err, true);
                    }
                }
            }
        });
        add(compileBtn).height(26).pad(2);

        addSeparator();

        // --- GROUP 4: Simulation Controls (Play, Pause, Stop) ---
        playBtn = createToolButton("> Play", "Start simulation");
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

        pauseBtn = createToolButton("|| Pause", "Pause simulation");
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

        stopBtn = createToolButton("[] Stop", "Stop simulation");
        stopBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                playModeController.stop();
            }
        });

        playStateBadge = new Label("Stopped", skin, "muted");

        add(playBtn).height(26).pad(2);
        add(pauseBtn).height(26).pad(2);
        add(stopBtn).height(26).pad(2);
        add(playStateBadge).padLeft(4).padRight(4);

        addSeparator();

        // --- GROUP 5: Transform Tools ---
        selectToolBtn = createToolButton("Select", "Selection Tool");
        selectToolBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.setToolMode(ToolMode.SELECT);
                updateToolHighlight();
            }
        });

        moveToolBtn = createToolButton("Move", "Move Tool (W)");
        moveToolBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.setToolMode(ToolMode.MOVE);
                updateToolHighlight();
            }
        });

        rotateToolBtn = createToolButton("Rotate", "Rotate Tool (E)");
        rotateToolBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.setToolMode(ToolMode.ROTATE);
                updateToolHighlight();
            }
        });

        scaleToolBtn = createToolButton("Scale", "Scale Tool (R)");
        scaleToolBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                editorState.setToolMode(ToolMode.SCALE);
                updateToolHighlight();
            }
        });

        add(selectToolBtn).height(26).pad(2);
        add(moveToolBtn).height(26).pad(2);
        add(rotateToolBtn).height(26).pad(2);
        add(scaleToolBtn).height(26).pad(2);

        addSeparator();

        // --- GROUP 6: Camera Controls ---
        TextButton resetCamBtn = createToolButton("Reset View", "Reset camera view to origin");
        resetCamBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (camera != null) {
                    camera.setPosition(320f, 240f, 0f);
                    camera.setZoom(1.0f);
                    camera.update();
                    editorState.setStatusMessage("Camera view reset", false);
                }
            }
        });
        add(resetCamBtn).height(26).pad(2);

        if (onToggleGrid != null) {
            TextButton gridBtn = createToolButton("Grid", "Toggle Scene View background grid");
            gridBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    onToggleGrid.run();
                }
            });
            add(gridBtn).height(26).pad(2);
        }

        if (onTogglePhysics != null) {
            TextButton physBtn = createToolButton("Physics Debug", "Toggle Box2D physics wireframe overlay");
            physBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    onTogglePhysics.run();
                }
            });
            add(physBtn).height(26).pad(2);
        }

        // Fill remaining horizontal space
        Table spacer = new Table();
        add(spacer).expandX();

        // --- GROUP 7: AI Assistant Toggle ---
        if (onToggleAssistant != null) {
            assistantToggleBtn = createToolButton("AI Assistant", "Show/Hide AI Assistant panel");
            assistantToggleBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    onToggleAssistant.run();
                }
            });
            add(assistantToggleBtn).height(26).padRight(6);
        } else {
            assistantToggleBtn = null;
        }

        // Listen for play mode changes to update UI button states
        playModeController.addListener((oldState, newState) -> updatePlayModeUi(newState));
        updatePlayModeUi(playModeController.getState());
        updateToolHighlight();

        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onToolModeChanged(ToolMode oldMode, ToolMode newMode) {
                updateToolHighlight();
            }
        });
    }

    private TextButton createToolButton(String text, String tooltipText) {
        TextButton btn = new TextButton(text, skin);
        return btn;
    }

    private void addSeparator() {
        Label sep = new Label("|", skin, "muted");
        add(sep).padLeft(6).padRight(6);
    }

    private void updateToolHighlight() {
        ToolMode current = editorState.getToolMode();
        setButtonActive(selectToolBtn, current == ToolMode.SELECT);
        setButtonActive(moveToolBtn, current == ToolMode.MOVE);
        setButtonActive(rotateToolBtn, current == ToolMode.ROTATE);
        setButtonActive(scaleToolBtn, current == ToolMode.SCALE);
    }

    private void setButtonActive(TextButton btn, boolean active) {
        if (active) {
            Color activeColor = skin.has("accent", Color.class) ? skin.getColor("accent") : new Color(0.35f, 0.65f, 1.0f, 1.0f);
            btn.setColor(activeColor);
        } else {
            Color normalColor = skin.has("text-primary", Color.class) ? skin.getColor("text-primary") : Color.WHITE;
            btn.setColor(normalColor);
        }
    }

    private void updatePlayModeUi(PlayModeState state) {
        Color textPrimary = skin.has("text-primary", Color.class) ? skin.getColor("text-primary") : Color.WHITE;
        Color textMuted = skin.has("text-muted", Color.class) ? skin.getColor("text-muted") : Color.GRAY;
        Color successColor = skin.has("success", Color.class) ? skin.getColor("success") : new Color(0.3f, 0.8f, 0.4f, 1f);
        Color warningColor = skin.has("warning", Color.class) ? skin.getColor("warning") : new Color(1f, 0.75f, 0.2f, 1f);
        Color errorColor = skin.has("error", Color.class) ? skin.getColor("error") : new Color(0.9f, 0.35f, 0.35f, 1f);

        switch (state) {
            case STOPPED -> {
                playBtn.setText("> Play");
                playBtn.setDisabled(false);
                playBtn.setColor(textPrimary);

                pauseBtn.setText("|| Pause");
                pauseBtn.setDisabled(true);
                pauseBtn.setColor(textMuted);

                stopBtn.setDisabled(true);
                stopBtn.setColor(textMuted);

                newBtn.setDisabled(false);
                saveBtn.setDisabled(false);
                loadBtn.setDisabled(false);

                playStateBadge.setText("Stopped");
                playStateBadge.setColor(textMuted);
            }
            case PLAYING -> {
                playBtn.setText("> Playing");
                playBtn.setDisabled(true);
                playBtn.setColor(successColor);

                pauseBtn.setText("|| Pause");
                pauseBtn.setDisabled(false);
                pauseBtn.setColor(textPrimary);

                stopBtn.setDisabled(false);
                stopBtn.setColor(errorColor);

                newBtn.setDisabled(true);
                saveBtn.setDisabled(true);
                loadBtn.setDisabled(true);

                playStateBadge.setText("Playing");
                playStateBadge.setColor(successColor);
            }
            case PAUSED -> {
                playBtn.setText("> Resume");
                playBtn.setDisabled(false);
                playBtn.setColor(textPrimary);

                pauseBtn.setText("|| Paused");
                pauseBtn.setDisabled(false);
                pauseBtn.setColor(warningColor);

                stopBtn.setDisabled(false);
                stopBtn.setColor(errorColor);

                newBtn.setDisabled(true);
                saveBtn.setDisabled(true);
                loadBtn.setDisabled(true);

                playStateBadge.setText("Paused");
                playStateBadge.setColor(warningColor);
            }
        }
    }
}
