package io.github.ldev22live.gdxunity.engine.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One scene's performance in the game runtime (roadmap M9.5): how long it takes to load, what a frame costs, how much Java heap
 * it uses and the sound output's latency. One tab-separated row of a baseline file ({@link #HEADER}); a value that was not
 * measured (no sound device, say) is NaN and written as {@value #NONE}.
 *
 * @param scene              the scene file as given on the command line
 * @param measuredFrames     frames that went into the frame times (after the warm-up)
 * @param parseMillis        reading and parsing the scene file
 * @param startMillis        starting the session: scripts, physics, graphs, animators, sound binding
 * @param firstFrameMillis   creating the renderers and drawing the first frame, which loads models, textures and terrain
 * @param frameMeanMillis    mean frame cost, GPU finished
 * @param frameP50Millis     median frame cost
 * @param frameP95Millis     95th percentile frame cost: the slow frames a player notices
 * @param frameMaxMillis     the slowest measured frame
 * @param terrainTriangles   terrain triangles drawn per frame, on average
 * @param heapPeakMb         the largest Java heap use seen while measuring, in MiB
 * @param heapRetainedMb     Java heap in use after a garbage collection on the last frame, in MiB
 * @param audioLatencyMillis buffered sound latency (device buffers plus one block); NaN when no device was open
 * @param audioBlockMillis   mean time to render one sound block; NaN when no device was open
 * @param audioBlockMaxMillis the longest a sound block took; NaN when no device was open
 * @param gpu                the OpenGL renderer string
 */
public record PerformanceRecord(String scene, int measuredFrames, double parseMillis, double startMillis, double firstFrameMillis,
                                double frameMeanMillis, double frameP50Millis, double frameP95Millis, double frameMaxMillis,
                                long terrainTriangles, double heapPeakMb, double heapRetainedMb, double audioLatencyMillis,
                                double audioBlockMillis, double audioBlockMaxMillis, String gpu) {
    /** The column names, tab separated, in the order {@link #toRow()} writes them. */
    public static final String HEADER = String.join("\t", "scene", "frames", "parseMs", "startMs", "firstFrameMs", "frameMeanMs",
        "frameP50Ms", "frameP95Ms", "frameMaxMs", "terrainTriangles", "heapPeakMb", "heapRetainedMb", "audioLatencyMs",
        "audioBlockMs", "audioBlockMaxMs", "gpu");
    /** How a value that was not measured is written. */
    public static final String NONE = "-";
    private static final int COLUMNS = 16;
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;

    public PerformanceRecord {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(gpu, "gpu cannot be null");
        if (scene.isBlank() || scene.contains("\t") || gpu.contains("\t")) {
            throw new IllegalArgumentException("scene must be non-blank and neither scene nor gpu may contain a tab: " + scene);
        }
    }

    /** Bytes to MiB. */
    public static double mebibytes(long bytes) {
        return bytes < 0 ? Double.NaN : bytes / BYTES_PER_MIB;
    }

    /** This record as a tab-separated row, values with two decimals. */
    public String toRow() {
        return String.join("\t", scene, Integer.toString(measuredFrames), format(parseMillis), format(startMillis),
            format(firstFrameMillis), format(frameMeanMillis), format(frameP50Millis), format(frameP95Millis), format(frameMaxMillis),
            Long.toString(terrainTriangles), format(heapPeakMb), format(heapRetainedMb), format(audioLatencyMillis),
            format(audioBlockMillis), format(audioBlockMaxMillis), gpu);
    }

    /**
     * Reads a row {@link #toRow()} wrote.
     *
     * @throws IllegalArgumentException if the row has the wrong number of columns or a value is not a number
     */
    public static PerformanceRecord fromRow(String row) {
        Objects.requireNonNull(row, "row cannot be null");
        String[] cells = row.split("\t", -1);
        if (cells.length != COLUMNS) {
            throw new IllegalArgumentException("a performance row has " + COLUMNS + " columns, this has " + cells.length + ": " + row);
        }
        try {
            return new PerformanceRecord(cells[0], Integer.parseInt(cells[1]), parse(cells[2]), parse(cells[3]), parse(cells[4]),
                parse(cells[5]), parse(cells[6]), parse(cells[7]), parse(cells[8]), Long.parseLong(cells[9]), parse(cells[10]),
                parse(cells[11]), parse(cells[12]), parse(cells[13]), parse(cells[14]), cells[15]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number in performance row: " + row, e);
        }
    }

    /**
     * The median of each value over several runs of the same scene, so one slow run (a virus scan, a background update) does not
     * set the baseline. Frames, triangles and the GPU come from the first run.
     *
     * @throws IllegalArgumentException if {@code runs} is empty or mixes scenes
     */
    public static PerformanceRecord median(List<PerformanceRecord> runs) {
        Objects.requireNonNull(runs, "runs cannot be null");
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("runs must not be empty");
        }
        PerformanceRecord first = runs.get(0);
        for (PerformanceRecord run : runs) {
            if (!run.scene.equals(first.scene)) {
                throw new IllegalArgumentException("runs of different scenes: " + first.scene + ", " + run.scene);
            }
        }
        return new PerformanceRecord(first.scene, first.measuredFrames, median(runs, Metric.PARSE), median(runs, Metric.START),
            median(runs, Metric.FIRST_FRAME), median(runs, Metric.FRAME_MEAN), median(runs, Metric.FRAME_P50),
            median(runs, Metric.FRAME_P95), median(runs, Metric.FRAME_MAX), first.terrainTriangles, median(runs, Metric.HEAP_PEAK),
            median(runs, Metric.HEAP_RETAINED), median(runs, Metric.AUDIO_LATENCY), median(runs, Metric.AUDIO_BLOCK),
            median(runs, Metric.AUDIO_BLOCK_MAX), first.gpu);
    }

    private static double median(List<PerformanceRecord> runs, Metric metric) {
        double[] values = runs.stream().mapToDouble(metric::of).filter(value -> !Double.isNaN(value)).sorted().toArray();
        if (values.length == 0) {
            return Double.NaN;
        }
        int middle = values.length / 2;
        return values.length % 2 == 1 ? values[middle] : (values[middle - 1] + values[middle]) / 2;
    }

    /**
     * The values a comparison checks, each with the smallest change that counts: below it, run-to-run noise is larger than the
     * change. The maximum frame and the heap peak are not compared, since one hitch or one collection moves them a lot.
     */
    public enum Metric {
        PARSE("parse", 10.0),
        START("start", 25.0),
        FIRST_FRAME("first frame", 50.0),
        FRAME_MEAN("frame mean", 0.5),
        FRAME_P50("frame median", 0.5),
        FRAME_P95("frame 95th percentile", 1.0),
        FRAME_MAX("frame max", Double.POSITIVE_INFINITY),
        HEAP_PEAK("heap peak", Double.POSITIVE_INFINITY),
        HEAP_RETAINED("heap retained", 16.0),
        AUDIO_LATENCY("sound latency", 1.0),
        AUDIO_BLOCK("sound block render", 0.5),
        AUDIO_BLOCK_MAX("sound block render max", Double.POSITIVE_INFINITY);

        private final String label;
        private final double floor;

        Metric(String label, double floor) {
            this.label = label;
            this.floor = floor;
        }

        /** How the metric is named in a comparison. */
        public String label() {
            return label;
        }

        /** The smallest increase, in the metric's unit (ms or MiB), that counts as a regression. */
        public double floor() {
            return floor;
        }

        /** This metric's value in a record. */
        public double of(PerformanceRecord record) {
            return switch (this) {
                case PARSE -> record.parseMillis;
                case START -> record.startMillis;
                case FIRST_FRAME -> record.firstFrameMillis;
                case FRAME_MEAN -> record.frameMeanMillis;
                case FRAME_P50 -> record.frameP50Millis;
                case FRAME_P95 -> record.frameP95Millis;
                case FRAME_MAX -> record.frameMaxMillis;
                case HEAP_PEAK -> record.heapPeakMb;
                case HEAP_RETAINED -> record.heapRetainedMb;
                case AUDIO_LATENCY -> record.audioLatencyMillis;
                case AUDIO_BLOCK -> record.audioBlockMillis;
                case AUDIO_BLOCK_MAX -> record.audioBlockMaxMillis;
            };
        }
    }

    /**
     * What got worse from {@code baseline} to this record: each compared metric that grew by more than {@code tolerance} (a
     * fraction, 0.25 for 25 %) and by more than its {@link Metric#floor()}. Also a sound device that was open in the baseline and is
     * not now. Empty when nothing regressed.
     */
    public List<String> regressionsSince(PerformanceRecord baseline, double tolerance) {
        Objects.requireNonNull(baseline, "baseline cannot be null");
        if (!(tolerance >= 0)) {
            throw new IllegalArgumentException("tolerance must not be negative: " + tolerance);
        }
        List<String> regressions = new ArrayList<>();
        for (Metric metric : Metric.values()) {
            double before = metric.of(baseline);
            double now = metric.of(this);
            if (Double.isNaN(before) || metric.floor == Double.POSITIVE_INFINITY) {
                continue;
            }
            if (Double.isNaN(now)) {
                regressions.add(String.format(Locale.ROOT, "%s: %s was %.2f, now not measured", scene, metric.label, before));
            } else if (now - before > metric.floor && now > before * (1 + tolerance)) {
                regressions.add(String.format(Locale.ROOT, "%s: %s %.2f -> %.2f (+%.0f%%)", scene, metric.label, before, now,
                    (now / before - 1) * 100));
            }
        }
        return regressions;
    }

    private static String format(double value) {
        return Double.isNaN(value) ? NONE : String.format(Locale.ROOT, "%.2f", value);
    }

    private static double parse(String cell) {
        return NONE.equals(cell) ? Double.NaN : Double.parseDouble(cell);
    }
}
