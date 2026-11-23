package io.github.ldev22live.gdxunity.engine.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M9.5: a scene's performance as a baseline row, the median of runs, and what counts as a regression. */
class PerformanceRecordTest {
    private static final double NONE = Double.NaN;

    @TempDir
    Path folder;

    private static PerformanceRecord record(String scene, double parse, double firstFrame, double p50, double p95, double retained,
                                            double audioLatency) {
        return new PerformanceRecord(scene, 300, parse, 20.0, firstFrame, p50 + 0.2, p50, p95, p95 + 5, 1000L, 300.0, retained,
            audioLatency, Double.isNaN(audioLatency) ? NONE : 0.3, Double.isNaN(audioLatency) ? NONE : 1.1, "Test GPU 1.0");
    }

    @Test
    void shouldRoundTripARowWithTwoDecimalsAndADashForWhatWasNotMeasured() {
        PerformanceRecord record = record("samples/hopper-3d.json", 12.346, 800.0, 6.0, 9.5, 120.0, NONE);

        String row = record.toRow();
        assertEquals(PerformanceRecord.HEADER.split("\t").length, row.split("\t", -1).length);
        assertTrue(row.startsWith("samples/hopper-3d.json\t300\t12.35\t20.00\t800.00\t"), row);
        assertTrue(row.contains("\t-\t-\t-\tTest GPU 1.0"), row);

        PerformanceRecord read = PerformanceRecord.fromRow(row);
        assertEquals(12.35, read.parseMillis(), 1e-9);
        assertTrue(Double.isNaN(read.audioLatencyMillis()));
        assertEquals(row, read.toRow());
    }

    @Test
    void shouldRejectARowWithTheWrongColumnsOrANonNumber() {
        assertThrows(IllegalArgumentException.class, () -> PerformanceRecord.fromRow("a\tb"));
        String row = record("s.json", 1, 1, 1, 1, 1, 1).toRow().replace("\t300\t", "\tmany\t");
        assertThrows(IllegalArgumentException.class, () -> PerformanceRecord.fromRow(row));
        assertThrows(IllegalArgumentException.class, () -> record("has\ttab", 1, 1, 1, 1, 1, 1));
    }

    @Test
    void shouldTakeTheMedianOfEachValueSoOneSlowRunDoesNotSetTheBaseline() {
        PerformanceRecord median = PerformanceRecord.median(List.of(
            record("s.json", 10, 700, 6.0, 9.0, 100, 110),
            record("s.json", 90, 2500, 6.4, 30.0, 104, 110),
            record("s.json", 11, 720, 5.8, 9.4, 102, NONE)));

        assertEquals(11, median.parseMillis(), 1e-9);
        assertEquals(720, median.firstFrameMillis(), 1e-9);
        assertEquals(6.0, median.frameP50Millis(), 1e-9);
        assertEquals(9.4, median.frameP95Millis(), 1e-9);
        assertEquals(110, median.audioLatencyMillis(), 1e-9, "runs without a value are left out");
        assertThrows(IllegalArgumentException.class, () -> PerformanceRecord.median(List.of()));
        assertThrows(IllegalArgumentException.class, () -> PerformanceRecord.median(
            List.of(record("a.json", 1, 1, 1, 1, 1, 1), record("b.json", 1, 1, 1, 1, 1, 1))));
    }

    @Test
    void shouldReportAValueAboveTheToleranceAndItsFloorAsARegression() {
        PerformanceRecord before = record("s.json", 10, 700, 6.0, 9.0, 100, 110);

        assertEquals(List.of(), record("s.json", 12, 800, 7.0, 10.0, 115, 110).regressionsSince(before, 0.25),
            "every change within 25 %");
        assertEquals(List.of(), record("s.json", 10, 700, 1.4, 2.8, 100, 110).regressionsSince(record("s.json", 10, 700, 1.0, 1.9, 100,
            110), 0.25), "frame times up by 40 % or more, but by less than the 0.5 ms and 1 ms floors: noise");

        List<String> regressions = record("s.json", 10, 700, 8.0, 9.0, 140, 110).regressionsSince(before, 0.25);
        assertEquals(List.of("s.json: frame mean 6.20 -> 8.20 (+32%)", "s.json: frame median 6.00 -> 8.00 (+33%)",
            "s.json: heap retained 100.00 -> 140.00 (+40%)"), regressions);
    }

    @Test
    void shouldReportASoundDeviceThatNoLongerOpens() {
        PerformanceRecord before = record("s.json", 10, 700, 6.0, 9.0, 100, 110);

        List<String> regressions = record("s.json", 10, 700, 6.0, 9.0, 100, NONE).regressionsSince(before, 0.25);

        assertEquals(List.of("s.json: sound latency was 110.00, now not measured", "s.json: sound block render was 0.30, now not measured"),
            regressions);
    }

    @Test
    void shouldCompareFilesSceneBySceneAndCountAMissingSceneAsARegression() throws IOException {
        File baseline = folder.resolve("baseline.tsv").toFile();
        PerformanceBaseline.write(baseline, List.of(record("a.json", 10, 700, 6, 9, 100, NONE), record("b.json", 10, 700, 6, 9, 100, NONE)),
            List.of("Machine: test"));
        String text = Files.readString(baseline.toPath(), StandardCharsets.UTF_8);
        assertTrue(text.startsWith("# Machine: test\n" + PerformanceRecord.HEADER + "\n"), text);

        List<PerformanceRecord> read = PerformanceBaseline.read(baseline);
        assertEquals(2, read.size());
        List<String> regressions = PerformanceBaseline.compare(read,
            List.of(record("a.json", 10, 700, 6, 30, 100, NONE), record("c.json", 10, 700, 60, 90, 100, NONE)), 0.25);

        assertEquals(List.of("a.json: frame 95th percentile 9.00 -> 30.00 (+233%)", "b.json: in the baseline but not measured now"),
            regressions);
    }

    @Test
    void shouldFailToCompareAgainstAMissingBaseline() {
        File missing = folder.resolve("none.tsv").toFile();

        IOException failure = assertThrows(IOException.class, () -> PerformanceBaseline.read(missing));
        assertTrue(failure.getMessage().contains("No performance file"), failure.getMessage());
    }

    @Test
    void shouldAppendRowsUnderOneHeader() throws IOException {
        File file = folder.resolve("runs/report.tsv").toFile();

        DesktopLauncher.appendRecord(file, record("a.json", 1, 1, 1, 1, 1, NONE));
        DesktopLauncher.appendRecord(file, record("a.json", 2, 2, 2, 2, 2, NONE));

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertEquals(PerformanceRecord.HEADER, lines.get(0));
        assertEquals(2, PerformanceBaseline.read(file).size());
    }

    @Test
    void shouldRefuseFramesThatAreAllWarmUp() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> PerformanceBaseline.run(
            new String[] {"record", folder.resolve("o.tsv").toString(), "--frames", "30", "a.json"}));
        assertTrue(failure.getMessage().contains("warm-up"), failure.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PerformanceBaseline.run(new String[] {"record", "o.tsv"}));
        assertThrows(IllegalArgumentException.class, () -> PerformanceBaseline.run(new String[] {"measure"}));
    }
}
