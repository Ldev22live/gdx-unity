package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.asset.SpriteReload;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceNavigator;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.NoDialogs;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.RecordingView;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshSaveSession;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshWorkspace;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteSession;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.sculpt.StaticMeshImport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.FRAME;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.newEditor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 2.5D hybrid sample (roadmap MC.4) taken from the New Game gallery into a project: its 2D sprite is edited in 2D Studio and
 * one of its Kenney 3D models in 3D Modeling, each reached through Customize; both changes show in the World and in Platformer +
 * 2.5D Play, and survive saving and reopening. The provider's original model is never overwritten.
 */
class HybridSideScrollerSampleTest {
    private static final File ASSETS = new File("../assets");
    private static final String SAMPLE = "samples/hybrid-side-scroller-25d.json";
    private static final String HERO_SPRITE = "sprites/hero.sprite";
    private static final String EDITED_MODEL_PART = "House Window";
    /** How far the window wall's top is raised, in the model's own units (metres). */
    private static final float RAISE = 0.5f;
    private static final float EPSILON = 1e-4f;
    private static final int PAINT = 0xFF00FFFF;

    @TempDir
    Path temp;

    private static Input previousInput;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
        previousInput = Gdx.input;
        // Editor Play reads the keyboard through the profile step's own input; nothing reads Gdx.input, but keep it harmless
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
    void shouldEditTheSpriteIn2DStudioAndAModelIn3DModelingThenPlaySaveAndReopen() throws Exception {
        // Project > New Game > Hybrid Side-scroller > Use as Template
        EditorState editor = newEditor();
        TemplateGallery.Choice hybrid = TemplateGallery.samples(ASSETS).stream().filter(c -> SAMPLE.equals(c.scene())).findFirst()
            .orElseThrow(() -> new AssertionError("the gallery offers the hybrid sample"));
        assertEquals(GameMode.MODE_2_5D, hybrid.mode());
        TemplateProject.Result made = TemplateGallery.create(hybrid, ASSETS, temp.resolve("projects").toFile(), "Hybrid",
            editor.getSerializer());
        File root = made.folder();
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        Scene scene = editor.getActiveScene();
        assertEquals(GameGenres.PLATFORMER, scene.getGameGenre());
        WorkspaceNavigator navigator = new WorkspaceNavigator(editor, new RecordingView());

        // The sprite: Customize > Edit 2D Asset opens 2D Studio; a pixel of the first frame is painted and saved
        Entity hero = scene.findEntityByName("Hero");
        CustomizeGateway.EditOffer spriteOffer = CustomizeGateway.editSelected(hero, editor.getSerializer().getRegistry()).get(0);
        assertEquals("Edit 2D Asset hero.sprite", spriteOffer.label());
        assertEquals(Workspace.STUDIO_2D, spriteOffer.workspace());
        editor.setSelectedEntity(hero);
        navigator.open(Workspace.destinations().stream().filter(d -> d.workspace() == Workspace.STUDIO_2D).findFirst().orElseThrow());
        SpriteSession studio = SpriteSession.open(new File(root, spriteOffer.assetPath()));
        assertNotEquals(PAINT, studio.getDocument().getPixel(0, 0, 0, 0));
        studio.setFrame(0);
        studio.paint(List.of(new int[] {0, 0}), PAINT, "Pencil");
        studio.saveAs(studio.getFile());
        navigator.backToWorld();
        assertEquals(3, SpriteReload.reloadUsers(scene, HERO_SPRITE), "the hero and both villagers share the sprite and reload it");
        SpriteAnimatorComponent heroSprite = hero.getComponent(SpriteAnimatorComponent.class);
        heroSprite.load(new FileHandle(new File(root, HERO_SPRITE))); // the World draws it again
        assertEquals(PAINT, heroSprite.getDocument().getPixel(0, 0, 0, 0));

        // A model: Customize > Edit 3D Model opens 3D Modeling; the window wall's top is raised and saved as a unique derived model
        Entity wall = scene.findEntityByName(EDITED_MODEL_PART);
        String shipped = wall.getComponent(ModelComponent.class).getModelPath();
        String plainWall = scene.findEntityByName("House Wall").getComponent(ModelComponent.class).getModelPath();
        assertTrue(shipped.startsWith("thirdparty/kenney/building-kit/"), shipped);
        byte[] originalBytes = Files.readAllBytes(root.toPath().resolve(shipped));
        CustomizeGateway.EditOffer modelOffer = CustomizeGateway.editSelected(wall, editor.getSerializer().getRegistry()).get(0);
        assertTrue(modelOffer.label().startsWith("Edit 3D Model"), modelOffer.label());
        assertEquals(Workspace.MODELING_3D, modelOffer.workspace());
        editor.setSelectedEntity(wall);
        assertTrue(navigator.editModel(wall), editor.getStatusMessage());
        assertEquals(Workspace.MODELING_3D, navigator.current());
        MeshWorkspace workspace = new MeshWorkspace(root.toPath(), wall);
        MeshSaveSession modeling = new MeshSaveSession(root.toPath(), workspace.open(wall));
        float shippedTop = topOf(root.toPath(), shipped);
        assertTrue(raiseTop(modeling.drafts(), RAISE) >= 2, "the wall's top vertices were moved");
        String derived = modeling.save(MeshSaveSession.Mode.UNIQUE, "Window wall raised", editor, false);
        navigator.backToWorld();
        assertEquals(Workspace.WORLD, navigator.current());
        assertNotEquals(shipped, derived);
        assertEquals(derived, wall.getComponent(ModelComponent.class).getModelPath(), "the World shows the derived wall");
        assertEquals(shippedTop + RAISE, topOf(root.toPath(), derived), EPSILON);
        assertEquals(plainWall, scene.findEntityByName("House Wall").getComponent(ModelComponent.class).getModelPath(),
            "the other wall pieces keep the kit's models");
        assertArrayEquals(originalBytes, Files.readAllBytes(root.toPath().resolve(shipped)), "the provider original is never overwritten");

        // Play as Platformer + 2.5D: the Play copy has both edits; Stop leaves the scene as it was
        String authored = editor.getSerializer().toJson(scene);
        assertPlayShowsTheEdits(editor, root, derived);
        assertEquals(authored, editor.getSerializer().toJson(editor.getActiveScene()), "Stop leaves the scene as it was");

        // Save, close, reopen: both edits are still there, and Play still shows them
        assertTrue(files.save(), editor.getStatusMessage());
        EditorState reopened = newEditor();
        assertTrue(new SceneFileController(reopened, new NoDialogs(), root).openFile(sceneFile), reopened.getStatusMessage());
        assertEquals(derived, reopened.getActiveScene().findEntityByName(EDITED_MODEL_PART).getComponent(ModelComponent.class)
            .getModelPath(), "the derived wall survives reopening");
        assertPlayShowsTheEdits(reopened, root, derived);
    }

    /** Plays one frame in editor Play: the Play copy's wall uses the derived model and its hero the edited sprite; then stops. */
    private static void assertPlayShowsTheEdits(EditorState editor, File project, String derived) {
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        play.getProfileStep().setInput(key -> false);
        assertTrue(play.play(), editor.getStatusMessage());
        try {
            assertTrue(editor.getStatusMessage().startsWith("Playing as Platformer / Side-scroller + 2.5D."), editor.getStatusMessage());
            play.update(FRAME);
            Scene runtime = play.getRuntimeScene();
            assertEquals(derived, runtime.findEntityByName(EDITED_MODEL_PART).getComponent(ModelComponent.class).getModelPath());
            SpriteAnimatorComponent sprite = runtime.findEntityByName("Hero").getComponent(SpriteAnimatorComponent.class);
            if (sprite.needsLoad()) {
                sprite.load(new FileHandle(new File(project, HERO_SPRITE)));
            }
            assertNotNull(sprite.getDocument(), "the Play copy's hero sprite loads");
            assertEquals(PAINT, sprite.getDocument().getPixel(0, 0, 0, 0), "the Play copy has the edited frame");
        } finally {
            play.stop();
        }
    }

    /** Raises every vertex at the top of the highest draft by {@code amount}; returns how many moved. */
    private static int raiseTop(List<MeshPartEditor> drafts, float amount) {
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
        assertNotNull(highest, "the wall has geometry");
        List<MeshPartEditor.Vertex> vertices = highest.geometry().vertices();
        int moved = 0;
        for (int i = 0; i < vertices.size(); i++) {
            if (vertices.get(i).y() >= top - EPSILON) {
                assertTrue(highest.apply(MeshPartEditor.Tool.Translate, MeshPartEditor.Kind.Vertex, i, 0f, amount, 0f));
                moved++;
            }
        }
        return moved;
    }

    /** The highest point of a model in its own units, read from the project file as the workspace reads it (no GL). */
    private static float topOf(Path root, String model) throws IOException {
        float top = Float.NEGATIVE_INFINITY;
        for (MeshPartEditor draft : MeshPartEditor.open(StaticMeshImport.read(root, Path.of(model)))) {
            for (MeshPartEditor.Vertex vertex : draft.geometry().vertices()) {
                top = Math.max(top, vertex.y());
            }
        }
        return top;
    }
}
