package io.github.ldev22live.gdxunity.engine.web;

import io.github.ldev22live.gdxunity.engine.desktop.ScriptRegistry;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.web.generated.GeneratedScripts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registry generated from assets/scripts must cover every script the bundled samples bind (TD-036). */
class GeneratedScriptsTest {
    /** Tests run in the module folder. */
    private static final Path SAMPLES = Path.of("../assets/samples");
    private static final Pattern SCRIPT_CLASS = Pattern.compile("\"scriptClassName\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void shouldRegisterEveryScriptTheSamplesBind() throws IOException {
        Set<String> bound = new TreeSet<>();
        try (Stream<Path> files = Files.list(SAMPLES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                Matcher matcher = SCRIPT_CLASS.matcher(Files.readString(file));
                while (matcher.find()) {
                    bound.add(matcher.group(1));
                }
            }
        }
        Set<String> registered = GeneratedScripts.create().getClassNames();

        assertFalse(bound.isEmpty(), "the samples bind scripts");
        Set<String> missing = new TreeSet<>(bound);
        missing.removeAll(registered);
        assertEquals(Set.of(), missing);
    }

    @Test
    void shouldCreateEachRegisteredScriptUnderItsOwnName() throws ClassNotFoundException {
        ScriptRegistry registry = GeneratedScripts.create();

        for (String className : registry.getClassNames()) {
            ScriptComponent script = registry.create(className);
            assertEquals(className, script.getClass().getName());
        }
        assertTrue(registry.getClassNames().size() > 0);
    }
}
