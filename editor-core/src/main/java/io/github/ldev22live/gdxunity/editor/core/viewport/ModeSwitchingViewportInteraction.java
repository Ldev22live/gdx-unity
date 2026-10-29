package io.github.ldev22live.gdxunity.editor.core.viewport;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;

import java.util.Objects;

/**
 * Routes scene view input to the 2D or 3D interaction according to {@link EditorState#getViewportMode()}.
 * A press and its drag/release always go to the interaction that received the press, even if the mode
 * changes mid-gesture.
 */
public class ModeSwitchingViewportInteraction implements ViewportInteraction {

    private final EditorState editorState;
    private final ViewportInteraction interaction2D;
    private final ViewportInteraction interaction3D;
    private ViewportInteraction gestureTarget;

    public ModeSwitchingViewportInteraction(EditorState editorState, ViewportInteraction interaction2D,
                                            ViewportInteraction interaction3D) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.interaction2D = Objects.requireNonNull(interaction2D, "interaction2D cannot be null");
        this.interaction3D = Objects.requireNonNull(interaction3D, "interaction3D cannot be null");
    }

    private ViewportInteraction active() {
        return editorState.getViewportMode() == ViewportMode.VIEW_3D ? interaction3D : interaction2D;
    }

    @Override
    public boolean touchDown(float localX, float localY, float panelWidth, float panelHeight, int button, int modifiers) {
        gestureTarget = active();
        return gestureTarget.touchDown(localX, localY, panelWidth, panelHeight, button, modifiers);
    }

    @Override
    public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
        ViewportInteraction target = gestureTarget != null ? gestureTarget : active();
        return target.touchDragged(localX, localY, panelWidth, panelHeight);
    }

    @Override
    public boolean touchUp(int button) {
        ViewportInteraction target = gestureTarget != null ? gestureTarget : active();
        gestureTarget = null;
        return target.touchUp(button);
    }

    @Override
    public boolean scrolled(float amountY) {
        return active().scrolled(amountY);
    }

    @Override
    public boolean keyDown(int keycode) {
        return active().keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        return active().keyUp(keycode);
    }

    @Override
    public void update(float deltaTime) {
        active().update(deltaTime);
    }
}
