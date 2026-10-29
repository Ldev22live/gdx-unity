package io.github.ldev22live.gdxunity.editor.visualscript;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** When each node of each graph last ran during play, so the graph editor can highlight active nodes. */
public class GraphExecutionTrace {
    private final Map<String, Long> lastRunNanos = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public GraphExecutionTrace() {
        this(System::nanoTime);
    }

    /** @param clock nanosecond clock (tests pass a fake) */
    public GraphExecutionTrace(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public void record(String graphPath, String nodeId) {
        lastRunNanos.put(key(graphPath, nodeId), clock.getAsLong());
    }

    /** Whether the node ran in the last {@code seconds}. */
    public boolean ranWithin(String graphPath, String nodeId, float seconds) {
        Long time = lastRunNanos.get(key(graphPath, nodeId));
        return time != null && clock.getAsLong() - time <= (long) (seconds * 1e9);
    }

    public void clear() {
        lastRunNanos.clear();
    }

    private static String key(String graphPath, String nodeId) {
        return graphPath.toLowerCase(Locale.ROOT) + "#" + nodeId;
    }
}
