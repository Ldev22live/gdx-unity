package io.github.ldev22live.gdxunity.engine.desktop;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Performance baselines (roadmap M9.5): plays a fixed list of scenes in the game runtime, each in a fresh JVM, and writes their
 * median {@link PerformanceRecord}s to a baseline file; or compares two such files and lists what got worse.
 *
 * <pre>
 *   record &lt;out.tsv&gt; [--runs N] [--frames N] [--scripts dir] [--project dir] scene... [--project dir scene...]
 *   compare &lt;baseline.tsv&gt; &lt;current.tsv&gt; [--tolerance 0.25]
 * </pre>
 *
 * {@code --project} applies to the scenes after it, so one baseline can hold scenes of several projects (the bundled samples
 * and a generated large map). Without it, scenes are relative to the working directory.
 *
 * Exit code: 0 when recording succeeded or nothing regressed, 1 otherwise. A scene that fails to play fails the recording; it
 * is not left out silently. Run through {@code ./gradlew :engine-desktop:perfBaseline} (see {@code docs/PERFORMANCE_BASELINES.md}).
 */
public final class PerformanceBaseline {
    /** Runs per scene; the median of each value is kept. */
    static final int DEFAULT_RUNS = 3;
    /** Frames per run: {@value GameRenderer#STATS_WARMUP_FRAMES} warm-up frames, then about five seconds measured at 60 fps. */
    static final int DEFAULT_FRAMES = 330;
    /** A regression is a value more than this fraction above the baseline (and above its metric's floor). */
    static final double DEFAULT_TOLERANCE = 0.25;
    /**
     * The longest one run may take. A minimized game window stops rendering (LWJGL pauses it), so without a limit a run whose
     * window was minimized would wait for ever.
     */
    static final long RUN_TIMEOUT_MINUTES = 5;
    /** Comment lines of a baseline file start with this. */
    static final String COMMENT = "#";

    private PerformanceBaseline() {
    }

    public static void main(String[] args) {
        int exitCode;
        try {
            exitCode = run(args);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println(e.getMessage());
            exitCode = DesktopLauncher.EXIT_FAILURE;
        }
        System.exit(exitCode);
    }

    static int run(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: record <out.tsv> [--runs N] [--frames N] [--project dir] [--scripts dir] scene... "
                + "| compare <baseline.tsv> <current.tsv> [--tolerance F]");
        }
        return switch (args[0]) {
            case "record" -> record(args);
            case "compare" -> compare(args);
            default -> throw new IllegalArgumentException("unknown command: " + args[0]);
        };
    }

    private static int record(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("record needs an output file");
        }
        File out = new File(args[1]);
        int runs = DEFAULT_RUNS;
        int frames = DEFAULT_FRAMES;
        String project = null;
        String scripts = null;
        // Scene to the project it is played in (null: the working directory)
        Map<String, String> scenes = new LinkedHashMap<>();
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--runs" -> runs = positive(args, ++i);
                case "--frames" -> frames = positive(args, ++i);
                case "--project" -> project = value(args, ++i);
                case "--scripts" -> scripts = value(args, ++i);
                default -> scenes.put(args[i], project);
            }
        }
        if (scenes.isEmpty()) {
            throw new IllegalArgumentException("record needs at least one scene");
        }
        if (frames <= GameRenderer.STATS_WARMUP_FRAMES) {
            throw new IllegalArgumentException("--frames must be more than the " + GameRenderer.STATS_WARMUP_FRAMES + " warm-up frames: " + frames);
        }
        List<PerformanceRecord> medians = new ArrayList<>();
        for (Map.Entry<String, String> scene : scenes.entrySet()) {
            List<PerformanceRecord> sceneRuns = new ArrayList<>();
            for (int run = 1; run <= runs; run++) {
                System.out.println("Playing " + scene.getKey() + ", run " + run + " of " + runs);
                sceneRuns.add(playOnce(scene.getKey(), frames, scene.getValue(), scripts));
            }
            PerformanceRecord median = PerformanceRecord.median(sceneRuns);
            System.out.println("  " + median.toRow());
            medians.add(median);
        }
        write(out, medians, comment(runs, frames));
        System.out.println("Wrote " + medians.size() + " scene(s) to " + out);
        return DesktopLauncher.EXIT_OK;
    }

    /** Plays one scene in a new JVM with the same class path and returns what it recorded. */
    private static PerformanceRecord playOnce(String scene, int frames, String project, String scripts) throws IOException {
        Path report = Files.createTempFile("forgeroom-perf", ".tsv");
        try {
            List<String> command = new ArrayList<>();
            command.add(ProcessHandle.current().info().command().orElseThrow(
                () -> new IOException("cannot tell which Java runs this process")));
            command.add("--enable-native-access=ALL-UNNAMED");
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
                command.add("-XstartOnFirstThread");
            }
            String scriptsProperty = scripts != null ? scripts : System.getProperty(GameLaunchOptions.SCRIPTS_PROPERTY);
            if (scriptsProperty != null) {
                command.add("-D" + GameLaunchOptions.SCRIPTS_PROPERTY + "=" + scriptsProperty);
            }
            command.add(DesktopLauncher.class.getName());
            if (project != null) {
                command.add("--project");
                command.add(project);
            }
            command.add("--scene");
            command.add(scene);
            command.add("--smoke-test=" + frames);
            ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
            // The class path goes through the environment: on Windows it can be longer than a command line may be
            builder.environment().put("CLASSPATH", System.getProperty("java.class.path"));
            builder.environment().put(DesktopLauncher.PERF_REPORT_VARIABLE, report.toString());
            int exit;
            Process process = builder.start();
            try {
                if (!process.waitFor(RUN_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    throw new IOException(scene + " did not finish within " + RUN_TIMEOUT_MINUTES + " minutes; a minimized game window "
                        + "stops rendering, so keep it on screen while measuring");
                }
                exit = process.exitValue();
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while playing " + scene, e);
            }
            List<PerformanceRecord> records = Files.size(report) == 0 ? List.of() : read(report.toFile());
            if (exit != DesktopLauncher.EXIT_OK || records.size() != 1) {
                throw new IOException(scene + " did not play cleanly (exit " + exit + ", " + records.size()
                    + " record(s)); see its output above");
            }
            return records.get(0);
        } finally {
            Files.deleteIfExists(report);
        }
    }

    private static int compare(String[] args) throws IOException {
        if (args.length < 3) {
            throw new IllegalArgumentException("compare needs a baseline and a current file");
        }
        double tolerance = DEFAULT_TOLERANCE;
        for (int i = 3; i < args.length; i++) {
            if (args[i].equals("--tolerance")) {
                tolerance = Double.parseDouble(value(args, ++i));
            } else {
                throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }
        List<String> regressions = compare(read(new File(args[1])), read(new File(args[2])), tolerance);
        if (regressions.isEmpty()) {
            System.out.println("No regressions beyond " + Math.round(tolerance * 100) + " %.");
            return DesktopLauncher.EXIT_OK;
        }
        System.out.println("Regressions beyond " + Math.round(tolerance * 100) + " %:");
        regressions.forEach(line -> System.out.println("  " + line));
        return DesktopLauncher.EXIT_FAILURE;
    }

    /**
     * What regressed from {@code baseline} to {@code current}, scene by scene; a baseline scene missing from {@code current} is
     * a regression too, since it can no longer be compared. Scenes only in {@code current} are new and not compared.
     */
    static List<String> compare(List<PerformanceRecord> baseline, List<PerformanceRecord> current, double tolerance) {
        Objects.requireNonNull(baseline, "baseline cannot be null");
        Objects.requireNonNull(current, "current cannot be null");
        Map<String, PerformanceRecord> now = new LinkedHashMap<>();
        current.forEach(record -> now.put(record.scene(), record));
        List<String> regressions = new ArrayList<>();
        for (PerformanceRecord before : baseline) {
            PerformanceRecord after = now.get(before.scene());
            if (after == null) {
                regressions.add(before.scene() + ": in the baseline but not measured now");
            } else {
                regressions.addAll(after.regressionsSince(before, tolerance));
            }
        }
        return regressions;
    }

    /**
     * Reads a baseline file: comment lines and the header are skipped, every other line is a record.
     *
     * @throws IOException if the file does not exist: comparing against a missing baseline must not pass
     */
    static List<PerformanceRecord> read(File file) throws IOException {
        if (!file.isFile()) {
            throw new IOException("No performance file at " + file.getAbsolutePath());
        }
        List<PerformanceRecord> records = new ArrayList<>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith(COMMENT) || line.equals(PerformanceRecord.HEADER)) {
                continue;
            }
            records.add(PerformanceRecord.fromRow(line));
        }
        return records;
    }

    /** Writes a baseline file: the comment lines, the header, then one row per record. */
    static void write(File file, List<PerformanceRecord> records, List<String> comments) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String comment : comments) {
            text.append(COMMENT).append(' ').append(comment).append('\n');
        }
        text.append(PerformanceRecord.HEADER).append('\n');
        for (PerformanceRecord record : records) {
            text.append(record.toRow()).append('\n');
        }
        Path path = file.toPath().toAbsolutePath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    /** What a reader needs to judge the numbers: the machine, the Java, and how they were taken. */
    private static List<String> comment(int runs, int frames) {
        return List.of(
            "Forgeroom performance baseline (roadmap M9.5); medians of " + runs + " run(s) per scene, " + frames + " frames each, the first "
                + GameRenderer.STATS_WARMUP_FRAMES + " not measured",
            "Machine: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")
                + ", " + Runtime.getRuntime().availableProcessors() + " processors; Java " + System.getProperty("java.version")
                + "; the GPU is in each row",
            "Frame times wait for the GPU to finish (glFinish), so vsync does not hide them; heap is the Java heap only, not native memory");
    }

    private static String value(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException(args[index - 1] + " needs a value");
        }
        return args[index];
    }

    private static int positive(String[] args, int index) {
        String text = value(args, index);
        try {
            int value = Integer.parseInt(text);
            if (value < 1) {
                throw new IllegalArgumentException(args[index - 1] + " must be at least 1: " + text);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(args[index - 1] + " is not a whole number: " + text, e);
        }
    }
}
