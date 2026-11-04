package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.project.CrashLog;
import io.github.ldev22live.gdxunity.editor.core.project.EditorLaunchOptions;
import io.github.ldev22live.gdxunity.editor.core.project.EditorProjectLocator;
import io.github.ldev22live.gdxunity.editor.core.project.PlatformDirectories;
import io.github.ldev22live.gdxunity.editor.desktop.file.TinyFileDialogService;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Launcher for the desktop Forgeroom Editor application.
 *
 * <p>Arguments are described in {@link EditorLaunchOptions}. The project is chosen by {@link EditorProjectLocator}
 * (TD-033). A failure that ends the editor is written to a crash log in {@link PlatformDirectories#logDir()} and,
 * outside smoke tests, shown in a native message box, because a packaged app has no console.
 *
 * <p>Exit codes: 0 on a normal exit or a passed smoke test, 1 when startup failed, the editor crashed or a smoke test
 * found errors.
 */
public class EditorLauncher {
    /**
     * System property naming the sample project bundled with a packaged editor; set by the {@code jpackage} launcher
     * (see {@code editor-desktop/build.gradle}). Absent in development runs.
     */
    static final String SAMPLE_PROJECT_PROPERTY = "gdxunity.sampleProject";
    /** Process exit code for startup failures, crashes and failed smoke tests. */
    static final int EXIT_FAILURE = 1;

    public static void main(String[] args) {
        PlatformDirectories directories = PlatformDirectories.forCurrentPlatform();
        boolean smokeTest = Arrays.stream(args).anyMatch(arg -> arg.startsWith("--smoke-test"));
        int exitCode;
        try {
            EditorLaunchOptions options = EditorLaunchOptions.parse(args);
            if (EditorStartupHelper.startNewJvmIfRequired()) return;
            exitCode = run(options, directories);
        } catch (Throwable failure) {
            reportCrash(failure, args, directories, !smokeTest);
            exitCode = EXIT_FAILURE;
        }
        // Exit explicitly: background threads (script compiler, assistant) must not keep a finished editor alive
        System.exit(exitCode);
    }

    /** Opens the project and runs the editor until its window closes; returns the exit code. */
    static int run(EditorLaunchOptions options, PlatformDirectories directories) {
        String sample = System.getProperty(SAMPLE_PROJECT_PROPERTY);
        EditorProjectLocator.Result located = EditorProjectLocator.locate(options.project(),
            new File(System.getProperty("user.dir")), directories.projectsDir(),
            sample == null || sample.isBlank() ? null : new File(sample));
        System.out.println("Forgeroom Editor: opening " + located.project() + " (" + located.source() + ")");

        EditorApplication editor = new EditorApplication(located.project(), directories, options);
        createApplication(editor);
        if (!options.isSmokeTest()) {
            return 0;
        }
        if (!editor.isSmokeTestFinished()) {
            throw new IllegalStateException("Smoke test: the editor closed before rendering "
                + options.smokeTestFrames() + " frames");
        }
        List<EditorLog.Entry> errors = editor.getSmokeTestErrors();
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Smoke test: " + errors.size() + " error(s) in the Output Log");
            for (EditorLog.Entry error : errors) {
                message.append(System.lineSeparator()).append("  [").append(error.source()).append("] ")
                    .append(error.message());
            }
            throw new IllegalStateException(message.toString());
        }
        System.out.println("Forgeroom Editor: smoke test passed (" + options.smokeTestFrames() + " frames)");
        return 0;
    }

    private static Lwjgl3Application createApplication(EditorApplication editor) {
        Lwjgl3ApplicationConfiguration configuration = getDefaultConfiguration();
        // Closing the window offers to save unsaved scene changes first (Cancel keeps the editor open)
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public boolean closeRequested() {
                return editor.confirmClose();
            }
        });
        return new Lwjgl3Application(editor, configuration);
    }

    /**
     * Writes the crash log and tells the user where it is. Problems while reporting are attached to the original
     * failure, which is always printed to stderr.
     */
    private static void reportCrash(Throwable failure, String[] args, PlatformDirectories directories,
                                    boolean showDialog) {
        String where;
        try {
            File log = CrashLog.write(directories.logDir(), failure, LocalDateTime.now(), args,
                System.getProperties());
            where = "Details were written to " + log.getAbsolutePath();
        } catch (IOException | RuntimeException logFailure) {
            failure.addSuppressed(logFailure);
            where = "No crash log could be written to " + directories.logDir().getAbsolutePath();
        }
        System.err.println("Forgeroom Editor stopped: " + failure.getMessage());
        System.err.println(where);
        failure.printStackTrace();
        if (showDialog) {
            try {
                new TinyFileDialogService().showError("Forgeroom Editor stopped",
                    failure.getMessage() + System.lineSeparator() + System.lineSeparator() + where);
            } catch (Throwable dialogFailure) {
                System.err.println("The error dialog could not be shown: " + dialogFailure);
            }
        }
    }

    public static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Forgeroom Editor");
        configuration.useVsync(true);
        configuration.setForegroundFPS(60);

        // Responsive window resolution detection:
        // Set sensible windowed fallback size based on primary monitor or safe default,
        // and configure maximized mode to respect OS taskbar/work areas.
        int defaultWidth = 1440;
        int defaultHeight = 900;
        try {
            com.badlogic.gdx.Graphics.DisplayMode displayMode = Lwjgl3ApplicationConfiguration.getDisplayMode();
            if (displayMode != null && displayMode.width > 0 && displayMode.height > 0) {
                // Set fallback to ~85% of display dimensions to prevent overflow on non-maximized startup
                defaultWidth = Math.max(1024, (int) (displayMode.width * 0.85f));
                defaultHeight = Math.max(640, (int) (displayMode.height * 0.85f));
            }
        } catch (Throwable ignored) {
            // Safe fallback if display query fails before graphics initialization
        }

        configuration.setWindowedMode(defaultWidth, defaultHeight);
        configuration.setMaximized(true);
        configuration.setResizable(true);
        configuration.setWindowSizeLimits(1024, 640, -1, -1);
        return configuration;
    }
}
