package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorSkinFactory;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.ModelingNoticePanel;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor.Tool;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshSaveSession.Mode;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.ModelAssets;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshGeometry3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Windows/Linux desktop GL and Jolt acceptance run. Run from the repository root with the desktop test classpath. */
public final class ModelReturnGraphicsSmoke extends ApplicationAdapter {
    private static final String HOUSE = "prefabs/buildings/CC0 House.prefab";
    private static final String WALL = "Wall East South";
    private Stage stage;
    private EditorSkinFactory skinFactory;
    private ModelAssets models;
    private SceneRenderer3D renderer;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Forgeroom mesh return verification");
        new Lwjgl3Application(new ModelReturnGraphicsSmoke(), config);
    }

    @Override public void create() {
        try {
            verify();
            Gdx.app.exit();
        } catch (Exception e) {
            throw new IllegalStateException("Model return smoke failed", e);
        }
    }

    private void verify() throws Exception {
        Path assets = Path.of("assets");
        Path scratch = Files.createTempDirectory(Files.createDirectories(Path.of("build/m7a5")), "house-");
        Path prefab = scratch.resolve(HOUSE);
        Files.createDirectories(prefab.getParent());
        Files.copy(assets.resolve(HOUSE), prefab);

        EditorState state = new EditorState();
        EditorApplication.registerComponentSerializers(state.getSerializer().getRegistry());
        Scene scene = new Scene("House");
        var sun = new DirectionalLightComponent();
        sun.setDirection(-1, -1, -0.6f);
        sun.setIntensity(2f);
        scene.createEntity("Sun").addComponent(sun);
        state.setActiveScene(scene);
        PrefabService prefabs = new PrefabService(state, scratch.toFile());
        Entity house = prefabs.instantiate(new AssetReference(HOUSE), new Vector3());
        assertNotNull(house);
        Entity wall = scene.findEntityByName(WALL);
        String source = wall.getComponent(ModelComponent.class).getModelPath();
        for (Entity part : scene.getAllEntities()) {
            ModelComponent model = part.getComponent(ModelComponent.class);
            if (model == null) continue;
            Path dest = scratch.resolve(model.getModelPath());
            Files.createDirectories(dest.getParent());
            if (!Files.exists(dest)) Files.copy(assets.resolve(model.getModelPath()), dest);
        }
        Path texture = Path.of(source).getParent().resolve("Textures/colormap.png");
        Files.createDirectories(scratch.resolve(texture).getParent());
        Files.copy(assets.resolve(texture), scratch.resolve(texture));
        // Exact mesh collision is the Inspector's Mesh Collider 3D choice. The shipped prefab uses authored boxes.
        wall.removeComponent(wall.getComponent(BoxCollider3DComponent.class));
        wall.addComponent(new MeshCollider3DComponent());
        state.setSelectedEntity(wall);
        models = new ModelAssets(scratch.toFile());
        var camera = new PerspectiveCamera3D(60f, 1f, 5000f);
        camera.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.setPosition(650f, 410f, 620f);
        camera.lookAt(0f, 120f, 0f);
        camera.update();
        renderer = new SceneRenderer3D(camera, scene);
        renderer.setModelAssets(models);
        renderer.initialize();
        renderer.setObjectCulling(false);
        var collision = new ColliderGeometry(models);
        scene.setColliderGeometry(collision);
        MeshGeometry3D original = collision.geometryOf(wall);
        skinFactory = new EditorSkinFactory();
        stage = new Stage();
        var panel = new ModelingNoticePanel(skinFactory.createSkin(), state, () -> true, scratch, models::reload);
        stage.addActor(panel);
        stage.act();
        panel.pack();
        select(Tool.Extrude);
        press("Apply");
        select(Mode.UNIQUE);
        press("Save");
        String unique = wall.getComponent(ModelComponent.class).getModelPath();
        assertNotEquals(source, unique);
        renderer.render(0);
        var firstInstance = renderer.getModelInstance(wall.getComponent(ModelComponent.class));
        assertNotNull(firstInstance);
        var firstModel = models.get(unique).model();
        assertEquals(original.triangleCount() + 6, collision.geometryOf(wall).triangleCount());
        press("Apply");
        select(Mode.UPDATE);
        press("Save");
        press("Update");
        assertNotSame(firstModel, models.get(unique).model());
        renderer.render(0);
        var current = renderer.getModelInstance(wall.getComponent(ModelComponent.class));
        assertNotNull(current);
        assertNotSame(firstInstance, current);
        assertSame(models.get(unique).model(), current.model);
        Pixmap frame = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            PixmapIO.writePNG(Gdx.files.absolute(scratch.resolve("edited-house.png").toAbsolutePath().toString()), frame, -1, true);
        } finally {
            frame.dispose();
        }
        MeshGeometry3D edited = collision.geometryOf(wall);
        assertEquals(original.triangleCount() + 12, edited.triangleCount());
        assertTrue(state.getCommandHistory().undo());
        assertEquals(original.triangleCount(), collision.geometryOf(wall).triangleCount());
        assertTrue(state.getCommandHistory().redo());
        assertEquals(edited.triangleCount(), collision.geometryOf(wall).triangleCount());

        prefabs.storeOverrides(scene);
        Path file = scratch.resolve("house.json");
        Files.writeString(file, state.getSerializer().toJson(scene));
        Scene reopened = state.getSerializer().fromJson(Files.readString(file));
        state.setActiveScene(reopened);
        assertEquals(List.of(), prefabs.syncScene(reopened));
        reopened.setColliderGeometry(collision);
        Entity savedWall = reopened.findEntityByName(WALL);
        assertEquals(unique, savedWall.getComponent(ModelComponent.class).getModelPath());
        assertArrayEquals(edited.positions(), collision.geometryOf(savedWall).positions());
        PlayModeController play = new PlayModeController(state);
        assertTrue(play.play(), state.getStatusMessage());
        try {
            play.update(1f / 60f);
            renderer.setActiveScene(play.getRuntimeScene());
            renderer.render(0);
            assertNotNull(renderer.getModelInstance(play.getRuntimeScene().findEntityByName(WALL).getComponent(ModelComponent.class)));
            Entity runtimeWall = play.getRuntimeScene().findEntityByName(WALL);
            float[] xyz = edited.positions();
            int[] indices = edited.indices();
            Vector3 a = point(xyz, indices[0], runtimeWall);
            Vector3 b = point(xyz, indices[1], runtimeWall);
            Vector3 c = point(xyz, indices[2], runtimeWall);
            Vector3 normal = b.cpy().sub(a).crs(c.cpy().sub(a)).nor();
            Vector3 center = a.add(b).add(c).scl(1f / 3f);
            var hit = play.getPhysicsSystem3D().raycast(center.cpy().mulAdd(normal, 1f), normal.scl(-1), 2f,
                PhysicsLayers3D.ALL_LAYERS).orElseThrow();
            assertSame(runtimeWall, hit.entity());
            assertTrue(hit.point().epsilonEquals(center, 0.1f), "Ray must hit the twice-extruded face");
            System.out.println("Kenney house wall: " + original.triangleCount() + " -> " + edited.triangleCount()
                + " triangles; shared refresh, undo/redo, prefab reopen and Jolt hit verified at " + hit.point());
        } finally {
            play.stop();
        }
    }

    private static Vector3 point(float[] xyz, int index, Entity wall) {
        int i = index * 3;
        return new Vector3(xyz[i], xyz[i + 1], xyz[i + 2]).mul(wall.getTransform().getWorldMatrix());
    }

    private void press(String label) {
        var button = find(stage.getRoot(), TextButton.class, b -> b.getText().toString().equals(label));
        assertNotNull(button, label);
        button.fire(new ChangeEvent());
        stage.act();
        stage.draw();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void select(Enum<?> value) {
        SelectBox box = find(stage.getRoot(), SelectBox.class, s -> value.getClass().isInstance(s.getSelected()));
        assertNotNull(box, value.toString());
        box.setSelected(value);
    }

    private static <T extends Actor> T find(Group parent, Class<T> type, Predicate<T> match) {
        for (Actor actor : parent.getChildren()) {
            if (type.isInstance(actor) && match.test(type.cast(actor))) return type.cast(actor);
            if (actor instanceof Group group) {
                T found = find(group, type, match);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override public void dispose() {
        stage.dispose();
        skinFactory.dispose();
        renderer.dispose();
        models.dispose();
    }
}
