package io.github.ldev22live.gdxunity.editor.core.log;

import io.github.ldev22live.gdxunity.editor.core.EditorState;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bounded, in-memory log behind the Output Log panel. Keeps the most recent {@link #DEFAULT_CAPACITY}
 * entries and notifies listeners on every change.
 */
public class EditorLog {

    public static final int DEFAULT_CAPACITY = 1000;

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    /** One log line. */
    public record Entry(LocalTime time, Severity severity, String source, String message) {
        public Entry {
            Objects.requireNonNull(time, "time cannot be null");
            Objects.requireNonNull(severity, "severity cannot be null");
            Objects.requireNonNull(source, "source cannot be null");
            Objects.requireNonNull(message, "message cannot be null");
        }
    }

    public interface Listener {
        void onLogChanged(EditorLog log);
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public EditorLog() {
        this(DEFAULT_CAPACITY);
    }

    public EditorLog(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got: " + capacity);
        }
        this.capacity = capacity;
    }

    /** Records every editor status message: errors as {@link Severity#ERROR}, the rest as {@link Severity#INFO}. */
    public void captureStatusMessages(EditorState editorState) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onStatusMessage(String message, boolean isError) {
                add(isError ? Severity.ERROR : Severity.INFO, "Editor", message);
            }
        });
    }

    public void info(String source, String message) {
        add(Severity.INFO, source, message);
    }

    public void warning(String source, String message) {
        add(Severity.WARNING, source, message);
    }

    public void error(String source, String message) {
        add(Severity.ERROR, source, message);
    }

    public synchronized void add(Severity severity, String source, String message) {
        entries.addLast(new Entry(LocalTime.now(), severity, source, message));
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
        notifyListeners();
    }

    /** Entries at or above {@code minimumSeverity}, oldest first. */
    public synchronized List<Entry> getEntries(Severity minimumSeverity) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.severity().ordinal() >= minimumSeverity.ordinal()) {
                result.add(entry);
            }
        }
        return result;
    }

    public synchronized int size() {
        return entries.size();
    }

    public void clear() {
        synchronized (this) {
            entries.clear();
        }
        notifyListeners();
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.onLogChanged(this);
        }
    }
}
