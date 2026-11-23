package io.github.ldev22live.gdxunity.editor.core.prefab;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M9.3 (TD-093): prefab files are written in the scene file layout, so a small edit gives a small diff. */
class PrefabDocumentTest {
    private static final Path PREFABS = Path.of("..", "assets", "prefabs");

    private static List<Path> bundled() throws IOException {
        try (Stream<Path> files = Files.walk(PREFABS)) {
            List<Path> list = files.filter(f -> f.getFileName().toString().endsWith(".prefab")).sorted().toList();
            assertFalse(list.isEmpty(), "no bundled prefabs under " + PREFABS);
            return list;
        }
    }

    @Test
    void shouldWriteEveryBundledPrefabBackUnchanged() throws IOException {
        for (Path file : bundled()) {
            String committed = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            String written = PrefabDocument.parse(committed).toJson();
            assertTrue(written.startsWith("{\n  \"schemaVersion\": 1,\n  \"kind\": \"prefab\",\n"), file + ":\n" + written);
            assertEquals(written, PrefabDocument.parse(written).toJson(), file + " is not stable");
            if (!committed.endsWith("\n")) {
                committed += "\n";
            }
            assertEquals("", firstDifference(committed, written), file + " changes when written back");
        }
    }

    /** The first line that differs, with its number and both versions, or "" when the texts are equal. */
    private static String firstDifference(String expected, String actual) {
        String[] a = expected.split("\n", -1);
        String[] b = actual.split("\n", -1);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String left = i < a.length ? a[i] : "<none>";
            String right = i < b.length ? b[i] : "<none>";
            if (!left.equals(right)) {
                return "line " + (i + 1) + ": file has '" + left + "', written '" + right + "'";
            }
        }
        return "";
    }

    @Test
    void shouldChangeOneLineWhenOneValueChanges() throws IOException {
        Path file = bundled().get(0);
        String written = PrefabDocument.parse(Files.readString(file, StandardCharsets.UTF_8)).toJson();
        String renamed = written.replaceFirst("\"name\": \"([^\"]*)\",\n  \"rootId\"", "\"name\": \"Renamed\",\n  \"rootId\"");
        String rewritten = PrefabDocument.parse(renamed).toJson();
        String[] a = written.split("\n");
        String[] b = rewritten.split("\n");
        assertEquals(a.length, b.length);
        int changed = 0;
        for (int i = 0; i < a.length; i++) {
            changed += a[i].equals(b[i]) ? 0 : 1;
        }
        assertEquals(1, changed, rewritten);
    }
}
