package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.ldev22live.gdxunity.engine.core.EngineApplication;
import io.github.ldev22live.gdxunity.engine.core.EngineState;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Game runtime (TD-035): plays one scene file in a window, with the same systems as the editor's Play button.
 * See {@link GameLaunchOptions} for the command line.
 */
public final class DesktopLauncher {
    /** Initial window size in pixels; the window can be resized. */
    static final int WINDOW_WIDTH = 1280;
    static final int WINDOW_HEIGHT = 720;
    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired(args)) {
            return;
        }
        System.exit(run(args));
    }

    /** Runs the game and returns the process exit code. */
    static int run(String[] args) {
        GameLaunchOptions options;
        GameSession session;
        try {
            options = GameLaunchOptions.parse(args, new File("."), System.getProperty(GameLaunchOptions.SCRIPTS_PROPERTY));
            Scene scene = GameSession.loadScene(options.scene());
            session = new GameSession(options.project(), scene,
                new GameScripts(options.scripts(), DesktopLauncher.class.getClassLoader()), GameSession.CONSOLE);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Cannot start the game: " + e.getMessage());
            return EXIT_FAILURE;
        }

        List<String> problems = session.start();
        if (!problems.isEmpty()) {
            System.err.println("Cannot play " + options.scene() + ":");
            problems.forEach(problem -> System.err.println("  " + problem));
            disposeAfterFailedStart(session);
            return EXIT_FAILURE;
        }

        GameRenderer renderer = new GameRenderer(session, options.project());
        renderer.setExitAfterFrames(options.smokeTestFrames());
        EngineApplication application = new EngineApplication(renderer);
        try {
            new Lwjgl3Application(application, configuration(session.getScene().getName()));
        } catch (RuntimeException e) {
            System.err.println("The game stopped with an error:");
            e.printStackTrace(System.err);
            return EXIT_FAILURE;
        }
        if (application.getState() == EngineState.FAILED) {
            return EXIT_FAILURE;
        }
        if (options.isSmokeTest()) {
            return smokeTestResult(options, renderer, session);
        }
        return EXIT_OK;
    }

    private static int smokeTestResult(GameLaunchOptions options, GameRenderer renderer, GameSession session) {
        List<String> errors = session.getRuntimeErrors();
        boolean allFrames = renderer.getFramesRendered() >= options.smokeTestFrames();
        System.out.println("Smoke test: " + renderer.getFramesRendered() + " of " + options.smokeTestFrames()
            + " frames, " + errors.size() + " runtime error(s)");
        errors.forEach(error -> System.out.println("  " + error));
        return allFrames && errors.isEmpty() ? EXIT_OK : EXIT_FAILURE;
    }

    private static void disposeAfterFailedStart(GameSession session) {
        try {
            session.dispose();
        } catch (RuntimeException e) {
            System.err.println("Cleaning up after the failed start also failed: " + e.getMessage());
        }
    }

    private static Lwjgl3ApplicationConfiguration configuration(String title) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(title);
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        return configuration;
    }
}
