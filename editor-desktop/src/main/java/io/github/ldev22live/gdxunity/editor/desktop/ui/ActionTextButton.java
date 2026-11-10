package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * A labelled, bordered button in the top bar that runs an editor action, like the web editor's New, Save, Undo and tool
 * buttons. It is disabled while the action is, and shows the "selected" look while {@code selected} is true (the active
 * tool, sculpt mode).
 */
public class ActionTextButton extends TextButton {
    private final EditorAction action;
    private final BooleanSupplier selected;

    /**
     * @param actionId the action to run; it must be registered
     * @param label    the text on the button
     * @param selected when it returns true the button is drawn selected; null for a plain button
     * @throws IllegalArgumentException if no action has the id
     */
    public ActionTextButton(Skin skin, EditorActionRegistry actions, String actionId, String label, BooleanSupplier selected) {
        super(label, skin, "bar");
        Objects.requireNonNull(actions, "actions cannot be null");
        this.action = actions.find(actionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown editor action id: " + actionId));
        this.selected = selected != null ? selected : () -> false;
        addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                actions.execute(actionId);
            }
        });
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        setDisabled(!action.isEnabled());
        // A click toggles the button's own checked state; the editor state is the truth, so put it back every frame
        boolean shouldBeChecked = selected.getAsBoolean();
        if (isChecked() != shouldBeChecked) {
            setProgrammaticChangeEvents(false);
            setChecked(shouldBeChecked);
            setProgrammaticChangeEvents(true);
        }
    }
}
