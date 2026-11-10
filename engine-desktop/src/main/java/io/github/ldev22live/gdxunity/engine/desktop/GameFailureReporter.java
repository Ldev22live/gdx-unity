package io.github.ldev22live.gdxunity.engine.desktop;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Tells the player why an exported game could not run (TD-046). A packaged Windows game has no console, so a failure is
 * written to a crash log in the player's own log folder and shown in a native message box. Smoke tests (automated runs)
 * skip the box, because nobody is there to close it.
 */
final class GameFailureReporter {
    /** Game name used for the log folder when the project has none. */
    static final String DEFAULT_GAME_NAME = "Forgeroom Game";
    /** File name timestamp; sortable and valid on every desktop file system. */
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** Upper bound on numbered names tried when several crashes happen within one second. */
    private static final int MAX_NAME_ATTEMPTS = 100;
    /** System properties copied into the report, in this order. */
    private static final List<String> REPORTED_PROPERTIES = List.of(
        "os.name", "os.version", "os.arch", "java.version", "java.vendor", "java.home", "user.dir");

    private GameFailureReporter() {}

    /**
     * The folder crash logs of {@code gameName} go to, per user and never inside the (possibly read-only) install folder:
     * {@code %LOCALAPPDATA%\<name>\logs} on Windows, {@code ~/Library/Logs/<name>} on macOS, and
     * {@code $XDG_STATE_HOME/<name>/logs} (else {@code ~/.local/state/<name>/logs}) elsewhere.
     */
    static File logDir(String osName, Map<String, String> environment, File userHome, String gameName) {
        Objects.requireNonNull(osName, "osName cannot be null");
        Objects.requireNonNull(environment, "environment cannot be null");
        Objects.requireNonNull(userHome, "userHome cannot be null");
        String folder = folderName(gameName);
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String local = environment.get("LOCALAPPDATA");
            File base = local == null || local.isBlank() ? new File(userHome, "AppData" + File.separator + "Local") : new File(local);
            return new File(new File(base, folder), "logs");
        }
        if (os.contains("mac")) {
            return new File(new File(userHome, "Library" + File.separator + "Logs"), folder);
        }
        String state = environment.get("XDG_STATE_HOME");
        // The XDG specification says a relative value is invalid and must be ignored
        File base = state != null && new File(state).isAbsolute() ? new File(state) : new File(userHome, ".local" + File.separator + "state");
        return new File(new File(base, folder), "logs");
    }

    /** {@code name} as a folder name: letters, digits, spaces, dashes and underscores only. */
    static String folderName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return cleaned.isEmpty() ? DEFAULT_GAME_NAME : cleaned;
    }

    /**
     * Writes {@code crash-<time>.log} into {@code logDir} (created if needed) and returns it.
     *
     * @throws IOException if the folder or file cannot be written
     */
    static File writeLog(File logDir, String message, Throwable failure, LocalDateTime time, String[] arguments,
                         Map<?, ?> properties) throws IOException {
        Files.createDirectories(logDir.toPath());
        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        out.println("Forgeroom game crash report");
        out.println("time: " + time);
        for (String key : REPORTED_PROPERTIES) {
            out.println(key + ": " + properties.get(key));
        }
        out.println("arguments: " + String.join(" ", arguments));
        out.println("message: " + message);
        if (failure != null) {
            out.println();
            failure.printStackTrace(out);
        }
        out.flush();
        String stem = "crash-" + FILE_TIME.format(time);
        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            File file = new File(logDir, attempt == 0 ? stem + ".log" : stem + "-" + attempt + ".log");
            if (!file.exists()) {
                // CREATE_NEW: a crash writing the same name in the meantime fails instead of overwriting it
                Files.writeString(file.toPath(), text.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
                return file;
            }
        }
        throw new IOException("Could not find a free crash log name for " + stem + " in " + logDir);
    }

    /**
     * Prints {@code message} to stderr, writes the crash log and, unless {@code quiet}, shows a message box.
     * Problems while reporting never replace the original failure: they are printed too.
     *
     * @param gameName title of the game, for the log folder and the box
     * @param quiet    true in smoke tests: no message box
     */
    static void report(String gameName, String message, Throwable failure, String[] args, boolean quiet) {
        String where;
        try {
            File logDir = logDir(System.getProperty("os.name"), System.getenv(), new File(System.getProperty("user.home")), gameName);
            File log = writeLog(logDir, message, failure, LocalDateTime.now(), args, System.getProperties());
            where = "Details were written to " + log.getAbsolutePath();
        } catch (IOException | RuntimeException logFailure) {
            where = "No crash log could be written: " + logFailure.getMessage();
        }
        System.err.println(message);
        System.err.println(where);
        if (failure != null) {
            failure.printStackTrace();
        }
        if (!quiet) {
            try {
                TinyFileDialogs.tinyfd_messageBox(sanitize(gameName + " stopped"), sanitize(message + "\n\n" + where), "ok", "error", true);
            } catch (Throwable dialogFailure) {
                System.err.println("The error dialog could not be shown: " + dialogFailure);
            }
        }
    }

    /** tinyfd rejects quote characters in dialog text on some platforms. */
    static String sanitize(String text) {
        return text.replace('"', '`').replace('\'', '`');
    }
}
