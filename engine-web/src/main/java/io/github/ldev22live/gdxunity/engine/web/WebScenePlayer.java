package io.github.ldev22live.gdxunity.engine.web;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameRenderer;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.desktop.ScriptFactory;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Plays a scene in the browser with the same {@link GameSession} and {@link GameRenderer} as the desktop runtime
 * (TD-035, TD-036). Project files are the assets bundled with the page ({@link AssetSource#internal()}); scripts
 * come from the build's generated registry.
 *
 * <p>The session is created in {@link #create()}, because the bundled files are only readable once the web
 * backend has started. A scene that cannot start fails loudly: every problem is logged and the backend shows the
 * error.
 */
public final class WebScenePlayer extends ApplicationAdapter {
    /**
     * Keys whose browser default (scrolling, moving focus) is cancelled while the game has focus.
     * Without this, a game embedded in an iframe scrolls the host page on Space and the arrow keys, and Tab
     * leaves the game.
     */
    static final int[] CAUGHT_KEYS = {
        Keys.SPACE, Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT, Keys.PAGE_UP, Keys.PAGE_DOWN, Keys.END,
        Keys.TAB, Keys.BACKSPACE
    };

    private final String scenePath;
    private final Supplier<? extends ScriptFactory> scripts;
    private GameRenderer renderer;

    /**
     * @param scenePath scene file relative to the project's {@code assets/} folder
     * @param scripts   creates the script factory when the scene starts; the session owns and closes it
     */
    public WebScenePlayer(String scenePath, Supplier<? extends ScriptFactory> scripts) {
        this.scenePath = Objects.requireNonNull(scenePath, "scenePath cannot be null");
        this.scripts = Objects.requireNonNull(scripts, "scripts cannot be null");
    }

    /** Tells the backend to cancel the browser's default action for {@link #CAUGHT_KEYS}. */
    static void catchBrowserKeys(Input input) {
        for (int key : CAUGHT_KEYS) {
            input.setCatchKey(key, true);
        }
    }

    @Override
    public void create() {
        catchBrowserKeys(Gdx.input);
        AssetSource project = AssetSource.internal();
        Scene scene;
        try {
            scene = GameSession.loadScene(project, scenePath);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot play " + scenePath + ": " + e.getMessage(), e);
        }
        GameSession session = new GameSession(project, scene, scripts.get(), GameSession.CONSOLE);
        List<String> problems = session.start();
        if (!problems.isEmpty()) {
            problems.forEach(GameSession.CONSOLE::error);
            session.dispose();
            throw new IllegalStateException("Cannot play " + scenePath + ": " + String.join("; ", problems));
        }
        renderer = new GameRenderer(session, project);
        renderer.initialize();
        renderer.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        GameSession.CONSOLE.info("Playing " + scenePath);
    }

    @Override
    public void resize(int width, int height) {
        if (renderer != null) {
            renderer.resize(width, height);
        }
    }

    @Override
    public void render() {
        renderer.render(Gdx.graphics.getDeltaTime());
    }

    /** Disposes the renderer, which disposes the session and its scene last. Idempotent. */
    @Override
    public void dispose() {
        GameRenderer owned = renderer;
        renderer = null;
        if (owned != null) {
            owned.dispose();
        }
    }
}
