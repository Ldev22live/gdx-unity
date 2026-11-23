package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.Files.FileType;
import io.github.ldev22live.gdxunity.engine.core.EngineApplication;
import io.github.ldev22live.gdxunity.engine.core.EngineState;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

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
    /**
     * Testing aid (roadmap M9.5): a file to append this run's {@link PerformanceRecord} to at the end of a {@code --smoke-test} run;
     * switches the render statistics on.
     */
    static final String PERF_REPORT_VARIABLE = "GDXUNITY_PERF_REPORT";
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
        long parseNanos;
        try {
            options = GameLaunchOptions.parse(args, new File("."), System.getProperty(GameLaunchOptions.SCRIPTS_PROPERTY),
                System.getProperty(GameLaunchOptions.PROJECT_PROPERTY));
            long parseStart = System.nanoTime();
            Scene scene = GameSession.loadScene(options.scene());
            parseNanos = System.nanoTime() - parseStart;
            session = new GameSession(options.project(), scene,
                new GameScripts(options.scripts(), DesktopLauncher.class.getClassLoader()), GameSession.CONSOLE);
        } catch (IllegalArgumentException | IOException e) {
            GameFailureReporter.report(gameName(), "Cannot start the game: " + e.getMessage(), e, args, isSmokeTest(args));
            return EXIT_FAILURE;
        }

        long startStart = System.nanoTime();
        List<String> problems = session.start();
        long startNanos = System.nanoTime() - startStart;
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
        // Testing aids (roadmap M3.8): GDXUNITY_RENDER_STATS=1 reports each frame's cost at the end of a --smoke-test run;
        // GDXUNITY_TERRAIN_LOD=0 draws every terrain chunk at full detail, to compare
        String perfReport = System.getenv(PERF_REPORT_VARIABLE);
        boolean perf = perfReport != null && !perfReport.isBlank() && options.isSmokeTest();
        renderer.setRenderStats(perf || "1".equals(System.getenv("GDXUNITY_RENDER_STATS")));
        renderer.setTerrainLod(!"0".equals(System.getenv("GDXUNITY_TERRAIN_LOD")));
        // GDXUNITY_OBJECT_CULLING=0 draws every shape and model in every pass, to compare (roadmap M3.6)
        renderer.setObjectCulling(!"0".equals(System.getenv("GDXUNITY_OBJECT_CULLING")));
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
            int result = smokeTestResult(options, renderer, session);
            if (perf) {
                PerformanceRecord record = performanceRecord(options, renderer, parseNanos, startNanos);
                System.out.println("Performance: " + record.toRow());
                try {
                    appendRecord(new File(perfReport), record);
                } catch (IOException e) {
                    System.err.println("Cannot write the performance record to " + perfReport + ": " + e.getMessage());
                    return EXIT_FAILURE;
                }
            }
            return result;
        }
        return EXIT_OK;
    }

    private static int smokeTestResult(GameLaunchOptions options, GameRenderer renderer, GameSession session) {
        List<String> errors = session.getRuntimeErrors();
        boolean allFrames = renderer.getFramesRendered() >= options.smokeTestFrames();
        System.out.println("Smoke test: " + renderer.getFramesRendered() + " of " + options.smokeTestFrames()
            + " frames, " + errors.size() + " runtime error(s)");
        errors.forEach(error -> System.out.println("  " + error));
        if (renderer.getMeasuredFrames() > 0) {
            System.out.println(String.format(Locale.ROOT, "Render stats: %.2f ms per frame (GPU finished), %d terrain triangles, over %d "
                + "frames", renderer.getAverageFrameMillis(), renderer.getAverageTerrainTriangles(), renderer.getMeasuredFrames()));
        }
        return allFrames && errors.isEmpty() ? EXIT_OK : EXIT_FAILURE;
    }

    /** This run's measurements; the scene is named by its path inside the project when it is in it. */
    static PerformanceRecord performanceRecord(GameLaunchOptions options, GameRenderer renderer, long parseNanos, long startNanos) {
        Path project = options.project().toPath().toAbsolutePath().normalize();
        Path scene = options.scene().toPath().toAbsolutePath().normalize();
        String name = scene.startsWith(project) ? project.relativize(scene).toString().replace(File.separatorChar, '/') : scene.toString();
        FrameTimeStats frames = renderer.getFrameTimes();
        boolean measured = frames != null && frames.count() > 0;
        AudioOutputStats audio = renderer.getAudioStats();
        return new PerformanceRecord(name, renderer.getMeasuredFrames(), parseNanos / 1e6, startNanos / 1e6,
            renderer.getFirstFrameMillis(), measured ? frames.meanMillis() : Double.NaN,
            measured ? frames.percentileMillis(50) : Double.NaN, measured ? frames.percentileMillis(95) : Double.NaN,
            measured ? frames.maxMillis() : Double.NaN, renderer.getAverageTerrainTriangles(),
            PerformanceRecord.mebibytes(measured ? renderer.getPeakHeapBytes() : -1),
            PerformanceRecord.mebibytes(renderer.getRetainedHeapBytes()),
            audio == null ? Double.NaN : audio.latencyMillis(), audio == null ? Double.NaN : audio.meanRenderMillis(),
            audio == null ? Double.NaN : audio.maxRenderMillis(), renderer.getGpuName().replace("\t", " "));
    }

    /** Appends {@code record} as a row, writing the header first when the file is new or empty. */
    static void appendRecord(File file, PerformanceRecord record) throws IOException {
        Path path = file.toPath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        boolean fresh = !Files.isRegularFile(path) || Files.size(path) == 0;
        String text = (fresh ? PerformanceRecord.HEADER + System.lineSeparator() : "") + record.toRow() + System.lineSeparator();
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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

    /** Bits per colour channel of the window. */
    private static final int COLOR_BITS = 8;
    /** Bits of the window's depth buffer (see {@link #configuration}). */
    static final int DEPTH_BITS = 24;

    private static Lwjgl3ApplicationConfiguration configuration(String title, File projectIcon) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(title);
        if (projectIcon != null) {
            configuration.setWindowIcon(FileType.Absolute, projectIcon.getAbsolutePath());
        } else {
            configuration.setWindowIcon(DEFAULT_ICONS);
        }
        // 24 depth bits, not LibGDX's default 16: at the 1 to 50000 near and far planes of a terrain scene, 16 bits resolve only
        // about 250 units at 4 km, so water and the ground it lies on drew in bands (roadmap M3.9)
        configuration.setBackBufferConfig(COLOR_BITS, COLOR_BITS, COLOR_BITS, COLOR_BITS, DEPTH_BITS, 0, 0);
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        return configuration;
    }
}
