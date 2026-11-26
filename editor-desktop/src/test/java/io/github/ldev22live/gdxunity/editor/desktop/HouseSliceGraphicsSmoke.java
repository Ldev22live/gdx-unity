package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.NoDialogs;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorSkinFactory;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.ModelingNoticePanel;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor.Tool;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshSaveSession.Mode;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.ModelAssets;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.newEditor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap MC.2 on a real GL context, through the 3D Modeling panel's own controls: two houses from the Buildings palette, the
 * Roof NE part picked in the panel, a face extruded with Apply, <b>Edit shared prefab (every instance)</b> chosen and Save pressed.
 * The panel must show a warning naming both instances and change nothing until <b>Apply to all</b>; then both houses are drawn
 * with the new roof model and the project's prefab file holds it. Cancel must leave everything unchanged.
 *
 * <p>Not a Gradle test (it needs a display). Run from the repository root with the editor-desktop test runtime classpath; it writes
 * {@code build/mc2/shared-prefab.png} and exits 0, or throws.
 */
public final class HouseSliceGraphicsSmoke extends ApplicationAdapter {
    private static final String HOUSE_PREFAB = StarterPalette.BUILDING_FOLDER + "/CC0 House.prefab";
    private static final String ROOF = "Roof NE";
    private static final Path OUT = Path.of("build", "mc2");
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private Stage stage;
    private EditorSkinFactory skinFactory;
    private ModelAssets models;
    private SceneRenderer3D renderer;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Forgeroom house slice: shared prefab");
        config.setWindowedMode(WIDTH, HEIGHT);
        config.setResizable(false);
        new Lwjgl3Application(new HouseSliceGraphicsSmoke(), config);
    }

    @Override
    public void create() {
        try {
            verify();
            Gdx.app.exit();
        } catch (Exception | AssertionError e) {
            throw new IllegalStateException("House slice smoke failed", e);
        }
    }

    private void verify() throws Exception {
        Files.createDirectories(OUT);
        File assets = new File("assets");
        EditorState editor = newEditor();
        TemplateGallery.Choice empty3D = TemplateGallery.emptyStarts(assets).stream().filter(c -> c.mode() == GameMode.MODE_3D)
            .findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(empty3D, assets,
            Files.createTempDirectory(OUT, "slice-").resolve("project").toFile(), "Slice", editor.getSerializer());
        File root = made.folder();
        assertTrue(new SceneFileController(editor, new NoDialogs(), root).openFile(new File(root, made.startScene())),
            editor.getStatusMessage());
        PrefabService prefabs = new PrefabService(editor, root);
        editor.setPrefabService(prefabs);
        Scene scene = editor.getActiveScene();
        Entity first = prefabs.instantiate(new AssetReference(HOUSE_PREFAB), new Vector3(-350f, 0f, 0f));
        Entity second = prefabs.instantiate(new AssetReference(HOUSE_PREFAB), new Vector3(350f, 0f, 0f));
        assertNotNull(first, editor.getStatusMessage());
        assertNotNull(second, editor.getStatusMessage());
        Path prefabFile = root.toPath().resolve(HOUSE_PREFAB);
        String prefabBefore = Files.readString(prefabFile);
        String original = roofOf(first);

        models = new ModelAssets(root);
        PerspectiveCamera3D camera = new PerspectiveCamera3D(50f, 1f, 10000f);
        camera.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        camera.setPosition(0f, 900f, 1300f);
        camera.lookAt(0f, 150f, 0f);
        camera.update();
        renderer = new SceneRenderer3D(camera, scene);
        renderer.setModelAssets(models);
        renderer.initialize();
        renderer.setObjectCulling(false);

        // The 3D Modeling panel on the second house: pick Roof NE, extrude a face
        editor.setSelectedEntity(second);
        skinFactory = new EditorSkinFactory();
        stage = new Stage();
        ModelingNoticePanel panel = new ModelingNoticePanel(skinFactory.createSkin(), editor, () -> true, root.toPath(), models::reload);
        stage.addActor(panel);
        stage.act();
        pickPart(ROOF);
        selectTool(Tool.Extrude);
        press("Apply");

        // Edit shared prefab: Save shows the warning; Cancel changes nothing
        selectMode(Mode.PREFAB);
        press("Save");
        Dialog dialog = find(stage.getRoot(), Dialog.class, d -> true);
        assertNotNull(dialog, "Save on a shared prefab asks first");
        Label message = find(dialog, Label.class, l -> l.getText().toString().contains("instance(s)"));
        assertNotNull(message, "the dialog explains what changes");
        assertTrue(message.getText().toString().contains("All 2 instance(s)"), message.getText().toString());
        press("Cancel");
        assertEquals(original, roofOf(second), "Cancel binds nothing");
        assertEquals(prefabBefore, Files.readString(prefabFile), "Cancel leaves the prefab file");
        String[] edited = root.toPath().resolve("models/edited").toFile().list();
        assertTrue(edited == null || edited.length == 0, "Cancel writes no model");

        // Save again and Apply to all: both houses and the prefab take the new roof
        press("Save");
        press("Apply to all");
        String derived = roofOf(second);
        assertNotEquals(original, derived, "the second house has the new roof");
        assertEquals(derived, roofOf(first), "so has the first: every instance changed");
        assertTrue(Files.readString(prefabFile).contains(derived), "the prefab file holds the new roof");
        renderer.render(0f);
        var drawnFirst = renderer.getModelInstance(roofEntity(first).getComponent(ModelComponent.class));
        var drawnSecond = renderer.getModelInstance(roofEntity(second).getComponent(ModelComponent.class));
        assertNotNull(drawnFirst);
        assertNotNull(drawnSecond);
        assertSame(models.get(derived).model(), drawnFirst.model, "the first house is drawn with the new roof model");
        assertSame(models.get(derived).model(), drawnSecond.model, "the second house is drawn with the new roof model");
        Pixmap frame = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            PixmapIO.writePNG(Gdx.files.absolute(OUT.resolve("shared-prefab.png").toAbsolutePath().toString()), frame, -1, true);
        } finally {
            frame.dispose();
        }

        // One undo takes it all back
        assertTrue(editor.getCommandHistory().undo());
        assertEquals(original, roofOf(first));
        assertEquals(original, roofOf(second));
        assertEquals(prefabBefore, Files.readString(prefabFile));
        System.out.println("House slice: Edit shared prefab warned \"" + message.getText() + "\"; Cancel changed nothing; Apply to all "
            + "bound " + derived + " to both houses and the prefab, both drawn with it; one undo restored all");
    }

    private static Entity roofEntity(Entity house) {
        for (var child : house.getTransform().getChildren()) {
            if (child.getEntity().getName().equals(ROOF)) {
                return child.getEntity();
            }
        }
        throw new AssertionError(house.getName() + " has no " + ROOF);
    }

    private static String roofOf(Entity house) {
        return roofEntity(house).getComponent(ModelComponent.class).getModelPath();
    }

    private void press(String label) {
        TextButton button = find(stage.getRoot(), TextButton.class, b -> b.getText().toString().equals(label));
        assertNotNull(button, label);
        button.fire(new ChangeEvent());
        stage.act();
        stage.draw();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void pickPart(String name) {
        SelectBox box = find(stage.getRoot(), SelectBox.class, s -> s.getItems().size > 1
            && s.getSelected() != null && !(s.getSelected() instanceof Enum));
        assertNotNull(box, "the part list");
        Object wanted = null;
        for (Object item : box.getItems()) {
            if (item.toString().equals(name)) {
                wanted = item;
            }
        }
        assertNotNull(wanted, name + " in the part list");
        box.setSelected(wanted);
        stage.act();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void selectTool(Tool tool) {
        SelectBox box = find(stage.getRoot(), SelectBox.class, s -> s.getSelected() instanceof Tool);
        assertNotNull(box, "the tool list");
        box.setSelected(tool);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void selectMode(Mode mode) {
        SelectBox box = find(stage.getRoot(), SelectBox.class, s -> s.getSelected() instanceof Mode);
        assertNotNull(box, "the save mode list");
        box.setSelected(mode);
    }

    private static <T extends Actor> T find(Group parent, Class<T> type, Predicate<T> match) {
        for (Actor actor : parent.getChildren()) {
            if (type.isInstance(actor) && match.test(type.cast(actor))) {
                return type.cast(actor);
            }
            if (actor instanceof Group group) {
                T found = find(group, type, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
        if (skinFactory != null) {
            skinFactory.dispose();
        }
        if (renderer != null) {
            renderer.dispose();
        }
        if (models != null) {
            models.dispose();
        }
    }
}
