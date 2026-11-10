package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.Files;
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
    /** Forgeroom's icon in the sizes the window system picks from, bundled in this module's resources. */
    private static final String[] DEFAULT_ICONS = {
        "forgeroom-icon-256.png", "forgeroom-icon-128.png", "forgeroom-icon-64.png", "forgeroom-icon-32.png"};
    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired(args)) {
            return;
        }
        int exitCode;
        try {
            exitCode = run(args);
        } catch (Throwable failure) {
            // Anything not handled in run: still leave a crash log, a packaged game has no console
            GameFailureReporter.report(gameName(), "The game stopped with an error: " + failure.getMessage(), failure, args,
                isSmokeTest(args));
            exitCode = EXIT_FAILURE;
        }
        System.exit(exitCode);
    }

    /** True when the command line asks for a smoke test: automated runs get no message box. */
    private static boolean isSmokeTest(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--smoke-test")) {
                return true;
            }
        }
        return false;
    }

    /** The game's name from the project file, for log folders and message boxes. */
    private static String gameName() {
        String project = System.getProperty(GameLaunchOptions.PROJECT_PROPERTY);
        try {
            String name = GameProjectFile.read(new File(project == null || project.isBlank() ? "." : project)).name();
            return name.isBlank() ? GameFailureReporter.DEFAULT_GAME_NAME : name;
        } catch (IOException | RuntimeException e) {
            return GameFailureReporter.DEFAULT_GAME_NAME;
        }
    }

    /** Runs the game and returns the process exit code. */
    static int run(String[] args) {
        GameLaunchOptions options;
        GameSession session;
        try {
            options = GameLaunchOptions.parse(args, new File("."), System.getProperty(GameLaunchOptions.SCRIPTS_PROPERTY),
                System.getProperty(GameLaunchOptions.PROJECT_PROPERTY));
            Scene scene = GameSession.loadScene(options.scene());
            session = new GameSession(options.project(), scene,
                new GameScripts(options.scripts(), DesktopLauncher.class.getClassLoader()), GameSession.CONSOLE);
        } catch (IllegalArgumentException | IOException e) {
            GameFailureReporter.report(gameName(), "Cannot start the game: " + e.getMessage(), e, args, isSmokeTest(args));
            return EXIT_FAILURE;
        }

        List<String> problems = session.start();
        if (!problems.isEmpty()) {
            GameFailureReporter.report(gameName(), "Cannot play " + options.scene() + ":" + System.lineSeparator()
                + String.join(System.lineSeparator(), problems), null, args, options.isSmokeTest());
            disposeAfterFailedStart(session);
            return EXIT_FAILURE;
        }

        GameRenderer renderer = new GameRenderer(session, options.project());
        renderer.setExitAfterFrames(options.smokeTestFrames());
        // Testing aid (TD-054): GDXUNITY_SCREENSHOT=<file.png> saves the frame at the end of a --smoke-test run
        String screenshot = System.getenv("GDXUNITY_SCREENSHOT");
        if (screenshot != null && !screenshot.isBlank()) {
            renderer.setScreenshotFile(new File(screenshot));
        }
        EngineApplication application = new EngineApplication(renderer);
        try {
            new Lwjgl3Application(application, configuration(session.getScene().getName(), windowIcon(options.project())));
        } catch (RuntimeException e) {
            GameFailureReporter.report(gameName(), "The game stopped with an error: " + e.getMessage(), e, args,
                options.isSmokeTest());
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

    /** The project's own window icon when its project file names a PNG that exists; null for the Forgeroom icon. */
    static File windowIcon(File project) {
        try {
            String icon = GameProjectFile.read(project).windowIcon();
            File file = icon.isBlank() ? null : new File(project, icon);
            return file != null && file.isFile() ? file : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Lwjgl3ApplicationConfiguration configuration(String title, File projectIcon) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(title);
        if (projectIcon != null) {
            configuration.setWindowIcon(Files.FileType.Absolute, projectIcon.getAbsolutePath());
        } else {
            configuration.setWindowIcon(DEFAULT_ICONS);
        }
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        return configuration;
    }
}
