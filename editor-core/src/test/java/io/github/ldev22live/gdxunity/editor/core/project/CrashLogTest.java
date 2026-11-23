package io.github.ldev22live.gdxunity.editor.core.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashLogTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 30, 14, 5, 9);
    private static final Map<String, String> PROPERTIES = Map.of("os.name", "Windows 11", "java.version", "26.0.2");

    @TempDir
    Path temp;

    @Test
    void shouldWriteEnvironmentArgumentsAndStackTraceIntoANewLogFolder() throws IOException {
        File logDir = temp.resolve("logs").toFile();
        IllegalStateException failure = new IllegalStateException("shader did not compile",
            new RuntimeException("root cause"));

        File log = CrashLog.write(logDir, failure, TIME, new String[] {"--smoke-test"}, PROPERTIES);

        assertEquals("crash-20261030-140509.log", log.getName());
        String text = Files.readString(log.toPath());
        assertTrue(text.contains("os.name: Windows 11"), text);
        assertTrue(text.contains("java.version: 26.0.2"), text);
        assertTrue(text.contains("arguments: --smoke-test"), text);
        assertTrue(text.contains("IllegalStateException: shader did not compile"), text);
        assertTrue(text.contains("Caused by: java.lang.RuntimeException: root cause"), text);
    }

    @Test
    void shouldNotOverwriteAnEarlierCrashFromTheSameSecond() throws IOException {
        File logDir = temp.toFile();

        File first = CrashLog.write(logDir, new RuntimeException("first"), TIME, new String[0], PROPERTIES);
        File second = CrashLog.write(logDir, new RuntimeException("second"), TIME, new String[0], PROPERTIES);

        assertNotEquals(first, second);
        assertTrue(Files.readString(first.toPath()).contains("first"));
        assertTrue(Files.readString(second.toPath()).contains("second"));
    }
}
