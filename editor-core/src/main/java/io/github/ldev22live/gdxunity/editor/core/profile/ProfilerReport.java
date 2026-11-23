package io.github.ldev22live.gdxunity.editor.core.profile;

import io.github.ldev22live.gdxunity.engine.core.profile.FrameProfiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One recorded frame as the Profiler panel shows it (roadmap M9.6): the sections as a tree, slowest first among siblings, with the
 * time in this frame, the average over recent frames, the share of the frame and each counter's growth. Time a parent spent outside
 * its timed children shows as an {@value #OTHER} row, and time the frame spent outside every top-level section as
 * {@value #UNTIMED}, so the rows of one level always add up to their parent.
 */
public final class ProfilerReport {
    /** Name of the row for a parent's time outside its timed children. */
    public static final String OTHER = "Other";
    /** Name of the top-level row for the frame's time outside every section. */
    public static final String UNTIMED = "Untimed";
    /** A remainder smaller than this is noise from timing the sections themselves and gets no row. */
    public static final double MIN_REMAINDER_MILLIS = 0.01;
    /** Frames averaged by default: one second at 60 frames a second. */
    public static final int DEFAULT_AVERAGE_FRAMES = 60;

    private static final double NANOS_PER_MILLI = 1_000_000.0;

    /**
     * One row.
     *
     * @param section       the profiler section, or -1 for an {@value #OTHER} or {@value #UNTIMED} row
     * @param millis        time in the frame shown
     * @param averageMillis mean time per frame over the averaged frames (0 for a remainder row)
     * @param percent       share of the frame's measured time
     * @param calls         how often the section was timed in the frame (0 for a remainder row)
     * @param counts        each counter's growth while the section was open; empty for a remainder row
     */
    public record Row(String name, int depth, int section, double millis, double averageMillis, double percent, int calls,
                      long[] counts) {
        public Row {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(counts, "counts cannot be null");
        }

        /** True for an {@value #OTHER} or {@value #UNTIMED} row. */
        public boolean isRemainder() {
            return section < 0;
        }
    }

    /**
     * The frame as a whole.
     *
     * @param frameMillis     from the start to the end of the frame loop's work
     * @param periodMillis    from this frame's start to the next one's, with waiting; 0 while the next frame has not started
     * @param averageMillis   mean of {@code frameMillis} over the averaged frames
     * @param slowestMillis   the slowest {@code frameMillis} in the history
     * @param framesAgo       which frame this is, 0 for the newest
     * @param frames          frames in the history
     * @param values          the frame values, such as the entity count, in registration order
     */
    public record Summary(double frameMillis, double periodMillis, double averageMillis, double slowestMillis, int framesAgo,
                          int frames, long[] values) {
        /** Frames a second from the period, or 0 without one. */
        public double framesPerSecond() {
            return periodMillis > 0 ? 1000.0 / periodMillis : 0.0;
        }

        /** Time between frames that the loop did not measure: waiting for vsync and the GPU, and handling input. */
        public double waitingMillis() {
            return periodMillis > 0 ? Math.max(0.0, periodMillis - frameMillis) : 0.0;
        }
    }

    private ProfilerReport() {
    }

    /** The frame {@code framesAgo} back (0 is the newest) summed up, averaging the last {@code averageFrames}. */
    public static Summary summary(FrameProfiler profiler, int framesAgo, int averageFrames) {
        Objects.requireNonNull(profiler, "profiler cannot be null");
        checkAverage(averageFrames);
        int frames = profiler.getFrameCount();
        int averaged = Math.min(averageFrames, frames);
        double total = 0.0;
        for (int i = 0; i < averaged; i++) {
            total += profiler.getFrameNanos(i);
        }
        long slowest = 0L;
        for (int i = 0; i < frames; i++) {
            slowest = Math.max(slowest, profiler.getFrameNanos(i));
        }
        long[] values = new long[profiler.getValueCount()];
        for (int v = 0; v < values.length; v++) {
            values[v] = profiler.getValue(framesAgo, v);
        }
        return new Summary(millis(profiler.getFrameNanos(framesAgo)), millis(profiler.getPeriodNanos(framesAgo)),
            averaged == 0 ? 0.0 : total / averaged / NANOS_PER_MILLI, millis(slowest), framesAgo, frames, values);
    }

    /**
     * The rows for the frame {@code framesAgo} back, depth first. A section shows when it was timed in that frame or in one of the
     * averaged frames, so a step that runs every few frames does not flicker in and out.
     */
    public static List<Row> rows(FrameProfiler profiler, int framesAgo, int averageFrames) {
        Objects.requireNonNull(profiler, "profiler cannot be null");
        checkAverage(averageFrames);
        long frameNanos = profiler.getFrameNanos(framesAgo);
        int averaged = Math.min(averageFrames, profiler.getFrameCount());
        List<Row> rows = new ArrayList<>();
        addChildren(profiler, FrameProfiler.ROOT, frameNanos, framesAgo, averaged, frameNanos, rows);
        return rows;
    }

    private static void addChildren(FrameProfiler profiler, int parent, long parentNanos, int framesAgo, int averaged, long frameNanos,
                                    List<Row> rows) {
        List<Integer> children = new ArrayList<>();
        for (int s = 0; s < profiler.getSectionCount(); s++) {
            if (profiler.getSectionParent(s) == parent && wasTimed(profiler, s, framesAgo, averaged)) {
                children.add(s);
            }
        }
        if (children.isEmpty()) {
            return;
        }
        children.sort(Comparator.comparingLong((Integer s) -> profiler.getSectionNanos(framesAgo, s)).reversed()
            .thenComparing(Comparator.naturalOrder()));
        int depth = parent == FrameProfiler.ROOT ? 0 : profiler.getSectionDepth(parent) + 1;
        long childNanos = 0L;
        for (int s : children) {
            long nanos = profiler.getSectionNanos(framesAgo, s);
            childNanos += nanos;
            long[] counts = new long[profiler.getCounterCount()];
            for (int c = 0; c < counts.length; c++) {
                counts[c] = profiler.getSectionCount(framesAgo, s, c);
            }
            double average = 0.0;
            for (int i = 0; i < averaged; i++) {
                average += profiler.getSectionNanos(i, s);
            }
            average = averaged == 0 ? 0.0 : average / averaged / NANOS_PER_MILLI;
            rows.add(new Row(profiler.getSectionName(s), depth, s, millis(nanos), average, percent(nanos, frameNanos),
                profiler.getSectionCalls(framesAgo, s), counts));
            addChildren(profiler, s, nanos, framesAgo, averaged, frameNanos, rows);
        }
        long remainder = parentNanos - childNanos;
        if (millis(remainder) >= MIN_REMAINDER_MILLIS) {
            rows.add(new Row(parent == FrameProfiler.ROOT ? UNTIMED : OTHER, depth, -1, millis(remainder), 0.0,
                percent(remainder, frameNanos), 0, new long[0]));
        }
    }

    private static boolean wasTimed(FrameProfiler profiler, int section, int framesAgo, int averaged) {
        if (profiler.getSectionCalls(framesAgo, section) > 0) {
            return true;
        }
        for (int i = 0; i < averaged; i++) {
            if (profiler.getSectionCalls(i, section) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The frame as plain text: the summary line, then one indented line per row with its counts. Used to copy a frame from the panel
     * and by the editor's smoke test to print what the panel shows.
     */
    public static String toText(FrameProfiler profiler, int framesAgo, int averageFrames) {
        Summary summary = summary(profiler, framesAgo, averageFrames);
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "Frame %d back (of %d): %.2f ms work, ", framesAgo, summary.frames(),
            summary.frameMillis()));
        text.append(summary.periodMillis() > 0
            ? String.format(Locale.ROOT, "%.2f ms to the next frame (%.0f fps), %.2f ms waiting", summary.periodMillis(),
                summary.framesPerSecond(), summary.waitingMillis())
            : "next frame not started");
        text.append(String.format(Locale.ROOT, "; average %.2f ms, slowest %.2f ms", summary.averageMillis(), summary.slowestMillis()));
        for (int v = 0; v < summary.values().length; v++) {
            text.append("; ").append(profiler.getValueName(v)).append(' ').append(summary.values()[v]);
        }
        text.append('\n');
        for (Row row : rows(profiler, framesAgo, averageFrames)) {
            text.append("  ".repeat(row.depth() + 1))
                .append(String.format(Locale.ROOT, "%s %.3f ms (%.1f%%, average %.3f ms", row.name(), row.millis(), row.percent(),
                    row.averageMillis()));
            if (row.calls() > 1) {
                text.append(", ").append(row.calls()).append(" calls");
            }
            for (int c = 0; c < row.counts().length; c++) {
                text.append(", ").append(profiler.getCounterName(c)).append(' ').append(row.counts()[c]);
            }
            text.append(")\n");
        }
        return text.toString();
    }

    private static void checkAverage(int averageFrames) {
        if (averageFrames < 1) {
            throw new IllegalArgumentException("averageFrames must be at least 1: " + averageFrames);
        }
    }

    private static double millis(long nanos) {
        return nanos / NANOS_PER_MILLI;
    }

    private static double percent(long nanos, long frameNanos) {
        return frameNanos > 0 ? 100.0 * nanos / frameNanos : 0.0;
    }
}
