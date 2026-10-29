package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Compact tool strip drawn over the top-left of the scene view: transform tools plus view toggles.
 * Every button runs an action from the {@link EditorActionRegistry}.
 */
public class ViewportOverlayToolbar extends Table {

    private static final float BUTTON_HEIGHT = 24f;

    private final EditorState editorState;
    private final EditorActionRegistry actions;
    private final Skin skin;
    private final Map<ToolMode, TextButton> toolButtons = new EnumMap<>(ToolMode.class);
    private final Label infoLabel;
    private Supplier<String> infoText = () -> "";

    /**
     * @param viewActionIds ids of view actions (grid, physics overlay, reset view) shown after the tools
     */
    public ViewportOverlayToolbar(EditorState editorState, EditorActionRegistry actions, Skin skin, String... viewActionIds) {
        super(skin);
        this.infoLabel = new Label("", skin, "muted");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.actions = Objects.requireNonNull(actions, "actions cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        setBackground("elevated-bg");
        pad(2f);

        addToolButton(ToolMode.SELECT, DefaultEditorActions.TOOL_SELECT);
        addToolButton(ToolMode.MOVE, DefaultEditorActions.TOOL_MOVE);
        addToolButton(ToolMode.ROTATE, DefaultEditorActions.TOOL_ROTATE);
        addToolButton(ToolMode.SCALE, DefaultEditorActions.TOOL_SCALE);

        add(new Label("|", skin, "muted")).padLeft(4).padRight(4);
        for (String id : viewActionIds) {
            add(createActionButton(id)).height(BUTTON_HEIGHT).padLeft(1).padRight(1);
        }
        add(infoLabel).padLeft(8).padRight(4);

        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onToolModeChanged(ToolMode oldMode, ToolMode newMode) {
                updateToolHighlight();
            }
        });
        updateToolHighlight();
    }

    /** Short live text shown at the end of the strip (e.g. the 3D fly speed); refreshed every frame. */
    public void setInfoText(Supplier<String> infoText) {
        this.infoText = Objects.requireNonNull(infoText, "infoText cannot be null");
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        String text = infoText.get();
        if (!text.contentEquals(infoLabel.getText())) {
            infoLabel.setText(text);
        }
    }

    private void addToolButton(ToolMode mode, String actionId) {
        TextButton button = createActionButton(actionId);
        toolButtons.put(mode, button);
        add(button).height(BUTTON_HEIGHT).padLeft(1).padRight(1);
    }

    private TextButton createActionButton(String actionId) {
        String label = actions.find(actionId)
            .map(action -> action.hasShortcut() ? action.getLabel() + " (" + action.getShortcutText() + ")" : action.getLabel())
            .orElseThrow(() -> new IllegalArgumentException("Unknown editor action id: " + actionId));
        TextButton button = new TextButton(label, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                actions.execute(actionId);
            }
        });
        return button;
    }

    private void updateToolHighlight() {
        Color active = skin.getColor("accent");
        Color normal = skin.getColor("text-primary");
        ToolMode current = editorState.getToolMode();
        for (Map.Entry<ToolMode, TextButton> entry : toolButtons.entrySet()) {
            entry.getValue().setColor(entry.getKey() == current ? active : normal);
        }
    }
}
