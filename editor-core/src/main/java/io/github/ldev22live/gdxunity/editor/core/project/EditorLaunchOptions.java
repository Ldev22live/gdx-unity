package io.github.ldev22live.gdxunity.editor.core.project;

import java.io.File;
import java.util.Objects;

/**
 * Editor command-line options (TD-033).
 * <ul>
 *   <li>{@code --project <folder>} or {@code --project=<folder>}: open this project</li>
 *   <li>{@code --scene <file>} or {@code --scene=<file>}: open this scene file at startup; a relative path is
 *       relative to the project folder</li>
 *   <li>{@code --play}: enter Play mode once the editor is up</li>
 *   <li>{@code --export-game <folder>} or {@code --export-game=<folder>}: do not open the editor; compile the scripts,
 *       export the project as a standalone game into this new folder (Phase 18-C, TD-046) and exit with 0 on success
 *       or 1 on failure. The start scene is {@code --scene}, else the project file's {@code startScene}.</li>
 *   <li>{@code --smoke-test} or {@code --smoke-test=<frames>}: render that many frames, then exit with 0 on success
 *       or 1 when anything failed; for automated checks of a packaged build</li>
 * </ul>
 * Unknown options are rejected. The {@code -psn_...} argument older macOS versions pass to apps started from Finder
 * is ignored.
 *
 * @param project         requested project folder, or null
 * @param scene           scene file to open at startup, or null for the built-in startup scene
 * @param play            whether to enter Play mode after startup
 * @param exportGame      folder to export a standalone game to instead of opening the editor, or null
 * @param smokeTestFrames frames to render before exiting, or 0 for a normal session
 */
public record EditorLaunchOptions(File project, File scene, boolean play, File exportGame, int smokeTestFrames) {
    /** Frames rendered by {@code --smoke-test} without a count: a few seconds at 60 frames per second. */
    public static final int DEFAULT_SMOKE_TEST_FRAMES = 180;

    static final String PROJECT = "--project";
    static final String SMOKE_TEST = "--smoke-test";
    static final String SCENE = "--scene";
    static final String PLAY = "--play";
    static final String EXPORT_GAME = "--export-game";
    /** Prefix of the process serial number argument added by older macOS Finder launches. */
    static final String MAC_PROCESS_SERIAL_PREFIX = "-psn_";

    public EditorLaunchOptions {
        if (smokeTestFrames < 0) {
            throw new IllegalArgumentException("smokeTestFrames must be >= 0: " + smokeTestFrames);
        }
    }

    /** True when the editor should only export a game and exit. */
    public boolean isGameExport() {
        return exportGame != null;
    }

    /** True when the editor should exit by itself after {@link #smokeTestFrames()} frames. */
    public boolean isSmokeTest() {
        return smokeTestFrames > 0;
    }

    /**
     * @throws IllegalArgumentException for unknown options, a missing folder after {@code --project} or an invalid
     *                                  frame count
     */
    public static EditorLaunchOptions parse(String[] args) {
        Objects.requireNonNull(args, "args cannot be null");
        File project = null;
        File scene = null;
        File exportGame = null;
        boolean play = false;
        int frames = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals(PROJECT) || arg.startsWith(PROJECT + "=")) {
                project = new File(pathValue(args, i, PROJECT));
                if (arg.equals(PROJECT)) {
                    i++;
                }
            } else if (arg.equals(SCENE) || arg.startsWith(SCENE + "=")) {
                scene = new File(pathValue(args, i, SCENE));
                if (arg.equals(SCENE)) {
                    i++;
                }
            } else if (arg.equals(EXPORT_GAME) || arg.startsWith(EXPORT_GAME + "=")) {
                exportGame = new File(pathValue(args, i, EXPORT_GAME));
                if (arg.equals(EXPORT_GAME)) {
                    i++;
                }
            } else if (arg.equals(PLAY)) {
                play = true;
            } else if (arg.equals(SMOKE_TEST)) {
                frames = DEFAULT_SMOKE_TEST_FRAMES;
            } else if (arg.startsWith(SMOKE_TEST + "=")) {
                frames = parseFrames(arg.substring(SMOKE_TEST.length() + 1));
            } else if (!arg.startsWith(MAC_PROCESS_SERIAL_PREFIX)) {
                throw new IllegalArgumentException("Unknown option: " + arg + " (supported: " + PROJECT + " <folder>, "
                    + SCENE + " <file>, " + PLAY + ", " + EXPORT_GAME + " <folder>, " + SMOKE_TEST + "[=frames])");
            }
        }
        return new EditorLaunchOptions(project, scene, play, exportGame, frames);
    }

    /** The path after {@code option}, either as {@code option=value} or as the next argument. */
    private static String pathValue(String[] args, int index, String option) {
        String arg = args[index];
        String value = arg.equals(option) ? (index + 1 < args.length ? args[index + 1] : "")
            : arg.substring(option.length() + 1);
        if (value.isBlank()) {
            throw new IllegalArgumentException(option + " needs a path");
        }
        return value;
    }

    private static int parseFrames(String value) {
        try {
            int frames = Integer.parseInt(value);
            if (frames > 0) {
                return frames;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(SMOKE_TEST + " frame count must be a positive number: " + value, e);
        }
        throw new IllegalArgumentException(SMOKE_TEST + " frame count must be a positive number: " + value);
    }
}
