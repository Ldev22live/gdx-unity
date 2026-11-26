package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;
import io.github.ldev22live.gdxunity.editor.core.project.GameExportCommand;
import io.github.ldev22live.gdxunity.editor.core.project.GameExporter;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshSaveSession;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshWorkspace;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.ModelAssets;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.sculpt.StaticMeshImport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.NoDialogs;

import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.fakeRuntimeImage;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.newEditor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3c.6 on a real GL context: the house-edit replay of {@link HouseEditReplayTest}, drawn. A house from the Buildings palette
 * is placed in a new project, one roof part is raised and saved as a derived model bound to that part, and the same fixed camera draws
 * the World before and after the edit, editor Play, the scene after save and reopen, and the exported game's files. At the raised roof
 * the colour must change after the edit and stay changed in every later frame; on an untouched roof part it must stay as it was.
 *
 * <p>Not a Gradle test (it needs a display). Run from the repository root with the editor-desktop test runtime classpath; it writes
 * its frames to {@code build/m3c6/frames} and exits 0, or throws.
 */
public final class HouseEditReplayGraphicsSmoke extends ApplicationAdapter {
    private static final String HOUSE_PREFAB = StarterPalette.BUILDING_FOLDER + "/CC0 House.prefab";
    private static final String ROOF = "Roof NE";
    /** An untouched roof part, the control. */
    private static final String CONTROL_ROOF = "Roof SW";
    /** The roof raise in model units; the prefab's import scale of 100 makes it 40 world units. */
    private static final float RAISE = 0.4f;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    /** Half the side of the square of pixels averaged at a sample point. */
    private static final int PATCH = 2;
    /** Summed RGB difference (0 to 765) above which two samples count as different colours. */
    private static final int CHANGED = 45;
    /** Summed RGB difference below which two samples count as the same colour (lighting and filtering noise). */
    private static final int SAME = 15;
    /** The empty 3D start's ground box: 2000 by 20 by 2000 units. */
    private static final float GROUND_SIZE = 2000f;
    private static final float GROUND_DEPTH = 20f;
    private static final Path OUT = Path.of("build", "m3c6");

    private final List<AutoCloseable> owned = new ArrayList<>();

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Forgeroom house-edit replay");
        config.setWindowedMode(WIDTH, HEIGHT);
        config.setResizable(false);
        new Lwjgl3Application(new HouseEditReplayGraphicsSmoke(), config);
    }

    @Override
    public void create() {
        try {
            verify();
            Gdx.app.exit();
        } catch (Exception | AssertionError e) {
            throw new IllegalStateException("House-edit replay smoke failed", e);
        }
    }

    private void verify() throws Exception {
        Path frames = Files.createDirectories(OUT.resolve("frames"));
        Path projects = Files.createDirectories(OUT.resolve("projects"));
        File assets = new File("assets");
        EditorState editor = newEditor();
        TemplateGallery.Choice empty3D = TemplateGallery.emptyStarts(assets).stream().filter(c -> c.mode() == GameMode.MODE_3D)
            .findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(empty3D, assets,
            Files.createTempDirectory(projects, "replay-").resolve("project").toFile(), "Replay", editor.getSerializer());
        File root = made.folder();
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        PrefabService prefabs = new PrefabService(editor, root);
        editor.setPrefabService(prefabs);
        Scene scene = editor.getActiveScene();
        // The empty start's ground is a box without collision; Play's fix adds one fitted to it
        scene.findEntityByName("Ground").addComponent(new BoxCollider3DComponent(new Vector3(GROUND_SIZE, GROUND_DEPTH, GROUND_SIZE)));
        Entity house = prefabs.instantiate(new AssetReference(HOUSE_PREFAB), new Vector3());
        assertNotNull(house, editor.getStatusMessage());
        assertNotNull(prefabs.instantiate(new AssetReference(StarterPalette.CHARACTER_FOLDER + "/Player.prefab"),
            new Vector3(-600f, 0f, 600f)), editor.getStatusMessage());
        assertNotNull(prefabs.instantiate(new AssetReference(StarterPalette.CHARACTER_FOLDER + "/Spawn Point.prefab"),
            new Vector3(-600f, 0f, 600f)), editor.getStatusMessage());
        assertTrue(editor.setGameGenre(GameGenres.ACTION_RPG));
        Entity roof = scene.findEntityByName(ROOF);
        String shipped = roof.getComponent(ModelComponent.class).getModelPath();

        ModelAssets models = own(new ModelAssets(root));
        PerspectiveCamera3D camera = new PerspectiveCamera3D(50f, 1f, 10000f);
        camera.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        camera.setPosition(760f, 640f, 760f);
        camera.lookAt(0f, 220f, 0f);
        camera.update();
        SceneRenderer3D renderer = own(renderer(camera, scene, models));
        scene.setColliderGeometry(new ColliderGeometry(models));

        // Where to look: the middle of the raise above Roof NE's top, and the top of an untouched roof part
        Vector3 raisedAt = topCentre(root.toPath(), shipped, roof).add(0f, RAISE * importScale(roof) / 2f, 0f);
        Entity control = scene.findEntityByName(CONTROL_ROOF);
        Vector3 controlAt = topCentre(root.toPath(), control.getComponent(ModelComponent.class).getModelPath(), control)
            .add(0f, -1f, 0f);
        int[] raisedPixel = project(camera, raisedAt);
        int[] controlPixel = project(camera, controlAt);

        Frame before = draw(renderer, frames.resolve("1-world-before.png"), raisedPixel, controlPixel);

        // 3D Modeling: raise the roof's top and save a unique derived model, as the panel's Save does (it reloads the model cache)
        MeshSaveSession modeling = new MeshSaveSession(root.toPath(), new MeshWorkspace(root.toPath(), house).open(roof));
        raiseTop(modeling.drafts());
        String derived = modeling.save(MeshSaveSession.Mode.UNIQUE, "Roof NE raised", editor, false);
        models.reload(derived);
        assertEquals(derived, roof.getComponent(ModelComponent.class).getModelPath());

        Frame world = draw(renderer, frames.resolve("2-world-after.png"), raisedPixel, controlPixel);
        assertTrue(before.raised().distance(world.raised()) > CHANGED, "the World shows the raised roof: " + before + " -> " + world);
        assertTrue(before.control().distance(world.control()) < SAME, "the other roof parts are unchanged: " + before + " -> " + world);

        // Editor Play draws the runtime copy of the scene
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        play.getProfileStep().setInput(key -> false);
        assertTrue(play.play(), editor.getStatusMessage());
        Frame played;
        try {
            play.update(1f / 60f);
            renderer.setActiveScene(play.getRuntimeScene());
            played = draw(renderer, frames.resolve("3-play.png"), raisedPixel, controlPixel);
        } finally {
            play.stop();
            renderer.setActiveScene(editor.getActiveScene());
        }
        assertShowsEdit("Play", before, world, played);

        // Save, close, reopen in a fresh editor and model cache
        assertTrue(files.save(), editor.getStatusMessage());
        EditorState reopened = newEditor();
        reopened.setPrefabService(new PrefabService(reopened, root));
        assertTrue(new SceneFileController(reopened, new NoDialogs(), root).openFile(sceneFile), reopened.getStatusMessage());
        Scene back = reopened.getActiveScene();
        assertEquals(derived, back.findEntityByName(ROOF).getComponent(ModelComponent.class).getModelPath());
        ModelAssets reopenedModels = own(new ModelAssets(root));
        Frame afterReopen = draw(own(renderer(camera, back, reopenedModels)), frames.resolve("4-reopened.png"), raisedPixel,
            controlPixel);
        assertShowsEdit("Reopened", before, world, afterReopen);

        // Project > Export Game, then the exported game's own files (the game runtime's follow camera is not used here)
        Path exportRoot = root.toPath().getParent();
        GameExporter.Result result = GameExportCommand.run(new EditorProject(root), null,
            fakeRuntimeImage(exportRoot.resolve("image")).toFile(), exportRoot.resolve("export").toFile());
        Path game = result.folder().toPath().resolve("app").resolve(GameExporter.GAME_FOLDER);
        Scene exported = GameSession.loadScene(game.resolve(made.startScene()).toFile());
        assertEquals(derived, exported.findEntityByName(ROOF).getComponent(ModelComponent.class).getModelPath());
        Frame fromGame = draw(own(renderer(camera, exported, own(new ModelAssets(game.toFile())))), frames.resolve("5-game-files.png"),
            raisedPixel, controlPixel);
        assertShowsEdit("Game files", before, world, fromGame);

        System.out.println("House-edit replay: raised roof at pixel (" + raisedPixel[0] + ", " + raisedPixel[1] + ") " + before.raised()
            + " -> World " + world.raised() + ", Play " + played.raised() + ", reopened " + afterReopen.raised() + ", game files "
            + fromGame.raised() + "; control roof " + before.control() + " -> " + world.control() + "; derived model " + derived);
    }

    private static void assertShowsEdit(String where, Frame before, Frame world, Frame frame) {
        assertTrue(before.raised().distance(frame.raised()) > CHANGED, where + " shows the raised roof: " + before + " -> " + frame);
        assertTrue(world.raised().distance(frame.raised()) < SAME, where + " looks like the edited World: " + world + " vs " + frame);
        assertTrue(before.control().distance(frame.control()) < SAME, where + " keeps the other roof parts: " + frame);
    }

    private static SceneRenderer3D renderer(PerspectiveCamera3D camera, Scene scene, ModelAssets models) {
        SceneRenderer3D renderer = new SceneRenderer3D(camera, scene);
        renderer.setModelAssets(models);
        renderer.initialize();
        renderer.setObjectCulling(false);
        return renderer;
    }

    private static void raiseTop(List<MeshPartEditor> drafts) {
        MeshPartEditor highest = null;
        float top = Float.NEGATIVE_INFINITY;
        for (MeshPartEditor draft : drafts) {
            for (MeshPartEditor.Vertex vertex : draft.geometry().vertices()) {
                if (vertex.y() > top) {
                    top = vertex.y();
                    highest = draft;
                }
            }
        }
        assertNotNull(highest);
        List<MeshPartEditor.Vertex> vertices = highest.geometry().vertices();
        for (int i = 0; i < vertices.size(); i++) {
            if (vertices.get(i).y() >= top - 1e-3f) {
                assertTrue(highest.apply(MeshPartEditor.Tool.Translate, MeshPartEditor.Kind.Vertex, i, 0f, RAISE, 0f));
            }
        }
    }

    /** The world-space centre of a model part's highest vertices. */
    private static Vector3 topCentre(Path root, String model, Entity part) throws IOException {
        float top = Float.NEGATIVE_INFINITY;
        List<MeshPartEditor> drafts = MeshPartEditor.open(StaticMeshImport.read(root, Path.of(model)));
        for (MeshPartEditor draft : drafts) {
            for (MeshPartEditor.Vertex vertex : draft.geometry().vertices()) {
                top = Math.max(top, vertex.y());
            }
        }
        Vector3 sum = new Vector3();
        int count = 0;
        for (MeshPartEditor draft : drafts) {
            for (MeshPartEditor.Vertex vertex : draft.geometry().vertices()) {
                if (vertex.y() >= top - 1e-3f) {
                    sum.add(vertex.x(), vertex.y(), vertex.z());
                    count++;
                }
            }
        }
        float scale = importScale(part);
        return sum.scl(scale / count).mul(part.getTransform().getWorldMatrix());
    }

    private static float importScale(Entity part) {
        return part.getComponent(ModelComponent.class).getImportScale();
    }

    /** Screen pixel of a world point, origin at the bottom left, as the frame buffer is read. */
    private static int[] project(PerspectiveCamera3D camera, Vector3 world) {
        Vector3 screen = camera.getGdxCamera().project(world.cpy(), 0f, 0f, Gdx.graphics.getBackBufferWidth(),
            Gdx.graphics.getBackBufferHeight());
        int x = Math.round(screen.x);
        int y = Math.round(screen.y);
        assertTrue(x > PATCH && y > PATCH && x < Gdx.graphics.getBackBufferWidth() - PATCH
            && y < Gdx.graphics.getBackBufferHeight() - PATCH, "sample point on screen: " + x + ", " + y);
        return new int[] {x, y};
    }

    private Frame draw(SceneRenderer3D renderer, Path png, int[] raised, int[] control) {
        renderer.render(0f);
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap frame = Pixmap.createFromFrameBuffer(0, 0, width, height);
        try {
            PixmapIO.writePNG(Gdx.files.absolute(png.toAbsolutePath().toString()), frame, -1, true);
            return new Frame(sample(frame, raised), sample(frame, control));
        } finally {
            frame.dispose();
        }
    }

    /** The mean colour of a small square around a pixel; rows of a read frame buffer start at the bottom. */
    private static Rgb sample(Pixmap frame, int[] at) {
        int r = 0;
        int g = 0;
        int b = 0;
        int n = 0;
        Color color = new Color();
        for (int dy = -PATCH; dy <= PATCH; dy++) {
            for (int dx = -PATCH; dx <= PATCH; dx++) {
                Color.rgba8888ToColor(color, frame.getPixel(at[0] + dx, at[1] + dy));
                r += Math.round(color.r * 255f);
                g += Math.round(color.g * 255f);
                b += Math.round(color.b * 255f);
                n++;
            }
        }
        return new Rgb(r / n, g / n, b / n);
    }

    private <T> T own(T resource) {
        if (resource instanceof Disposable disposable) {
            owned.add(disposable::dispose);
        }
        return resource;
    }

    @Override
    public void dispose() {
        List<Exception> failures = new ArrayList<>();
        for (int i = owned.size() - 1; i >= 0; i--) {
            try {
                owned.get(i).close();
            } catch (Exception e) {
                failures.add(e);
            }
        }
        owned.clear();
        if (!failures.isEmpty()) {
            IllegalStateException failed = new IllegalStateException(failures.size() + " resource(s) failed to dispose");
            failures.forEach(failed::addSuppressed);
            throw failed;
        }
    }

    /** One 8-bit colour. */
    private record Rgb(int r, int g, int b) {
        int distance(Rgb other) {
            return Math.abs(r - other.r) + Math.abs(g - other.g) + Math.abs(b - other.b);
        }

        @Override
        public String toString() {
            return "rgb(" + r + "," + g + "," + b + ")";
        }
    }

    /** The samples one drawn frame gives: at the raised roof and at the untouched control roof. */
    private record Frame(Rgb raised, Rgb control) { }
}
