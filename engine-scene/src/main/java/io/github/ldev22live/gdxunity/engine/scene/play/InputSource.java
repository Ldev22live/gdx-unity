package io.github.ldev22live.gdxunity.engine.scene.play;

import com.badlogic.gdx.Gdx;

/**
 * Where a profile's movement reads keys from (TD-088): the keyboard while a game runs, a fake in tests. Key codes are LibGDX's
 * {@code Input.Keys}.
 */
@FunctionalInterface
public interface InputSource {
    /** Whether the key with this LibGDX key code is held down now. */
    boolean isPressed(int keyCode);

    /**
     * The keyboard of the running application.
     *
     * @throws IllegalStateException from {@link #isPressed} outside a running application ({@code Gdx.input} is not set)
     */
    static InputSource keyboard() {
        return keyCode -> {
            if (Gdx.input == null) {
                throw new IllegalStateException("No keyboard: the application is not running (Gdx.input is not set). Tests pass "
                    + "their own InputSource.");
            }
            return Gdx.input.isKeyPressed(keyCode);
        };
    }
}
