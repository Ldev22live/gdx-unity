package io.github.ldev22live.gdxunity.editor.desktop;

import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Testing aid (TD-054 style): with {@code GDXUNITY_EDITOR_TRACE=<file>} the editor appends a line to that file for every Output Log
 * entry, every change of the undo stack and every uncaught exception, so someone watching the file sees what a person did in the
 * running editor without seeing the screen. Off unless the variable is set; a file that cannot be written turns the trace off after one
 * message on the error stream, and never disturbs the editor.
 */
final class EditorTrace {
    private final Path file;
    private EditorLog.Entry lastEntry;
    private boolean broken;

    private EditorTrace(Path file) {
        this.file = file;
    }

    /** Starts tracing to {@code fileName} (a blank name does nothing). */
    static void install(String fileName, EditorLog log, CommandHistory history) {
        Objects.requireNonNull(log, "log cannot be null");
        Objects.requireNonNull(history, "history cannot be null");
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        EditorTrace trace = new EditorTrace(Path.of(fileName.trim()));
        trace.write("trace started");
        log.addListener(trace::onLog);
        history.addListener(h -> trace.write("history: undo=" + h.getUndoCount() + " redo=" + h.getRedoCount()
            + " next undo: " + h.getUndoDescription()));
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            StringWriter text = new StringWriter();
            error.printStackTrace(new PrintWriter(text));
            trace.write("UNCAUGHT in " + thread.getName() + ": " + text);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private synchronized void onLog(EditorLog log) {
        List<EditorLog.Entry> entries = log.getEntries(EditorLog.Severity.INFO);
        int from = lastEntry == null ? 0 : entries.indexOf(lastEntry) + 1;
        for (int i = from; i < entries.size(); i++) {
            EditorLog.Entry entry = entries.get(i);
            write(entry.severity() + " [" + entry.source() + "] " + entry.message());
        }
        if (!entries.isEmpty()) {
            lastEntry = entries.get(entries.size() - 1);
        }
    }

    private synchronized void write(String line) {
        if (broken) {
            return;
        }
        try {
            Files.writeString(file, LocalTime.now().withNano(0) + " " + line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            broken = true;
            System.err.println("Editor trace turned off, cannot write " + file + ": " + e.getMessage());
        }
    }
}
