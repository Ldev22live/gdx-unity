package io.github.ldev22live.gdxunity.editor.core.project;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The empty starts of the template gallery (roadmap M1.4): a new project with one scene in the chosen game mode that holds only
 * what Play needs to show something: a camera, and for 2.5D and 3D a sun, a sky light and a ground to stand on. Like
 * {@link TemplateProject} it never overwrites a folder; the new project gets a name no folder has, and a half-made folder is removed.
 */
public final class StarterProject {
    /** The scene of a new empty project, relative to it. */
    public static final String START_SCENE = TemplateProject.SCENE_FOLDER + "/Main.json";
    /** Where the 2D camera looks: the middle of a 640 by 480 screen, as in the 2D samples. */
    static final float CAMERA_2D_X = 320f;
    static final float CAMERA_2D_Y = 240f;
    /** The 3D camera: up and back from the origin, tilted down by 30 degrees, as in the 3D samples. */
    static final float CAMERA_3D_HEIGHT = 370f;
    static final float CAMERA_3D_DISTANCE = 520f;
    static final float CAMERA_3D_PITCH_DEGREES = -30f;
    /** The 2.5D camera: a side view of the plane z = 0, slightly above it. */
    static final float CAMERA_25D_HEIGHT = 150f;
    static final float CAMERA_25D_DISTANCE = 700f;
    /** Ground of the 3D start: a flat slab this wide and deep, its top at y = 0. */
    static final float GROUND_3D_SIZE = 2000f;
    /** Ground of the 2.5D start: a strip along x, this long and deep, its top at y = 0. */
    static final float GROUND_25D_LENGTH = 1600f;
    static final float GROUND_25D_DEPTH = 200f;
    /** Thickness of either ground slab. */
    static final float GROUND_THICKNESS = 20f;
    /** Grass green for the ground. */
    private static final Color GROUND_COLOR = new Color(0.36f, 0.55f, 0.30f, 1f);

    private StarterProject() { }

    /**
     * Makes an empty project in {@code projectsDir}.
     *
     * @param name       what to call it; blank uses "My &lt;mode&gt; Game"; a number is added if the folder exists
     * @param mode       the game mode of its scene
     * @param serializer writes the scene; the editor's own, so every component it uses is registered
     * @throws IOException if the project cannot be written; nothing is left behind
     */
    public static TemplateProject.Result create(File projectsDir, String name, GameMode mode, SceneSerializer serializer)
        throws IOException {
        Objects.requireNonNull(projectsDir, "projectsDir cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(serializer, "serializer cannot be null");
        String chosen = name == null || name.isBlank() ? defaultName(mode) : name;
        Files.createDirectories(projectsDir.toPath());
        Path folder = TemplateProject.uniqueFolder(projectsDir.toPath(), chosen);
        Scene scene = createScene(folder.getFileName().toString(), mode);
        try {
            Files.createDirectory(folder);
            Files.createDirectories(folder.resolve(TemplateProject.SCENE_FOLDER));
            Files.writeString(folder.resolve(START_SCENE), serializer.toJson(scene), StandardCharsets.UTF_8);
            Files.writeString(folder.resolve(EditorProject.MARKER_FILE),
                "format=1\nname=" + folder.getFileName() + "\nstartScene=" + START_SCENE + "\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            IOException cleanup = TemplateProject.deleteTree(folder);
            if (cleanup != null) {
                e.addSuppressed(cleanup);
            }
            throw e;
        } finally {
            scene.dispose();
        }
        return new TemplateProject.Result(folder.toFile(), START_SCENE, List.of(START_SCENE));
    }

    /** "My 2D Game", "My 2.5D Game", "My 3D Game". */
    public static String defaultName(GameMode mode) {
        return "My " + Objects.requireNonNull(mode, "mode cannot be null").getLabel() + " Game";
    }

    /** The scene of an empty start in {@code mode}. */
    static Scene createScene(String name, GameMode mode) {
        Scene scene = new Scene(name);
        scene.setGameMode(mode);
        Entity camera = scene.createEntity("Main Camera");
        switch (mode) {
            case MODE_2D -> {
                camera.getTransform().setLocalPosition(CAMERA_2D_X, CAMERA_2D_Y, 0f);
                camera.addComponent(new CameraComponent(CameraComponent.Projection.ORTHOGRAPHIC));
            }
            case MODE_2_5D -> {
                camera.getTransform().setLocalPosition(0f, CAMERA_25D_HEIGHT, CAMERA_25D_DISTANCE);
                camera.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
                addLights(scene);
                addGround(scene, GROUND_25D_LENGTH, GROUND_25D_DEPTH);
            }
            case MODE_3D -> {
                camera.getTransform().setLocalPosition(0f, CAMERA_3D_HEIGHT, CAMERA_3D_DISTANCE);
                camera.getTransform().setLocalEulerAngles(0f, CAMERA_3D_PITCH_DEGREES, 0f);
                camera.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
                addLights(scene);
                addGround(scene, GROUND_3D_SIZE, GROUND_3D_SIZE);
            }
        }
        return scene;
    }

    private static void addLights(Scene scene) {
        Entity sun = scene.createEntity("Sun");
        sun.addComponent(new DirectionalLightComponent());
        Entity sky = scene.createEntity("Sky Light");
        sky.addComponent(new SkyLightComponent());
    }

    private static void addGround(Scene scene, float sizeX, float sizeZ) {
        Entity ground = scene.createEntity("Ground");
        ground.getTransform().setLocalPosition(0f, -GROUND_THICKNESS / 2f, 0f);
        ground.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, sizeX, GROUND_THICKNESS, sizeZ,
            new Color(GROUND_COLOR)));
    }
}
