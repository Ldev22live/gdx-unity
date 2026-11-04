package io.github.ldev22live.gdxunity.engine.desktop;

import java.io.File;
import java.util.Objects;

/**
 * Command-line options of the game runtime (TD-035).
 *
 * <pre>
 *   --project &lt;folder&gt;     folder the scene's asset paths are relative to (default: the working directory)
 *   --scene &lt;file&gt;         scene to play, relative to the project unless absolute (required)
 *   --scripts &lt;dir|jar&gt;    compiled script classes (default: the {@value #SCRIPTS_PROPERTY} system property,
 *                          else &lt;project&gt;/build/scripts, where the editor compiles them)
 *   --smoke-test[=N]       render N frames ({@value #DEFAULT_SMOKE_TEST_FRAMES} by default), then exit 0, or 1 on errors
 * </pre>
 *
 * @param project         project folder
 * @param scene           scene file, resolved against the project
 * @param scripts         compiled script classes, folder or jar, resolved against the project
 * @param smokeTestFrames frames to render before exiting; 0 to run until the window is closed
 */
public record GameLaunchOptions(File project, File scene, File scripts, int smokeTestFrames) {
    /** System property naming the compiled scripts when {@code --scripts} is not given (set by the Gradle run task). */
    public static final String SCRIPTS_PROPERTY = "gdxunity.scripts";
    /** Where the editor writes compiled scripts inside a project (TD-033). */
    public static final String DEFAULT_SCRIPTS_PATH = "build/scripts";
    /** Frames a smoke test renders when {@code --smoke-test} has no count: about three seconds at 60 fps. */
    public static final int DEFAULT_SMOKE_TEST_FRAMES = 180;

    private static final String PROJECT = "--project";
    private static final String SCENE = "--scene";
    private static final String SCRIPTS = "--scripts";
    private static final String SMOKE_TEST = "--smoke-test";

    public GameLaunchOptions {
        Objects.requireNonNull(project, "project cannot be null");
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(scripts, "scripts cannot be null");
        if (smokeTestFrames < 0) {
            throw new IllegalArgumentException("smokeTestFrames must not be negative: " + smokeTestFrames);
        }
    }

    public boolean isSmokeTest() {
        return smokeTestFrames > 0;
    }

    /**
     * Parses the command line.
     *
     * @param workingDirectory  folder relative project paths are resolved against; all returned paths are absolute
     * @param scriptsProperty   value of the {@value #SCRIPTS_PROPERTY} system property, or null
     * @throws IllegalArgumentException for an unknown option, a missing value or a missing {@code --scene}
     */
    public static GameLaunchOptions parse(String[] args, File workingDirectory, String scriptsProperty) {
        Objects.requireNonNull(args, "args cannot be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory cannot be null");
        String project = null;
        String scene = null;
        String scripts = null;
        int smokeTestFrames = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case PROJECT -> project = valueOf(args, ++i, arg);
                case SCENE -> scene = valueOf(args, ++i, arg);
                case SCRIPTS -> scripts = valueOf(args, ++i, arg);
                case SMOKE_TEST -> smokeTestFrames = DEFAULT_SMOKE_TEST_FRAMES;
                default -> {
                    if (arg.startsWith(SMOKE_TEST + "=")) {
                        smokeTestFrames = parseFrames(arg.substring(SMOKE_TEST.length() + 1));
                    } else if (!arg.startsWith("-psn_")) {
                        // -psn_ is added by macOS when an app is started from Finder
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                }
            }
        }
        if (scene == null) {
            throw new IllegalArgumentException("No scene given. Usage: --scene <file> [--project <folder>] "
                + "[--scripts <folder or jar>] [--smoke-test[=frames]]");
        }
        File projectDir = resolve(workingDirectory, project == null ? "." : project);
        String scriptsPath = scripts != null ? scripts
            : scriptsProperty != null && !scriptsProperty.isBlank() ? scriptsProperty : DEFAULT_SCRIPTS_PATH;
        return new GameLaunchOptions(projectDir, resolve(projectDir, scene), resolve(projectDir, scriptsPath),
            smokeTestFrames);
    }

    private static String valueOf(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static int parseFrames(String value) {
        try {
            int frames = Integer.parseInt(value);
            if (frames <= 0) {
                throw new IllegalArgumentException("--smoke-test frame count must be positive: " + value);
            }
            return frames;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--smoke-test frame count is not a number: " + value, e);
        }
    }

    private static File resolve(File base, String path) {
        File file = new File(path);
        // Absolute first: normalizing a relative "./." would leave an empty path that resolves against the drive root
        return (file.isAbsolute() ? file : new File(base, path)).toPath().toAbsolutePath().normalize().toFile();
    }
}
