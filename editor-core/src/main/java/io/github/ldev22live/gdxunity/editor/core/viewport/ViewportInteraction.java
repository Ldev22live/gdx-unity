package io.github.ldev22live.gdxunity.editor.core.viewport;

/**
 * Pointer and keyboard handling for the scene view, independent of any UI toolkit.
 *
 * <p>Coordinates are panel-local: origin at the bottom-left of the scene view, y pointing up, in UI units.
 * Modifiers are a bitwise OR of {@code EditorKeyboardDispatcher.MOD_CTRL}, {@code MOD_SHIFT} and
 * {@code MOD_ALT}.
 */
public interface ViewportInteraction {

    /** @return true if the press was handled and later drag/up events should be routed here */
    boolean touchDown(float localX, float localY, float panelWidth, float panelHeight, int button, int modifiers);

    boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight);

    boolean touchUp(int button);

    boolean scrolled(float amountY);

    /** @return true if the key was consumed (e.g. by fly navigation) and must not trigger editor shortcuts */
    default boolean keyDown(int keycode) {
        return false;
    }

    default boolean keyUp(int keycode) {
        return false;
    }

    /** Per-frame update for continuous navigation such as fly movement. */
    default void update(float deltaTime) {}
}
