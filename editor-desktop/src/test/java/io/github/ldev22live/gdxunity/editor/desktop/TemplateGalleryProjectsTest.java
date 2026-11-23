package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;
import io.github.ldev22live.gdxunity.editor.core.project.GameExportCommand;
import io.github.ldev22live.gdxunity.editor.core.project.GameExporter;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager;
import io.github.ldev22live.gdxunity.engine.desktop.GameScripts;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The done-when of roadmap M1.4: a new project made from each template the New Game gallery offers opens, plays and exports.
 * For every choice (the three empty starts and every catalog sample) a project is made in a temporary folder, its start scene is
 * opened by the editor's state as File > Open does, its scripts are compiled and it plays for two seconds in the game runtime's
 * session with no errors, and Export Game writes it into a game folder. The export copies a stand-in runtime image, because a
 * packaged editor is not built by the tests; the project half of the export is real. Finally the shipped samples are checked
 * unchanged.
 */
class TemplateGalleryProjectsTest {
    /** Tests run in the module folder; the project's assets are one level up. */
    private static final File PROJECT = new File("../assets");
    private static final float FRAME = 1f / 60f;
    /** Two seconds of play. */
    private static final int FRAMES = 120;
    /**
     * Templates whose play needs a model's triangles, which only a GL context can read (the game's renderer registers them). They
     * are opened and exported here, and played by the game runtime instead (see the M1.4 report). Nothing else may fail this way.
     */
    private static final Set<String> PLAYED_ONLY_WITH_GRAPHICS = Set.of("Mesh Colliders");
    /** What a collider built from a model says when models cannot be loaded (ColliderGeometry with no ModelAssets). */
    private static final String NEEDS_MODELS = "models cannot be loaded here";

    @TempDir
    static Path temp;

    private static Input previousInput;

    @BeforeAll
    static void setUp() {
        GdxNativesLoader.load();
        previousInput = Gdx.input;
        // No keys are held: the games idle, as they do before the person presses anything
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? false
                : method.getReturnType() == int.class ? 0
                : method.getReturnType() == float.class ? 0f
                : method.getReturnType() == long.class ? 0L : null);
    }

    @AfterAll
    static void restoreInput() {
        Gdx.input = previousInput;
    }

    @Test
    void shouldOpenPlayAndExportAProjectMadeFromEveryOfferedTemplateWithoutTouchingTheSamples() throws IOException {
        Map<String, Long> before = snapshot(PROJECT.toPath().resolve("samples"));
        List<TemplateGallery.Choice> choices = TemplateGallery.choices(PROJECT);
        assertTrue(choices.size() >= 3 + 40, "the empty starts and every sample: " + choices.size());
        Path image = fakeRuntimeImage(temp.resolve("image"));
        EditorState editor = new EditorState();
        EditorApplication.registerComponentSerializers(editor.getSerializer().getRegistry());
        List<String> failures = new ArrayList<>();
        Set<String> neededGraphics = new TreeSet<>();
        int index = 0;
        for (TemplateGallery.Choice choice : choices) {
            index++;
            try {
                TemplateProject.Result made = TemplateGallery.create(choice, PROJECT, temp.resolve("projects").toFile(), null,
                    editor.getSerializer());
                EditorProject project = new EditorProject(made.folder());
                File scene = new File(made.folder(), made.startScene());
                editor.loadSceneFromFile(scene);
                assertEquals(choice.mode(), editor.getActiveScene().getGameMode(), choice.title() + ": opened in its own mode");
                assertNotNull(GameCameraRig.findActiveCamera(editor.getActiveScene()), choice.title() + ": has a camera");
                if (!play(project, scene, choice.title())) {
                    neededGraphics.add(choice.title());
                }
                GameExporter.Result exported = GameExportCommand.run(project, null, image.toFile(),
                    temp.resolve("exports").resolve("game-" + index).toFile());
                assertTrue(new File(exported.folder(), "app/" + GameExporter.GAME_FOLDER + "/" + made.startScene()).isFile(),
                    choice.title() + ": the export holds the start scene");
            } catch (IOException | RuntimeException | AssertionError e) {
                failures.add(choice.title() + ": " + e.getMessage());
            }
        }
        assertTrue(failures.isEmpty(), () -> failures.size() + " template(s) failed:\n" + String.join("\n", failures));
        assertEquals(PLAYED_ONLY_WITH_GRAPHICS, neededGraphics, "only these templates need a GL context to play");
        assertEquals(before, snapshot(PROJECT.toPath().resolve("samples")), "the shipped samples are unchanged");
    }

    /**
     * Leaves the projects whose drawing the tests above cannot check (the three empty starts, and Mesh Colliders, which needs a GL
     * context to play) in {@code build/gallery-projects}, for runs of the real game runtime and editor on them (the M1.4 report).
     */
    @Test
    void shouldLeaveTheEmptyStartsAndTheGraphicsOnlyTemplateOnDiskForRunningTheGameOnThem() throws IOException {
        Path out = Path.of("build", "gallery-projects");
        if (Files.exists(out)) {
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        EditorState editor = new EditorState();
        EditorApplication.registerComponentSerializers(editor.getSerializer().getRegistry());
        List<String> made = new ArrayList<>();
        for (TemplateGallery.Choice choice : TemplateGallery.choices(PROJECT)) {
            if (choice.kind() == TemplateGallery.Kind.EMPTY || PLAYED_ONLY_WITH_GRAPHICS.contains(choice.title())) {
                made.add(TemplateGallery.create(choice, PROJECT, out.toFile(), null, editor.getSerializer()).folder().getName());
            }
        }
        assertEquals(List.of("My 2D Game", "My 25D Game", "My 3D Game", "physics-mesh-3d"), made);
    }

    /**
     * Compiles the project's scripts as Export and Play do, then plays the scene in the game runtime's session, with the collider
     * geometry the runtime gives primitives.
     *
     * @return false when the scene could not start only because a collider needs a model's triangles (a GL context)
     */
    private static boolean play(EditorProject project, File sceneFile, String title) throws IOException {
        if (project.getScriptsDir().isDirectory()) {
            ScriptManager scripts = new ScriptManager(project.getScriptsDir(), project.getScriptsBuildDir());
            try {
                assertTrue(scripts.compileAll(), () -> title + ": scripts compile: " + scripts.getLatestDiagnostics());
            } finally {
                scripts.dispose();
            }
        }
        List<String> logged = new ArrayList<>();
        GameSession.Log log = new GameSession.Log() {
            @Override
            public void info(String message) { }

            @Override
            public void error(String message) {
                logged.add(message);
            }
        };
        Scene scene = GameSession.loadScene(sceneFile);
        scene.setColliderGeometry(new ColliderGeometry(null));
        // What the game runtime's renderer does: terrain colliders read the project's terrains (TD-087)
        scene.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(project.getRoot()))));
        try (GameScripts scripts = new GameScripts(project.getScriptsBuildDir(), TemplateGalleryProjectsTest.class.getClassLoader())) {
            GameSession session = new GameSession(project.getRoot(), scene, scripts, log);
            try {
                try {
                    List<String> problems = session.start();
                    assertTrue(problems.isEmpty(), () -> title + ": starts: " + problems);
                    for (int i = 0; i < FRAMES; i++) {
                        session.update(FRAME);
                    }
                } catch (RuntimeException e) {
                    // The 3D physics backend asks for a mesh collider's triangles when it builds the body
                    if (e.getMessage() != null && e.getMessage().contains(NEEDS_MODELS)) {
                        return false;
                    }
                    throw new IllegalStateException(title + ": " + e, e);
                }
                assertTrue(session.getRuntimeErrors().isEmpty(), () -> title + ": plays: " + session.getRuntimeErrors());
                assertTrue(logged.isEmpty(), () -> title + ": logged errors: " + logged);
            } finally {
                session.dispose();
            }
        }
        return true;
    }

    /** A packaged editor's layout with stand-in files, as in {@code GameExporterTest}. */
    private static Path fakeRuntimeImage(Path root) throws IOException {
        write(root.resolve(GameExporter.EDITOR_LAUNCHER_NAME + ".exe"), "editor exe");
        write(root.resolve(GameExporter.LAUNCHER_NAME + ".exe"), "game exe");
        write(root.resolve("runtime/bin/java.dll"), "jvm");
        write(root.resolve("app/engine-desktop-1.0.0.jar"), "engine");
        write(root.resolve("app/" + GameExporter.LAUNCHER_NAME + ".cfg"), "game cfg");
        write(root.resolve("app/" + GameExporter.EDITOR_LAUNCHER_NAME + ".cfg"), "editor cfg");
        return root;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    /** Every file's size and modification time. */
    private static Map<String, Long> snapshot(Path folder) throws IOException {
        Map<String, Long> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                files.put(folder.relativize(file) + " size", Files.size(file));
                files.put(folder.relativize(file) + " time", Files.getLastModifiedTime(file).toMillis());
            }
        }
        return files;
    }
}
