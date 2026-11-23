package io.github.ldev22live.gdxunity.editor.core.project;

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
import java.util.Map;
import java.util.Objects;

/**
 * Writes a crash report for a failure that ends the editor (TD-033). A packaged Windows app has no console, so this
 * file is the only place the stack trace survives.
 */
public final class CrashLog {
    /** File name timestamp; sortable and valid on every desktop file system. */
    static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** Upper bound on numbered names tried when several crashes happen within one second. */
    static final int MAX_NAME_ATTEMPTS = 100;
    /** System properties copied into the report, in this order. */
    static final List<String> REPORTED_PROPERTIES = List.of(
        "os.name", "os.version", "os.arch", "java.version", "java.vendor", "java.home", "user.dir");

    private CrashLog() {}

    /**
     * Writes {@code crash-<time>.log} into {@code logDir} (created if needed) and returns it.
     *
     * @param arguments  the editor's command-line arguments
     * @param properties source of {@link #REPORTED_PROPERTIES}, normally {@link System#getProperties()}
     * @throws IOException if the folder or file cannot be written
     */
    public static File write(File logDir, Throwable failure, LocalDateTime time, String[] arguments,
                             Map<?, ?> properties) throws IOException {
        Objects.requireNonNull(logDir, "logDir cannot be null");
        Objects.requireNonNull(failure, "failure cannot be null");
        Objects.requireNonNull(time, "time cannot be null");
        Objects.requireNonNull(arguments, "arguments cannot be null");
        Objects.requireNonNull(properties, "properties cannot be null");
        Files.createDirectories(logDir.toPath());
        String content = format(failure, time, arguments, properties);
        String stem = "crash-" + FILE_TIME.format(time);
        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            File file = new File(logDir, attempt == 0 ? stem + ".log" : stem + "-" + attempt + ".log");
            if (!file.exists()) {
                // CREATE_NEW: a crash writing the same name in the meantime fails instead of being overwritten
                Files.writeString(file.toPath(), content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
                return file;
            }
        }
        throw new IOException("Could not find a free crash log name for " + stem + " in " + logDir);
    }

    /** The report text: header, environment, arguments and full stack trace. */
    static String format(Throwable failure, LocalDateTime time, String[] arguments, Map<?, ?> properties) {
        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        out.println("Forgeroom Editor crash report");
        out.println("time: " + time);
        for (String key : REPORTED_PROPERTIES) {
            out.println(key + ": " + properties.get(key));
        }
        out.println("arguments: " + String.join(" ", arguments));
        out.println();
        failure.printStackTrace(out);
        out.flush();
        return text.toString();
    }
}
