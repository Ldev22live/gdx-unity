package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.HeaderMenus;
import io.github.ldev22live.gdxunity.editor.core.play.GenreChoices;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorldStrip;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M1b.6: the shared UX specification for a web UI ({@code docs/web-ui-ux-spec.md}). Its "Shared vocabulary" section is written
 * here from the desktop's own code, so the web side sees every change of a word; and the desktop is checked to depend on no web
 * framework and not on the web player module (roadmap R1b: "do not couple desktop Java/LibGDX implementation to Next.js or Spring
 * Boot"). Refresh the section with {@code GDXUNITY_UPDATE_AUDIT=1}.
 */
class WebUxContractTest {
    private static final File SPEC = new File("../docs/web-ui-ux-spec.md");
    private static final File ROOT = new File("..");
    private static final String BEGIN = "<!-- ux-contract:begin -->";
    private static final String END = "<!-- ux-contract:end -->";
    /** The module the desktop editor is built from; everything it depends on is the desktop. */
    private static final String DESKTOP_EDITOR = "editor-desktop";
    /** Modules that are web targets: the desktop must never depend on them. */
    private static final Set<String> WEB_MODULES = Set.of("engine-web");
    /** Dependency groups of web servers, web frameworks and the Java-to-web compiler. */
    private static final List<String> WEB_DEPENDENCY_GROUPS = List.of("org.springframework", "jakarta.servlet", "javax.servlet",
        "io.netty", "org.eclipse.jetty", "io.undertow", "io.javalin", "io.ktor", "org.teavm", "com.github.xpenatan");
    /** Imports of the same, in desktop source. */
    private static final Pattern WEB_IMPORT = Pattern.compile(
        "^import\\s+(org\\.springframework|jakarta\\.servlet|javax\\.servlet|io\\.netty|org\\.eclipse\\.jetty|io\\.undertow|io\\.javalin"
            + "|io\\.ktor|org\\.teavm)\\.", Pattern.MULTILINE);
    private static final Pattern PROJECT_DEPENDENCY = Pattern.compile("project\\(['\"]:([\\w-]+)['\"]\\)");
    private static final Pattern EXTERNAL_DEPENDENCY = Pattern.compile("['\"]([\\w.-]+):([\\w.-]+)(:[^'\"]*)?['\"]");

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** The generated section: the words, places and labels a web UI shares with the desktop. */
    static String vocabulary(List<EditorAction> actions) {
        Map<String, EditorAction> byId = actions.stream().collect(Collectors.toMap(EditorAction::getId, Function.identity()));
        StringBuilder out = new StringBuilder();
        out.append("### Header controls\n\n| Control | Label | Shortcut | Action id |\n|---|---|---|---|\n");
        for (String[] control : new String[][] {{"Save", DefaultEditorActions.FILE_SAVE_SCENE}, {"Undo", DefaultEditorActions.EDIT_UNDO},
            {"Redo", DefaultEditorActions.EDIT_REDO}}) {
            EditorAction action = byId.get(control[1]);
            assertTrue(action != null, "the registry has " + control[1]);
            out.append("| ").append(control[0]).append(" | ").append(action.getLabel()).append(" | ")
                .append(action.getShortcutText().isEmpty() ? "none" : action.getShortcutText()).append(" | `").append(control[1])
                .append("` |\n");
        }
        out.append("\nDropdowns, left to right: ").append(String.join(", ", HeaderMenus.names())).append(", ")
            .append(DefaultEditorActions.MENU_CREATE).append(". Their items are in the action inventory.\n");

        out.append("\n### Customize destinations on Desktop (in order)\n\n"
            + "The web does not list these; it has its own short list (section 3).\n\n");
        for (Workspace.Destination destination : Workspace.destinations()) {
            out.append("- ").append(destination.label()).append('\n');
        }
        out.append("\nWorkspace names: ").append(Stream.of(Workspace.values()).map(Workspace::getLabel).collect(Collectors.joining(", ")))
            .append(".\n");

        out.append("\n### World strip\n\nThe section names are shared; the messages point at desktop tools, so the web writes its own (section 2).\n\n"
            + "| Game mode | Sections, left to right | Planned sections and the message shown |\n|---|---|---|\n");
        for (GameMode mode : GameMode.values()) {
            List<String> planned = new ArrayList<>();
            for (WorldStrip.Section section : WorldStrip.sectionsFor(mode)) {
                Optional<String> reason = WorldStrip.unavailableReason(section, mode);
                reason.ifPresent(text -> planned.add("**" + section.getLabel() + "**: " + text));
            }
            out.append("| ").append(mode.getLabel()).append(" | ")
                .append(WorldStrip.sectionsFor(mode).stream().map(WorldStrip.Section::getLabel).collect(Collectors.joining(", ")))
                .append(" | ").append(planned.isEmpty() ? "none" : String.join("<br>", planned)).append(" |\n");
        }

        out.append("\n### Footer\n\n- Game Mode choices: ").append(Stream.of(GameMode.values()).map(GameMode::getLabel).collect(Collectors.joining(", ")))
            .append("\n- Game Genre choices, as the desktop marks them in each game mode (the desktop registry; the web plays none of them yet):\n\n"
                + "| Game mode | Choices |\n|---|---|\n");
        GameGenres genres = GameGenres.standard();
        for (GameMode mode : GameMode.values()) {
            out.append("| ").append(mode.getLabel()).append(" | ").append(GenreChoices.choices(genres, null, mode).stream()
                .map(GenreChoices.Choice::display).collect(Collectors.joining(", "))).append(" |\n");
        }

        out.append("\n### Desktop template labels (Appendix F5), from the most to the least editable\n\n"
            + "The web labels (Web Editable, Web Preview Only, Desktop Required) are separate (section 5).\n\n| Label | Meaning |\n|---|---|\n");
        for (TemplateMatrix.Label label : TemplateMatrix.Label.values()) {
            out.append("| ").append(label.text()).append(" | ").append(label.meaning()).append(" |\n");
        }
        out.append("\nNot yet checked for any template: ").append(String.join("; ", TemplateMatrix.UNCHECKED_GATES)).append(".\n");

        out.append("\n### Desktop gallery status lines\n\nThe web lines say whether a sample's scripts were ported to the web player; they are not web labels.\n\n- \"").append(TemplateGallery.DESKTOP_STATUS).append("\"\n- \"")
            .append(TemplateGallery.WEB_PORTED_STATUS).append("\"\n- \"").append(TemplateGallery.WEB_UNKNOWN_STATUS).append("\"\n");
        return out.toString();
    }

    @Test
    void shouldKeepTheSharedVocabularyOfTheWebUxSpecUpToDate() throws IOException {
        String expectedBody = vocabulary(ActionInventoryDump.registeredActions(project.toFile()));
        String spec = Files.readString(SPEC.toPath());
        int begin = spec.indexOf(BEGIN);
        int end = spec.indexOf(END);
        assertTrue(begin >= 0 && end > begin, "web-ui-ux-spec.md needs the markers " + BEGIN + " and " + END);
        String newline = spec.contains("\r\n") ? "\r\n" : "\n";
        String expected = BEGIN + newline + expectedBody.replace("\n", newline) + END;
        String current = spec.substring(begin, end + END.length());
        if ("1".equals(System.getenv(EditabilityAuditTest.UPDATE_VARIABLE)) && !current.equals(expected)) {
            Files.writeString(SPEC.toPath(), spec.substring(0, begin) + expected + spec.substring(end + END.length()));
            return;
        }
        assertEquals(expected, current, "the shared vocabulary in web-ui-ux-spec.md is stale: run this test with "
            + EditabilityAuditTest.UPDATE_VARIABLE + "=1");
    }

    @Test
    void shouldPutEveryWorkspaceAndStripSectionInTheVocabulary() throws IOException {
        String vocabulary = vocabulary(ActionInventoryDump.registeredActions(project.toFile()));
        for (Workspace workspace : Workspace.values()) {
            assertTrue(vocabulary.contains(workspace.getLabel()), workspace.getLabel());
        }
        for (WorldStrip.Section section : WorldStrip.Section.values()) {
            assertTrue(vocabulary.contains(section.getLabel()), section.getLabel());
        }
        for (TemplateMatrix.Label label : TemplateMatrix.Label.values()) {
            assertTrue(vocabulary.contains("| " + label.text() + " |"), label.text());
        }
    }

    @Test
    void shouldBuildTheDesktopEditorFromNoWebModuleAndNoWebFramework() throws IOException {
        Set<String> modules = desktopModules();
        assertTrue(modules.containsAll(Set.of("editor-desktop", "editor-core", "engine-desktop", "engine-core")), modules.toString());
        Set<String> web = new TreeSet<>(modules);
        web.retainAll(WEB_MODULES);
        assertEquals(Set.of(), web, "the desktop must not depend on a web module");

        List<String> webDependencies = new ArrayList<>();
        List<String> webImports = new ArrayList<>();
        for (String module : modules) {
            Matcher external = EXTERNAL_DEPENDENCY.matcher(Files.readString(buildFile(module)));
            while (external.find()) {
                String group = external.group(1);
                if (WEB_DEPENDENCY_GROUPS.stream().anyMatch(web1 -> group.equals(web1) || group.startsWith(web1 + "."))) {
                    webDependencies.add(module + ": " + external.group(0));
                }
            }
            Path sources = ROOT.toPath().resolve(module).resolve("src/main");
            if (Files.isDirectory(sources)) {
                try (Stream<Path> files = Files.walk(sources)) {
                    for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java") || f.toString().endsWith(".kt"))::iterator) {
                        if (WEB_IMPORT.matcher(Files.readString(file)).find()) {
                            webImports.add(ROOT.toPath().relativize(file).toString());
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), webDependencies, "web framework dependencies in the desktop");
        assertEquals(List.of(), webImports, "desktop source importing a web framework");
    }

    @Test
    void shouldFindAWebDependencyWhenOneIsThere() {
        Matcher external = EXTERNAL_DEPENDENCY.matcher("implementation \"org.springframework.boot:spring-boot-starter-web:3.3.0\"");
        assertTrue(external.find());
        assertTrue(WEB_DEPENDENCY_GROUPS.stream().anyMatch(group -> external.group(1).startsWith(group)));
        assertTrue(WEB_IMPORT.matcher("package a;\nimport org.springframework.web.bind.annotation.GetMapping;\n").find());
        assertFalse(WEB_IMPORT.matcher("import com.badlogic.gdx.Gdx;\n").find());
    }

    /** {@value #DESKTOP_EDITOR} and every module it depends on, by following {@code project(':...')} in the build files. */
    private static Set<String> desktopModules() throws IOException {
        Set<String> seen = new TreeSet<>();
        Deque<String> todo = new ArrayDeque<>(List.of(DESKTOP_EDITOR));
        while (!todo.isEmpty()) {
            String module = todo.pop();
            if (!seen.add(module)) {
                continue;
            }
            Matcher dependency = PROJECT_DEPENDENCY.matcher(Files.readString(buildFile(module)));
            while (dependency.find()) {
                todo.push(dependency.group(1));
            }
        }
        assertFalse(seen.isEmpty());
        return seen;
    }

    private static Path buildFile(String module) {
        Path file = ROOT.toPath().resolve(module).resolve("build.gradle");
        assertTrue(Files.isRegularFile(file), "no build file for " + module);
        return file;
    }
}
