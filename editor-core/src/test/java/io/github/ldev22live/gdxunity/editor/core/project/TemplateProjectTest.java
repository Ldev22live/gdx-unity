package io.github.ldev22live.gdxunity.editor.core.project;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Use as Template" (roadmap M1.3): a sample becomes a separate project that stands on its own, and the shipped sample is not touched.
 * The real samples are checked all at once: each template holds every file its scene needs (following the references again from inside
 * the template finds nothing missing), its Java scripts compile there without the sample project, and the source is unchanged.
 */
class TemplateProjectTest {
    /** Tests run in the module folder; the project's assets are one level up. */
    private static final File ASSETS = new File("../assets");

    @TempDir
    Path temp;

    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    private static TreeMap<String, String> snapshot(Path root) throws IOException {
        TreeMap<String, String> state = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                state.put(root.relativize(path).toString().replace('\\', '/'),
                    Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis());
            }
        }
        return state;
    }

    private static List<String> samples() {
        File[] files = new File(ASSETS, "samples").listFiles((dir, name) -> name.endsWith(".json"));
        assertTrue(files != null && files.length >= 38, "the sample scenes are in place");
        Arrays.sort(files);
        return Arrays.stream(files).map(f -> "samples/" + f.getName()).collect(Collectors.toList());
    }

    @Test
    void shouldMakeASelfContainedProjectFromEverySampleWithoutTouchingTheOriginal() throws Exception {
        Path source = ASSETS.toPath().toAbsolutePath().normalize();
        TreeMap<String, String> before = snapshot(source);
        File projects = temp.resolve("projects").toFile();
        int withScripts = 0;
        List<String> problems = new ArrayList<>();

        for (String scene : samples()) {
            TemplateProject.Result result;
            try {
                result = TemplateProject.create(ASSETS, scene, projects, scene.substring("samples/".length(), scene.length() - 5));
            } catch (IOException e) {
                problems.add(scene + ": could not be made: " + e.getMessage());
                continue;
            }
            Path folder = result.folder().toPath();
            if (!EditorProject.isProject(result.folder())) {
                problems.add(scene + ": the new folder is not a project");
            }
            if (!Arrays.equals(Files.readAllBytes(source.resolve(scene)), Files.readAllBytes(folder.resolve(result.startScene())))) {
                problems.add(scene + ": the scene was changed in the copy");
            }
            // Following the references again, from inside the template, finds nothing it does not already hold
            TreeSet<String> held = new TreeSet<>(result.files());
            TreeSet<String> needed = TemplateProject.findDependencies(folder, result.startScene());
            if (!held.equals(needed)) {
                problems.add(scene + ": the template needs " + needed + " but holds " + held);
            }
            for (String file : result.files()) {
                if (!Files.isRegularFile(folder.resolve(file))) {
                    problems.add(scene + ": missing " + file);
                }
            }
            if (Files.isDirectory(folder.resolve("scripts"))) {
                withScripts++;
                try {
                    GameExportCommand.compileScripts(new EditorProject(result.folder()));
                } catch (IOException e) {
                    problems.add(scene + ": its scripts do not compile on their own: " + e.getMessage());
                }
            }
        }

        assertEquals(List.of(), problems);
        assertTrue(withScripts >= 15, "the Java samples were compiled inside their templates: " + withScripts);
        assertEquals(before, snapshot(source), "the shipped samples and assets must not change");
    }

    /**
     * Leaves a few templates in {@code build/template-projects} so the game runtime can be started on them by hand (for example
     * {@code :engine-desktop:run --args="--project editor-core/build/template-projects/ruins-runner-25d --scene scenes/ruins-runner-25d.json
     * --smoke-test=240"}): the project that holds the samples is not involved.
     */
    @Test
    void shouldLeaveSomeTemplatesOnDiskForRunningTheGameOnThem() throws Exception {
        File out = new File("build/template-projects");
        if (out.exists()) {
            try (Stream<Path> walk = Files.walk(out.toPath())) {
                for (Path path : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        for (String name : List.of("ruins-runner-25d", "collector-3d", "terrain-3d", "landscape-3d", "racing-3d")) {
            TemplateProject.Result result = TemplateProject.create(ASSETS, "samples/" + name + ".json", out, name);
            assertEquals(name, result.folder().getName());
            assertTrue(new File(result.folder(), "scenes/" + name + ".json").isFile());
            // A game's scripts are compiled from the template's own sources, into the template (not from the sample project)
            GameExportCommand.compileScripts(new EditorProject(result.folder()));
        }
    }

    @Test
    void shouldCopyOnlyWhatTheSceneNeedsAndNotOtherScenesOrUnrelatedFiles() throws Exception {
        Path source = temp.resolve("src");
        write(source, "samples/game.json", "{\"entities\":[{\"components\":[{\"data\":{\"model\":\"models/rock.gltf\",\"clip\":\"anims/a.anim\","
            + "\"scriptPath\":\"scripts/samples/one/One.java\"}}]}]}");
        write(source, "samples/other.json", "{\"entities\":[]}");
        write(source, "models/rock.gltf", "{\"buffers\":[{\"uri\":\"rock.bin\"}],\"images\":[{\"uri\":\"rock.png\"},{\"uri\":\"data:image/png;base64,AAAA\"}]}");
        write(source, "models/rock.bin", "bin");
        write(source, "models/rock.png", "png");
        write(source, "models/unused.glb", "unused");
        write(source, "anims/a.anim", "{\"sound\":\"sounds/hit.wav\"}");
        write(source, "sounds/hit.wav", "wav");
        write(source, "sounds/unused.wav", "wav");
        write(source, "scripts/samples/one/One.java", "package samples.one;\nimport samples.common.Shared;\npublic class One { String s = \"sounds/hit.wav\"; String n = \"not a file\"; }");
        write(source, "scripts/samples/one/Sibling.java", "package samples.one;\npublic class Sibling { }");
        write(source, "scripts/samples/common/Shared.java", "package samples.common;\npublic class Shared { }");
        write(source, "scripts/samples/two/Two.java", "package samples.two;\npublic class Two { }");

        TemplateProject.Result result = TemplateProject.create(source.toFile(), "samples/game.json", temp.resolve("out").toFile(), "My Game");

        assertEquals(List.of("anims/a.anim", "models/rock.bin", "models/rock.gltf", "models/rock.png", "scenes/game.json", "scripts/samples/common/Shared.java",
            "scripts/samples/one/One.java", "scripts/samples/one/Sibling.java", "sounds/hit.wav"), result.files());
        assertEquals("scenes/game.json", result.startScene());
        assertEquals("My Game", result.folder().getName());
        Properties settings = new Properties();
        settings.load(Files.newBufferedReader(result.folder().toPath().resolve(EditorProject.MARKER_FILE), StandardCharsets.UTF_8));
        assertEquals("scenes/game.json", settings.getProperty("startScene"));
        assertEquals("My Game", settings.getProperty("name"));
        assertFalse(Files.exists(result.folder().toPath().resolve("samples")), "other scenes are not copied");
        assertFalse(Files.exists(result.folder().toPath().resolve("models/unused.glb")));
        assertFalse(Files.exists(result.folder().toPath().resolve("scripts/samples/two")), "another sample's code is not copied");
    }

    @Test
    void shouldNeverOverwriteAnExistingFolderAndGiveTheNextNameInstead() throws Exception {
        Path source = temp.resolve("src");
        write(source, "samples/game.json", "{\"entities\":[]}");
        File out = temp.resolve("out").toFile();

        File first = TemplateProject.create(source.toFile(), "samples/game.json", out, "Game").folder();
        Files.writeString(first.toPath().resolve("mine.txt"), "my work");
        File second = TemplateProject.create(source.toFile(), "samples/game.json", out, "Game").folder();
        write(source, "samples/zebra.json", "{\"entities\":[]}");
        File blank = TemplateProject.create(source.toFile(), "samples/zebra.json", out, "  ").folder();
        File odd = TemplateProject.create(source.toFile(), "samples/game.json", out, "a/b:c").folder();

        assertEquals("Game", first.getName());
        assertEquals("Game 2", second.getName());
        assertEquals("zebra", blank.getName(), "a blank name uses the scene's name");
        assertEquals("abc", odd.getName(), "characters a folder name cannot hold are dropped, as for an exported game");
        assertEquals("my work", Files.readString(first.toPath().resolve("mine.txt")), "the first project was left as it was");
    }

    @Test
    void shouldRefuseAnythingThatIsNotASceneInsideTheSourceAndLeaveNothingBehind() throws Exception {
        Path source = temp.resolve("src");
        write(source, "samples/game.json", "{\"entities\":[]}");
        write(source, "notes.txt", "x");
        File out = temp.resolve("out").toFile();

        assertThrows(IOException.class, () -> TemplateProject.create(source.toFile(), "notes.txt", out, "X"));
        assertThrows(IOException.class, () -> TemplateProject.create(source.toFile(), "samples/missing.json", out, "X"));
        assertThrows(IOException.class, () -> TemplateProject.create(source.toFile(), "../outside.json", out, "X"));
        assertEquals(List.of(), listing(out), "a refused template leaves no folder");
    }

    @Test
    void shouldRemoveTheHalfMadeFolderAndSayWhichFileCouldNotBeReadWhenAReferencedFileIsBroken() throws Exception {
        Path source = temp.resolve("src");
        write(source, "samples/game.json", "{\"entities\":[{\"components\":[{\"data\":{\"graph\":\"graphs/bad.graph\"}}]}]}");
        write(source, "graphs/bad.graph", "{ this is not json");
        File out = temp.resolve("out").toFile();

        IOException failure = assertThrows(IOException.class, () -> TemplateProject.create(source.toFile(), "samples/game.json", out, "Bad"));

        assertTrue(failure.getMessage().contains("graphs/bad.graph"), failure.getMessage());
        assertEquals(List.of(), listing(out));
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static List<String> listing(File folder) {
        String[] names = folder.list();
        return names == null ? List.of() : Arrays.stream(names).sorted().collect(Collectors.toList());
    }
}
