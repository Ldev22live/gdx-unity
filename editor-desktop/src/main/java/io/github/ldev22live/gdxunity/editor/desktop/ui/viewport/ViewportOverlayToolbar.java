package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Compact tool strip drawn over the top-left of the scene view: transform tools plus view toggles.
 * Every button runs an action from the {@link EditorActionRegistry}.
 */
public class ViewportOverlayToolbar extends Table {

    private static final float BUTTON_HEIGHT = 24f;
    /** Width of an icon-only button. */
    private static final float ICON_BUTTON_WIDTH = 28f;

    private final EditorState editorState;
    private final EditorActionRegistry actions;
    private final Skin skin;
    private final Map<ToolMode, Button> toolButtons = new EnumMap<>(ToolMode.class);
    private final Label infoLabel;
    private final Map<String, Button> actionButtons = new LinkedHashMap<>();
    private final Map<String, BooleanSupplier> toggles = new LinkedHashMap<>();
    private final Map<String, String> iconStyles;
    private Supplier<String> infoText = () -> "";
    /** Label of the button under the mouse, shown in place of the info text; empty when none. */
    private String hoverText = "";

    /**
     * @param iconStyles    skin style name of the icon for each action id; an action without an entry is a text button
     * @param viewActionIds ids of view actions (grid, physics overlay, reset view) shown after the tools
     */
    public ViewportOverlayToolbar(EditorState editorState, EditorActionRegistry actions, Skin skin,
                                  Map<String, String> iconStyles, String... viewActionIds) {
        this(editorState, actions, skin, true, iconStyles, viewActionIds);
    }

    /**
     * @param withTools false to leave out the select, move, rotate and scale buttons (the top bar has them as labelled
     *                  buttons); only the view actions and the info text are shown then
     */
    public ViewportOverlayToolbar(EditorState editorState, EditorActionRegistry actions, Skin skin, boolean withTools,
                                  Map<String, String> iconStyles, String... viewActionIds) {
        super(skin);
        this.iconStyles = Map.copyOf(Objects.requireNonNull(iconStyles, "iconStyles cannot be null"));
        this.infoLabel = new Label("", skin, "muted");
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.actions = Objects.requireNonNull(actions, "actions cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        setBackground("elevated-bg");
        pad(2f);

        if (withTools) {
            addToolButton(ToolMode.SELECT, DefaultEditorActions.TOOL_SELECT);
            addToolButton(ToolMode.MOVE, DefaultEditorActions.TOOL_MOVE);
            addToolButton(ToolMode.ROTATE, DefaultEditorActions.TOOL_ROTATE);
            addToolButton(ToolMode.SCALE, DefaultEditorActions.TOOL_SCALE);

            add(new Label("|", skin, "muted")).padLeft(4).padRight(4);
        }
        for (String id : viewActionIds) {
            Button button = createActionButton(id);
            actionButtons.put(id, button);
            addSized(button);
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
    /**
     * Makes one of the view-action buttons show a state: it is drawn in the accent colour while {@code active} is true,
     * like the selected tool.
     */
    public void setToggle(String actionId, BooleanSupplier active) {
        if (!actionButtons.containsKey(actionId)) {
            throw new IllegalArgumentException("No toolbar button for action " + actionId);
        }
        toggles.put(actionId, Objects.requireNonNull(active, "active cannot be null"));
    }

    public void setInfoText(Supplier<String> infoText) {
        this.infoText = Objects.requireNonNull(infoText, "infoText cannot be null");
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        for (Map.Entry<String, BooleanSupplier> toggle : toggles.entrySet()) {
            actionButtons.get(toggle.getKey()).setColor(skin.getColor(toggle.getValue().getAsBoolean() ? "accent" : "text-primary"));
        }
        String text = hoverText.isEmpty() ? infoText.get() : hoverText;
        if (!text.contentEquals(infoLabel.getText())) {
            infoLabel.setText(text);
        }
    }

    private void addToolButton(ToolMode mode, String actionId) {
        Button button = createActionButton(actionId);
        toolButtons.put(mode, button);
        addSized(button);
    }

    private void addSized(Button button) {
        float width = button instanceof TextButton ? 0f : ICON_BUTTON_WIDTH;
        add(button).height(BUTTON_HEIGHT).width(width > 0f ? width : button.getPrefWidth()).padLeft(1).padRight(1);
    }

    private Button createActionButton(String actionId) {
        String label = actions.find(actionId)
            .map(action -> action.hasShortcut() ? action.getLabel() + " (" + action.getShortcutText() + ")" : action.getLabel())
            .orElseThrow(() -> new IllegalArgumentException("Unknown editor action id: " + actionId));
        String iconStyle = iconStyles.get(actionId);
        Button button = iconStyle != null ? new ImageButton(skin.get(iconStyle, ImageButton.ImageButtonStyle.class))
            : new TextButton(label, skin, "flat");
        if (iconStyle != null) {
            // Icon-only buttons name themselves in the strip while the mouse is over them
            button.addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    hoverText = label;
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    if (label.equals(hoverText)) {
                        hoverText = "";
                    }
                }
            });
        }
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
        for (Map.Entry<ToolMode, Button> entry : toolButtons.entrySet()) {
            entry.getValue().setColor(entry.getKey() == current ? active : normal);
        }
    }
}
