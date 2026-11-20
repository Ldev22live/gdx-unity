package io.github.ldev22live.gdxunity.editor.desktop.audit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The full-map brief's criterion "No Age of Empires UI or screenshots remain" (UI M8), kept true: no source file of a shipped module
 * and no shipped asset names that game in its text or file name. Planning documents may still mention it as a workflow reference;
 * they are not shipped.
 */
class NoForeignGameArtTest {
    /** Words that would mean another game's interface or screenshots had crept in. */
    private static final List<String> FORBIDDEN = List.of("age of empires", "ageofempires", "age_of_empires", "age-of-empires");
    /** Text files read for the words; other files (images, models, sounds) are judged by their name only. */
    private static final Set<String> TEXT = Set.of("java", "kt", "json", "prefab", "graph", "material", "terrain", "anim", "song", "sprite",
        "txt", "md", "gltf");
    private static final Path ROOT = Path.of("..");

    @Test
    void shouldShipNoAgeOfEmpiresInterfaceTextOrImages() throws IOException {
        List<String> found = new ArrayList<>();
        List<Path> roots = new ArrayList<>();
        roots.add(ROOT.resolve("assets"));
        try (Stream<Path> modules = Files.list(ROOT)) {
            modules.filter(module -> {
                String name = module.getFileName().toString();
                return (name.startsWith("engine-") || name.startsWith("editor-")) && Files.isDirectory(module.resolve("src/main"));
            }).forEach(module -> roots.add(module.resolve("src/main")));
        }
        int scanned = 0;
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    scanned++;
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (FORBIDDEN.stream().anyMatch(name::contains)) {
                        found.add(file + " (file name)");
                        continue;
                    }
                    int dot = name.lastIndexOf('.');
                    if (dot > 0 && TEXT.contains(name.substring(dot + 1)) && Files.size(file) < 8L * 1024 * 1024) {
                        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                        if (FORBIDDEN.stream().anyMatch(text::contains)) {
                            found.add(file.toString());
                        }
                    }
                }
            }
        }
        assertTrue(scanned > 1000, "the scan found the shipped files (" + scanned + ")");
        assertTrue(found.isEmpty(), "Age of Empires found in shipped files: " + found);
    }
}
